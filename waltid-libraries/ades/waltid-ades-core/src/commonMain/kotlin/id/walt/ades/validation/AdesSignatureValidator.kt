package id.walt.ades.validation

import id.walt.ades.crypto.AdesSignatureAlgorithm
import id.walt.ades.x509.OcspResponse
import id.walt.ades.x509.X509Certificate
import id.walt.ades.x509.X509Crl
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * Everything a format module (CB-AdES, JAdES, XAdES, PAdES) extracts from one signature so the format-neutral
 * EN 319 102-1 processes can run. Format-specific checks are reported through [checks]; FAILED checks flagged as
 * format failures make the signature TOTAL-FAILED/FORMAT_FAILURE.
 */
class SignatureEvidence(
    val signatureId: String,
    val checks: List<ValidationCheck>,
    val signingCertificate: X509Certificate?,
    val signatureAlgorithm: AdesSignatureAlgorithm?,
    /** Result of the cryptographic verification of the signature value over the signed data. */
    val signatureValueValid: Boolean,
    /** False when (detached) signed data could not be obtained. */
    val signedDataFound: Boolean = true,
    /** True when a signed data-object digest (e.g. sigD hashV) does not match. */
    val hashFailure: Boolean = false,
    /** Signature policy processing problem (e.g. sigPId digest mismatch). */
    val policyError: String? = null,
    val claimedSigningTime: Instant? = null,
    val certificates: List<X509Certificate> = emptyList(),
    val crls: List<X509Crl> = emptyList(),
    val ocsps: List<OcspResponse> = emptyList(),
    val contentTimestamps: List<TimestampEvidence> = emptyList(),
    val signatureTimestamps: List<TimestampEvidence> = emptyList(),
    val archiveTimestamps: List<TimestampEvidence> = emptyList(),
    val level: AdesLevel? = null,
) {
    val formatFailures: List<ValidationCheck> get() = checks.filter { it.status == CheckStatus.FAILED && it.id.startsWith(FORMAT_PREFIX) }

    companion object {
        const val FORMAT_PREFIX = "format:"
    }
}

/** ETSI EN 319 102-1 validation for one signature (basic, with time/LTV, archive time-stamps as POE). */
class AdesSignatureValidator(private val policy: AdesValidationPolicy) {

    suspend fun validate(evidence: SignatureEvidence): SignatureValidationResult {
        val now = policy.now()
        val checks = evidence.checks.toMutableList()
        val pool = ValidationData(evidence.certificates, evidence.crls, evidence.ocsps).also { it.addAll(policy.additionalValidationData) }
        val resolver = ValidationDataResolver(pool, policy.transport, policy.trustAnchors, policy.fetchMissingValidationData)
        val chainValidator = CertificateChainValidator(resolver, policy)
        val tsValidator = TimestampValidator(chainValidator)
        val timestamps = mutableListOf<TimestampValidationResult>()

        fun result(indication: Indication, sub: SubIndication?, chain: List<X509Certificate> = emptyList(), best: Instant = now) =
            SignatureValidationResult(
                evidence.signatureId, indication, sub, evidence.signingCertificate, chain, evidence.claimedSigningTime,
                best, now, evidence.level, timestamps.toList(), checks.toList(),
            )

        // 5.3.4 step 1: format checking
        if (evidence.formatFailures.isNotEmpty()) return result(Indication.TOTAL_FAILED, SubIndication.FORMAT_FAILURE)

        // step 2: signing certificate identification
        val signer = evidence.signingCertificate ?: return result(Indication.INDETERMINATE, SubIndication.NO_SIGNING_CERTIFICATE_FOUND)

        evidence.policyError?.let {
            checks += ValidationCheck("policy", CheckStatus.FAILED, it, "EN 319 102-1 5.2.4")
            return result(Indication.INDETERMINATE, SubIndication.POLICY_PROCESSING_ERROR)
        }

        // 5.2.7 cryptographic verification
        if (!evidence.signedDataFound) return result(Indication.INDETERMINATE, SubIndication.SIGNED_DATA_NOT_FOUND)
        if (evidence.hashFailure) return result(Indication.TOTAL_FAILED, SubIndication.HASH_FAILURE)
        if (!evidence.signatureValueValid) {
            checks += ValidationCheck("crypto:signature-value", CheckStatus.FAILED, "signature value does not verify with the signing certificate", "EN 319 102-1 5.2.7")
            return result(Indication.TOTAL_FAILED, SubIndication.SIG_CRYPTO_FAILURE)
        }
        checks += ValidationCheck("crypto:signature-value", CheckStatus.PASSED,
            "${evidence.signatureAlgorithm?.name ?: "signature"} verifies with ${signer.subject}", "EN 319 102-1 5.2.7")
        evidence.signatureAlgorithm?.digest?.let { d ->
            if (d.displayName in policy.forbiddenDigests) {
                checks += ValidationCheck("crypto:constraints", CheckStatus.FAILED, "${d.displayName} is not acceptable", "EN 319 102-1 5.2.8")
                return result(Indication.INDETERMINATE, SubIndication.CRYPTO_CONSTRAINTS_FAILURE_NO_POE)
            }
        }

        // Content time-stamps (used to refine basic validation)
        val contentTs = evidence.contentTimestamps.map { tsValidator.validate(it) }.also { timestamps += it }

        // 5.3.4 step 4: X.509 validation at current time
        var x509 = chainValidator.validate(signer, now)
        checks += ValidationCheck("x509:signing-certificate", if (x509.passed) CheckStatus.PASSED else CheckStatus.WARNING, x509.message, "EN 319 102-1 5.2.6")
        val latestContentTs = contentTs.filter { it.passed }.mapNotNull { it.genTime }.maxOrNull()
        if (latestContentTs != null) {
            val revoked = x509.signingCertificateStatus as? RevocationStatus.Revoked
            if (x509.subIndication == SubIndication.REVOKED_NO_POE && revoked != null && latestContentTs > revoked.revocationTime) {
                return result(Indication.TOTAL_FAILED, SubIndication.REVOKED, x509.chain)
            }
            if (x509.subIndication in setOf(SubIndication.OUT_OF_BOUNDS_NO_POE, SubIndication.OUT_OF_BOUNDS_NOT_REVOKED) && latestContentTs > signer.notAfter) {
                return result(Indication.TOTAL_FAILED, SubIndication.EXPIRED, x509.chain)
            }
        }
        val continuable = setOf(
            null, SubIndication.CRYPTO_CONSTRAINTS_FAILURE_NO_POE, SubIndication.REVOKED_NO_POE, SubIndication.REVOKED_CA_NO_POE,
            SubIndication.TRY_LATER, SubIndication.OUT_OF_BOUNDS_NO_POE, SubIndication.OUT_OF_BOUNDS_NOT_REVOKED,
        )
        if (!x509.passed && x509.subIndication !in continuable) return result(x509.indication.toTotal(), x509.subIndication, x509.chain)

        // 5.5.4 step 1/3: best-signature-time from signature (and archive) time-stamps
        var best = now
        val sigTs = evidence.signatureTimestamps.map { tsValidator.validate(it) }.also { timestamps += it }
        val arcTs = evidence.archiveTimestamps.map { tsValidator.validate(it) }.also { timestamps += it }
        for ((ts, ev) in (sigTs zip evidence.signatureTimestamps) + (arcTs zip evidence.archiveTimestamps)) {
            val label = "timestamp:${ev.description}"
            if (ts.passed) {
                checks += ValidationCheck(label, CheckStatus.PASSED, "${ev.description} at ${ts.genTime} by ${ts.tsaCertificate?.subject}", "EN 319 102-1 5.4")
                if (ts.genTime!! < best) best = ts.genTime
            } else {
                checks += ValidationCheck(label, if (policy.strictTimestamps) CheckStatus.FAILED else CheckStatus.WARNING,
                    "${ev.description} rejected: ${ts.indication}/${ts.subIndication}: ${ts.problems.joinToString("; ")}", "EN 319 102-1 5.4")
                if (policy.strictTimestamps && ts.indication == Indication.FAILED) return result(Indication.TOTAL_FAILED, ts.subIndication, x509.chain)
            }
        }

        // 5.5.4 step 6: TRY_LATER because revocation data was not fresh at current time -> freshness vs best-signature-time
        if (x509.subIndication == SubIndication.TRY_LATER && best < now) {
            val bestTime = best
            x509 = chainValidator.validate(signer, now, fresh = { it !is RevocationStatus.Unknown && (it.issuedAt ?: Instant.DISTANT_PAST) >= bestTime })
            if (x509.subIndication == SubIndication.TRY_LATER) {
                // LTV: validate the chain at best-signature-time with the embedded material (past certificate validation)
                x509 = chainValidator.validate(signer, bestTime, fresh = { it !is RevocationStatus.Unknown && (it.issuedAt ?: Instant.DISTANT_PAST) >= bestTime })
            }
        }
        // Expired signing/CA certificates protected by POE: past certificate validation at best-signature-time (5.6.2.1)
        if (x509.subIndication in setOf(SubIndication.OUT_OF_BOUNDS_NO_POE, SubIndication.OUT_OF_BOUNDS_NOT_REVOKED, SubIndication.CERTIFICATE_CHAIN_GENERAL_FAILURE) && best < now) {
            val past = chainValidator.validate(signer, best, fresh = { it !is RevocationStatus.Unknown })
            if (past.passed) {
                checks += ValidationCheck("x509:past-validation", CheckStatus.PASSED, "chain valid at best-signature-time $best", "EN 319 102-1 5.6.2.1")
                x509 = past
            }
        }

        // 5.5.4 step 4: comparing times
        when (x509.subIndication) {
            SubIndication.REVOKED_NO_POE, SubIndication.REVOKED_CA_NO_POE -> {
                val revocationTime = (x509.signingCertificateStatus as? RevocationStatus.Revoked)?.revocationTime
                if (revocationTime == null || revocationTime <= best) return result(Indication.INDETERMINATE, x509.subIndication, x509.chain, best)
                if (best < signer.notBefore) return result(Indication.TOTAL_FAILED, SubIndication.NOT_YET_VALID, x509.chain, best)
                if (best > signer.notAfter) return result(Indication.INDETERMINATE, SubIndication.OUT_OF_BOUNDS_NOT_REVOKED, x509.chain, best)
                checks += ValidationCheck("x509:revocation-poe", CheckStatus.PASSED, "signature existed at $best, before revocation at $revocationTime", "EN 319 102-1 5.5.4")
            }

            null, SubIndication.OUT_OF_BOUNDS_NO_POE -> {
                if (best < signer.notBefore) return result(Indication.TOTAL_FAILED, SubIndication.NOT_YET_VALID, x509.chain, best)
                if (x509.subIndication != null) return result(Indication.INDETERMINATE, x509.subIndication, x509.chain, best)
            }

            SubIndication.OUT_OF_BOUNDS_NOT_REVOKED -> {
                if (best < signer.notBefore) return result(Indication.TOTAL_FAILED, SubIndication.NOT_YET_VALID, x509.chain, best)
                if (best > signer.notAfter) return result(Indication.INDETERMINATE, SubIndication.OUT_OF_BOUNDS_NOT_REVOKED, x509.chain, best)
            }

            else -> return result(Indication.INDETERMINATE, x509.subIndication, x509.chain, best)
        }

        // 4e: time-stamp ordering (content <= signature <= archive; equal times are accepted)
        val contentMax = contentTs.filter { it.passed }.mapNotNull { it.genTime }.maxOrNull()
        val sigTimes = sigTs.filter { it.passed }.mapNotNull { it.genTime }
        val arcMin = arcTs.filter { it.passed }.mapNotNull { it.genTime }.minOrNull()
        if ((contentMax != null && sigTimes.any { it < contentMax }) || (arcMin != null && sigTimes.any { it > arcMin })) {
            return result(Indication.INDETERMINATE, SubIndication.TIMESTAMP_ORDER_FAILURE, x509.chain, best)
        }
        if (evidence.claimedSigningTime != null && evidence.claimedSigningTime > best + CLOCK_SKEW && best < now) {
            checks += ValidationCheck("time:claimed-signing-time", CheckStatus.WARNING,
                "claimed signing time ${evidence.claimedSigningTime} is after best-signature-time $best", "EN 319 102-1 5.5.4")
        }
        return result(Indication.TOTAL_PASSED, null, x509.chain, best)
    }

    private fun Indication.toTotal() = when (this) {
        Indication.PASSED -> Indication.TOTAL_PASSED
        Indication.FAILED -> Indication.TOTAL_FAILED
        else -> this
    }

    companion object {
        private val CLOCK_SKEW = Duration.parse("5m")
    }
}
