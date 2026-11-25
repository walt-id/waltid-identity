package id.walt.ades.validation

import id.walt.ades.asn1.Oids
import id.walt.ades.transport.AdesTransport
import id.walt.ades.tsp.TimeStampToken
import id.walt.ades.x509.X509Certificate
import id.walt.certificate.x509.X509CertificateUtil
import id.walt.certificate.x509.truststore.InMemoryTrustStore
import id.walt.certificate.x509.validation.X509CertificateChainValidator
import id.walt.certificate.x509.validation.validator.X509CertificateAuthorityKeyIdValidator
import id.walt.certificate.x509.validation.validator.X509CertificateBasicConstraintsValidator
import id.walt.certificate.x509.validation.validator.X509CertificateCaHasKeyCertSignKeyUsageValidator
import id.walt.certificate.x509.validation.validator.X509CertificateSignatureValidator
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/** Signature validation policy / constraints (subset of ETSI TS 119 172-1 validation constraints). */
class AdesValidationPolicy(
    /** Explicit trust anchors; an empty list never establishes trust. */
    val trustAnchors: List<X509Certificate> = emptyList(),
    /** Validation (current) time; defaults to the system clock. */
    val validationTime: Instant? = null,
    /** Network access for missing issuers/OCSP/CRLs and sigD references; null = offline. */
    val transport: AdesTransport? = null,
    val fetchMissingValidationData: Boolean = transport != null,
    /** Revocation data issued within this window before the control time is considered fresh. */
    val revocationFreshness: Duration = 1.days,
    /**
     * EN 319 102-1 5.5.4 3b removes failing signature time-stamps and continues. When true, a present but
     * invalid signature/archive time-stamp propagates its validation indication/sub-indication to the signature.
     */
    val strictTimestamps: Boolean = false,
    /** Digests that are never acceptable (TS 119 152-1 6.2.1: MD5 shall not be used). */
    val forbiddenDigests: Set<String> = setOf("MD5"),
    /** Certificates / CRLs / OCSP responses supplied by the driving application (EN 319 102-1 "certificate validation data"). */
    val additionalValidationData: ValidationData = ValidationData(),
) {
    fun now(): Instant = validationTime ?: Clock.System.now()
}

data class ChainValidationResult(
    val indication: Indication,
    val subIndication: SubIndication?,
    val chain: List<X509Certificate>,
    val signingCertificateStatus: RevocationStatus?,
    val message: String,
    /** Status of each non-anchor certificate, including failures hidden by a leaf-level failure. */
    val certificateStatuses: Map<X509Certificate, RevocationStatus> = emptyMap(),
) {
    val passed get() = indication == Indication.PASSED
}

/** X.509 certificate validation building block (EN 319 102-1 5.2.6, shell model). */
class CertificateChainValidator(
    val resolver: ValidationDataResolver,
    internal val policy: AdesValidationPolicy,
) {
    /** RFC 5280 path constraints are shared with waltid-x509; AdES retains revocation and POE processing. */
    private suspend fun validatePath(chain: List<X509Certificate>, time: Instant): String? {
        val sharedChain = chain.map { it.sharedCertificate }
        val anchors = policy.trustAnchors.map { it.sharedCertificate }
        // Shell-model validity is classified separately below so trusted historical time can recover expiry.
        val validators = listOf(
            X509CertificateBasicConstraintsValidator(), X509CertificateCaHasKeyCertSignKeyUsageValidator(),
            X509CertificateAuthorityKeyIdValidator(), X509CertificateSignatureValidator(X509CertificateUtil.Default.services.signatureValidator),
        )
        val result = X509CertificateChainValidator(validators, InMemoryTrustStore(anchors))
            .validate(X509CertificateUtil.Default.services.cryptoRuntime, sharedChain)
        return result.errorLog.takeIf { !result.valid }?.joinToString { it.message }
    }

    /** Later evidence can establish historical status, but evidence stale before the control time cannot. */
    fun freshAt(time: Instant, evidenceAvailableAt: Instant = time): (RevocationStatus) -> Boolean = { status ->
        when (status) {
            is RevocationStatus.Good -> status.issuedAt <= evidenceAvailableAt &&
                status.issuedAt >= time - policy.revocationFreshness &&
                (status.nextUpdate?.let { it >= time } ?: true)
            is RevocationStatus.Revoked -> true
            is RevocationStatus.Unknown -> false
        }
    }

    suspend fun validate(
        certificate: X509Certificate,
        time: Instant,
        fresh: (RevocationStatus) -> Boolean = freshAt(time),
        requiredEku: String? = null,
    ): ChainValidationResult {
        if (resolver.isTrustAnchor(certificate)) {
            return ChainValidationResult(Indication.PASSED, null, listOf(certificate), null, "certificate is a trust anchor")
        }
        val chains = resolver.buildChains(certificate)
        if (chains.isEmpty()) {
            return ChainValidationResult(Indication.INDETERMINATE, SubIndication.NO_CERTIFICATE_CHAIN_FOUND, listOf(certificate), null,
                "no certificate chain to a trust anchor for ${certificate.subject}")
        }
        var last = ChainValidationResult(Indication.INDETERMINATE, SubIndication.CERTIFICATE_CHAIN_GENERAL_FAILURE, chains.first(), null, "no valid chain")

        chainLoop@ for (chain in chains) {
            // RFC 5280 6.1 (simplified): issuer constraints and CA validity (shell model); see validatePath().
            val pathProblem = validatePath(chain, time)
            if (pathProblem != null) {
                last = ChainValidationResult(Indication.INDETERMINATE, SubIndication.CERTIFICATE_CHAIN_GENERAL_FAILURE, chain, null, pathProblem)
                continue
            }
            if (requiredEku != null && requiredEku !in certificate.extendedKeyUsage) {
                last = ChainValidationResult(Indication.INDETERMINATE, SubIndication.CHAIN_CONSTRAINTS_FAILURE, chain, null, "certificate lacks EKU $requiredEku")
                continue
            }

            // Revocation checking for every certificate except the anchor and ocsp-nocheck responders
            val statuses = linkedMapOf<X509Certificate, RevocationStatus>()
            for (i in 0 until chain.size - 1) {
                val cert = chain[i]
                if (cert.hasOcspNoCheck && Oids.EKU_OCSP_SIGNING in cert.extendedKeyUsage) continue
                statuses[cert] = resolver.revocationStatus(cert, chain[i + 1], time, fresh)
            }
            val signingStatus = statuses[certificate]
            val revoked = statuses.entries.firstOrNull { (_, status) -> status is RevocationStatus.Revoked && status.revocationTime <= time }
            if (revoked != null) {
                val status = revoked.value as RevocationStatus.Revoked
                last = ChainValidationResult(Indication.INDETERMINATE,
                    if (revoked.key == certificate) SubIndication.REVOKED_NO_POE else SubIndication.REVOKED_CA_NO_POE,
                    chain, signingStatus, "certificate ${revoked.key.subject} revoked at ${status.revocationTime}", statuses)
                continue@chainLoop
            }

            // Step 7: validity of the signing certificate and every CA at the control time.
            val expired = chain.dropLast(1).firstOrNull { !it.isValidAt(time) }
            if (expired != null) {
                val status = statuses[expired]
                val notRevoked = status is RevocationStatus.Good && status.issuedAt > expired.notAfter
                last = ChainValidationResult(Indication.INDETERMINATE,
                    if (notRevoked) SubIndication.OUT_OF_BOUNDS_NOT_REVOKED else SubIndication.OUT_OF_BOUNDS_NO_POE,
                    chain, signingStatus, "certificate ${expired.subject} validity ${expired.notBefore}..${expired.notAfter} does not include $time", statuses)
                continue@chainLoop
            }
            val missing = statuses.entries.firstOrNull { (_, status) -> status is RevocationStatus.Unknown || !fresh(status) }
            if (missing != null) {
                last = ChainValidationResult(Indication.INDETERMINATE, SubIndication.TRY_LATER, chain, signingStatus,
                    "no fresh revocation information for ${missing.key.subject}" + ((missing.value as? RevocationStatus.Unknown)?.let { ": ${it.reason}" } ?: ""), statuses)
                continue@chainLoop
            }
            return ChainValidationResult(Indication.PASSED, null, chain, signingStatus, "chain of ${chain.size} certificate(s) to ${chain.last().subject}", statuses)
        }
        return last
    }
}

/** Format-neutral description of a time-stamp found in a signature, with the bytes its imprint must cover. */
class TimestampEvidence(
    val type: TimestampType,
    /** DER TimeStampToken, or null when the container could not be decoded. */
    val token: ByteArray?,
    /** Message-imprint input computed per the format specification (null if it could not be computed). */
    val coveredData: ByteArray?,
    val description: String = type.name.lowercase(),
)

/** Time-stamp validation (EN 319 102-1 5.4). */
class TimestampValidator(
    private val chainValidator: CertificateChainValidator,
) {
    suspend fun validate(evidence: TimestampEvidence): TimestampValidationResult {
        val bytes = evidence.token
            ?: return TimestampValidationResult(evidence.type, Indication.FAILED, SubIndication.FORMAT_FAILURE, null, null, listOf("time-stamp token missing"))
        val token = runCatching { TimeStampToken(bytes) }.getOrElse {
            return TimestampValidationResult(evidence.type, Indication.FAILED, SubIndication.FORMAT_FAILURE, null, null, listOf("invalid time-stamp token: ${it.message}"))
        }
        token.certificates.forEach(chainValidator.resolver.pool::add)
        val v = try {
            token.verify(data = evidence.coveredData, additionalCertificates = chainValidator.resolver.pool.certificates)
        } catch (e: RuntimeException) {
            if (e !is IllegalArgumentException && e !is NoSuchElementException && e !is IndexOutOfBoundsException) throw e
            return TimestampValidationResult(evidence.type, Indication.FAILED, SubIndication.FORMAT_FAILURE, null, null,
                listOf("malformed time-stamp token: ${e.message}"))
        }
        val genTime = token.genTime
        if (evidence.coveredData == null || !v.messageImprintMatches) {
            return TimestampValidationResult(evidence.type, Indication.FAILED, SubIndication.HASH_FAILURE, genTime, v.signerCertificate, v.problems)
        }
        val tsa = v.signerCertificate
            ?: return TimestampValidationResult(evidence.type, Indication.INDETERMINATE, SubIndication.NO_SIGNING_CERTIFICATE_FOUND, genTime, null, v.problems)
        if (!v.signatureValid) return TimestampValidationResult(evidence.type, Indication.FAILED, SubIndication.SIG_CRYPTO_FAILURE, genTime, tsa, v.problems)
        if (!v.signingCertificateReferenceValid) return TimestampValidationResult(evidence.type, Indication.FAILED, SubIndication.FORMAT_FAILURE, genTime, tsa, v.problems)

        if (!v.timestampingCertificateValid) return TimestampValidationResult(
            evidence.type, Indication.INDETERMINATE, SubIndication.CHAIN_CONSTRAINTS_FAILURE, genTime, tsa,
            v.problems + "TSA certificate must have only id-kp-timeStamping in a critical EKU extension",
        )

        // TSA certificate validated at generation time; revocation known at any later point counts.
        // Evidence already stale before that time cannot establish the TSA's historical status.
        val chain = chainValidator.validate(tsa, genTime,
            fresh = chainValidator.freshAt(genTime, chainValidator.policy.validationTime ?: Clock.System.now()))
        val problems = v.problems + listOfNotNull(chain.message.takeIf { !chain.passed })
        return when {
            chain.passed -> TimestampValidationResult(evidence.type, Indication.PASSED, null, genTime, tsa, problems, token.tstInfo.accuracy)
            chain.subIndication == SubIndication.REVOKED_NO_POE ->
                TimestampValidationResult(evidence.type, Indication.FAILED, SubIndication.REVOKED, genTime, tsa, problems, token.tstInfo.accuracy)
            else -> TimestampValidationResult(evidence.type, Indication.INDETERMINATE, chain.subIndication, genTime, tsa, problems, token.tstInfo.accuracy)
        }
    }
}
