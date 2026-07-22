package id.walt.x509

import kotlin.time.Clock
import kotlin.time.Instant

@Throws(X509ValidationException::class)
internal fun validateCertificateChainWithExplicitTrust(
    leaf: CertificateDer,
    chain: List<CertificateDer>,
    trustAnchors: List<CertificateDer>?,
    enableTrustedChainRoot: Boolean,
    now: Instant = Clock.System.now(),
    additionalProcessedCriticalExtensionOids: Set<String> = emptySet(),
    requiredExtendedKeyUsageOid: String? = null,
) {
    val parsedLeaf = parseCertificate(leaf, "leaf certificate")
    val parsedChain = chain.mapIndexed { index, certificate ->
        parseCertificate(certificate, "chain certificate at position $index")
    }

    val explicitAnchors = trustAnchors.orEmpty().mapIndexed { index, certificate ->
        parseCertificate(certificate, "trust anchor at position $index")
    }
    val trustedChainRoots = if (enableTrustedChainRoot) {
        parsedChain.filter { candidate ->
            runCatching { candidate.certificate.isSelfSigned() }.getOrDefault(false)
        }
    } else {
        emptyList()
    }

    val anchors = (explicitAnchors + trustedChainRoots).distinctBy { it.der }
    if (anchors.isEmpty()) {
        throw X509ValidationException(
            "No trust anchors available: provide trustAnchors or include a trusted root."
        )
    }

    val candidates = parsedChain
        .filterNot { it.der == leaf }
        .distinctBy { it.der }

    val path = buildValidatedPath(
        current = parsedLeaf,
        candidates = candidates,
        anchors = anchors,
        visited = setOf(parsedLeaf.der),
        now = now,
        caCertificatesBelow = 0,
        processedCriticalExtensionOids = processedCriticalExtensionOids + additionalProcessedCriticalExtensionOids,
        issuerProcessedCriticalExtensionOids = processedCriticalExtensionOids,
        requiredExtendedKeyUsageOid = requiredExtendedKeyUsageOid,
    )

    if (path == null) {
        throw X509ValidationException("Certificate path could not be built with the provided certificates.")
    }
}

private data class ParsedCertificate(
    val der: CertificateDer,
    val certificate: PlatformX509Certificate,
)

private fun parseCertificate(
    certificate: CertificateDer,
    description: String,
): ParsedCertificate =
    runCatching { ParsedCertificate(certificate, PlatformX509Certificate.parse(certificate)) }
        .getOrElse { cause ->
            throw X509ValidationException(
                "Certificate chain validation failed: invalid X.509 DER in $description: ${cause.message}",
                cause,
            )
        }

private fun buildValidatedPath(
    current: ParsedCertificate,
    candidates: List<ParsedCertificate>,
    anchors: List<ParsedCertificate>,
    visited: Set<CertificateDer>,
    now: Instant,
    caCertificatesBelow: Int,
    processedCriticalExtensionOids: Set<String>,
    issuerProcessedCriticalExtensionOids: Set<String>,
    requiredExtendedKeyUsageOid: String?,
): List<ParsedCertificate>? {
    try {
        current.certificate.checkValidityAt(now)
    } catch (cause: Exception) {
        throw X509ValidationException(
            "Certificate path invalid: certificate is not valid at $now: ${cause.message}",
            cause,
        )
    }
    if (current.certificate.criticalExtensionOids.any { it !in processedCriticalExtensionOids }) {
        throw X509ValidationException("Certificate path invalid: certificate has an unsupported critical extension.")
    }

    if (anchors.any { it.der == current.der }) {
        return listOf(current)
    }

    val issuers = (candidates + anchors)
        .filterNot { it.der in visited }
        .filter { current.certificate.hasIssuerNameMatching(it.certificate) }

    for (issuer in issuers) {
        if (!current.hasKeyIdentifierMatch(issuer)) continue
        val issuerCaCertificatesBelow = caCertificatesBelow + if (current.certificate.isCertificateAuthority) 1 else 0
        if (
            !issuer.certificate.canIssueCertificates(
                issuerCaCertificatesBelow,
                issuerProcessedCriticalExtensionOids,
                requiredExtendedKeyUsageOid,
            )
        ) continue

        val verified = runCatching { current.certificate.verifySignedBy(issuer.certificate) }
            .isSuccess
        if (!verified) continue

        val path = buildValidatedPath(
            current = issuer,
            candidates = candidates,
            anchors = anchors,
            visited = visited + issuer.der,
            now = now,
            caCertificatesBelow = issuerCaCertificatesBelow,
            processedCriticalExtensionOids = issuerProcessedCriticalExtensionOids,
            issuerProcessedCriticalExtensionOids = issuerProcessedCriticalExtensionOids,
            requiredExtendedKeyUsageOid = requiredExtendedKeyUsageOid,
        )
        if (path != null) {
            return listOf(current) + path
        }
    }

    return null
}

private fun PlatformX509Certificate.canIssueCertificates(
    caCertificatesBelow: Int,
    processedCriticalExtensionOids: Set<String>,
    requiredExtendedKeyUsageOid: String?,
): Boolean =
    isCertificateAuthority &&
        canSignCertificates &&
        basicConstraintsCritical &&
        keyUsageCritical &&
        criticalExtensionOids.all { it in processedCriticalExtensionOids } &&
        (requiredExtendedKeyUsageOid == null || extendedKeyUsageOids?.contains(requiredExtendedKeyUsageOid) != false) &&
        (pathLengthConstraint?.let { caCertificatesBelow <= it } ?: true)

fun validateClientAuthenticationCertificateChain(
    leaf: CertificateDer,
    chain: List<CertificateDer>,
    trustAnchors: List<CertificateDer>,
) {
    validatePlatformClientAuthenticationCertificateChain(leaf, chain, trustAnchors)
    validateCertificateChainWithExplicitTrust(
        leaf = leaf,
        chain = chain,
        trustAnchors = trustAnchors,
        enableTrustedChainRoot = false,
        additionalProcessedCriticalExtensionOids = setOf(EXTENDED_KEY_USAGE_OID),
        requiredExtendedKeyUsageOid = CLIENT_AUTH_EXTENDED_KEY_USAGE_OID,
    )
    leaf.validateClientAuthenticationCertificateUsage()
}

private const val EXTENDED_KEY_USAGE_OID = "2.5.29.37"
private const val CLIENT_AUTH_EXTENDED_KEY_USAGE_OID = "1.3.6.1.5.5.7.3.2"

internal expect fun validatePlatformClientAuthenticationCertificateChain(
    leaf: CertificateDer,
    chain: List<CertificateDer>,
    trustAnchors: List<CertificateDer>,
)

private fun ParsedCertificate.hasKeyIdentifierMatch(
    issuer: ParsedCertificate,
): Boolean {
    val authorityKeyIdentifier = certificate.authorityKeyIdentifier
    val issuerSubjectKeyIdentifier = issuer.certificate.subjectKeyIdentifier
    return authorityKeyIdentifier == null ||
            issuerSubjectKeyIdentifier == null ||
            authorityKeyIdentifier.contentEquals(issuerSubjectKeyIdentifier)
}
