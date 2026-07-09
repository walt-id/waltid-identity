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
): List<ParsedCertificate>? {
    try {
        current.certificate.checkValidityAt(now)
    } catch (cause: Exception) {
        throw X509ValidationException(
            "Certificate path invalid: certificate is not valid at $now: ${cause.message}",
            cause,
        )
    }

    if (anchors.any { it.der == current.der }) {
        return listOf(current)
    }

    val issuers = (candidates + anchors)
        .filterNot { it.der in visited }
        .filter { current.certificate.hasIssuerNameMatching(it.certificate) }

    for (issuer in issuers) {
        if (!current.hasKeyIdentifierMatch(issuer)) continue

        val verified = runCatching { current.certificate.verifySignedBy(issuer.certificate) }
            .isSuccess
        if (!verified) continue

        val path = buildValidatedPath(
            current = issuer,
            candidates = candidates,
            anchors = anchors,
            visited = visited + issuer.der,
            now = now,
        )
        if (path != null) {
            return listOf(current) + path
        }
    }

    return null
}

private fun ParsedCertificate.hasKeyIdentifierMatch(
    issuer: ParsedCertificate,
): Boolean {
    val authorityKeyIdentifier = certificate.authorityKeyIdentifier
    val issuerSubjectKeyIdentifier = issuer.certificate.subjectKeyIdentifier
    return authorityKeyIdentifier == null ||
            issuerSubjectKeyIdentifier == null ||
            authorityKeyIdentifier.contentEquals(issuerSubjectKeyIdentifier)
}
