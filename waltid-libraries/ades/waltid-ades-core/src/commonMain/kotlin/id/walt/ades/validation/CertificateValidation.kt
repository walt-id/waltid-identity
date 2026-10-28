package id.walt.ades.validation

import id.walt.ades.asn1.Oids
import id.walt.ades.transport.AdesTransport
import id.walt.ades.tsp.TimeStampToken
import id.walt.ades.x509.KeyUsageBit
import id.walt.ades.x509.X509Certificate
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/** Signature validation policy / constraints (subset of ETSI TS 119 172-1 validation constraints). */
class AdesValidationPolicy(
    /** Trust anchors; when empty, chains ending in any self-signed root are accepted (test/diagnostic mode). */
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
     * cryptographically broken time-stamp (wrong imprint / signature) makes the whole signature TOTAL-FAILED.
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
) {
    val passed get() = indication == Indication.PASSED
}

/** X.509 certificate validation building block (EN 319 102-1 5.2.6, shell model). */
class CertificateChainValidator(
    val resolver: ValidationDataResolver,
    private val policy: AdesValidationPolicy,
) {
    fun freshAt(time: Instant): (RevocationStatus) -> Boolean = { status ->
        when (status) {
            is RevocationStatus.Good -> status.issuedAt >= time - policy.revocationFreshness || (status.nextUpdate != null && status.nextUpdate >= time && status.issuedAt <= time)
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
            // RFC 5280 6.1 (simplified): issuer constraints and CA validity (shell model)
            for (i in 0 until chain.size - 1) {
                val ca = chain[i + 1]
                val anchor = resolver.isTrustAnchor(ca) && i + 1 == chain.lastIndex
                if (!anchor) {
                    if (!ca.isCa || (ca.keyUsage?.contains(KeyUsageBit.KEY_CERT_SIGN) == false)) {
                        last = ChainValidationResult(Indication.INDETERMINATE, SubIndication.CERTIFICATE_CHAIN_GENERAL_FAILURE, chain, null, "${ca.subject} is not a CA")
                        continue@chainLoop
                    }
                    if (!ca.isValidAt(time)) {
                        last = ChainValidationResult(Indication.INDETERMINATE, SubIndication.CERTIFICATE_CHAIN_GENERAL_FAILURE, chain, null, "CA ${ca.subject} not valid at $time")
                        continue@chainLoop
                    }
                }
            }
            if (requiredEku != null && requiredEku !in certificate.extendedKeyUsage) {
                last = ChainValidationResult(Indication.INDETERMINATE, SubIndication.CHAIN_CONSTRAINTS_FAILURE, chain, null, "certificate lacks EKU $requiredEku")
                continue
            }

            // Revocation checking for every certificate except the anchor and ocsp-nocheck responders
            var signingStatus: RevocationStatus? = null
            for (i in 0 until chain.size - 1) {
                val cert = chain[i]
                if (cert.hasOcspNoCheck && Oids.EKU_OCSP_SIGNING in cert.extendedKeyUsage) continue
                val status = resolver.revocationStatus(cert, chain[i + 1], time, fresh)
                if (i == 0) signingStatus = status
                when {
                    status is RevocationStatus.Revoked && status.revocationTime <= time -> {
                        if (i == 0) {
                            return ChainValidationResult(Indication.INDETERMINATE, SubIndication.REVOKED_NO_POE, chain, status,
                                "signing certificate revoked at ${status.revocationTime} (${status.reason})")
                        }
                        last = ChainValidationResult(Indication.INDETERMINATE, SubIndication.REVOKED_CA_NO_POE, chain, signingStatus,
                            "CA certificate ${cert.subject} revoked at ${status.revocationTime}")
                        continue@chainLoop
                    }

                    status is RevocationStatus.Unknown || !fresh(status) -> {
                        last = ChainValidationResult(Indication.INDETERMINATE, SubIndication.TRY_LATER, chain, signingStatus,
                            "no fresh revocation information for ${cert.subject}" + ((status as? RevocationStatus.Unknown)?.let { ": ${it.reason}" } ?: ""))
                        continue@chainLoop
                    }
                }
            }

            // Step 7: validity of the signing certificate at validation time
            if (!certificate.isValidAt(time)) {
                val notRevoked = signingStatus is RevocationStatus.Good && signingStatus.issuedAt > certificate.notAfter
                return ChainValidationResult(
                    Indication.INDETERMINATE,
                    if (notRevoked) SubIndication.OUT_OF_BOUNDS_NOT_REVOKED else SubIndication.OUT_OF_BOUNDS_NO_POE,
                    chain, signingStatus, "signing certificate validity ${certificate.notBefore}..${certificate.notAfter} does not include $time",
                )
            }
            return ChainValidationResult(Indication.PASSED, null, chain, signingStatus, "chain of ${chain.size} certificate(s) to ${chain.last().subject}")
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
        val v = token.verify(data = evidence.coveredData, additionalCertificates = chainValidator.resolver.pool.certificates)
        val genTime = token.genTime
        if (evidence.coveredData == null || !v.messageImprintMatches) {
            return TimestampValidationResult(evidence.type, Indication.FAILED, SubIndication.HASH_FAILURE, genTime, v.signerCertificate, v.problems)
        }
        val tsa = v.signerCertificate
            ?: return TimestampValidationResult(evidence.type, Indication.INDETERMINATE, SubIndication.NO_SIGNING_CERTIFICATE_FOUND, genTime, null, v.problems)
        if (!v.signatureValid) return TimestampValidationResult(evidence.type, Indication.FAILED, SubIndication.SIG_CRYPTO_FAILURE, genTime, tsa, v.problems)
        if (!v.signingCertificateReferenceValid) return TimestampValidationResult(evidence.type, Indication.FAILED, SubIndication.FORMAT_FAILURE, genTime, tsa, v.problems)

        // TSA certificate validated at generation time; revocation known at any later point counts.
        val chain = chainValidator.validate(tsa, genTime, fresh = { it !is RevocationStatus.Unknown })
        val problems = v.problems + listOfNotNull(chain.message.takeIf { !chain.passed })
        return when {
            chain.passed -> TimestampValidationResult(evidence.type, Indication.PASSED, null, genTime, tsa, problems)
            chain.subIndication == SubIndication.REVOKED_NO_POE ->
                TimestampValidationResult(evidence.type, Indication.FAILED, SubIndication.REVOKED, genTime, tsa, problems)
            else -> TimestampValidationResult(evidence.type, Indication.INDETERMINATE, chain.subIndication, genTime, tsa, problems)
        }
    }
}
