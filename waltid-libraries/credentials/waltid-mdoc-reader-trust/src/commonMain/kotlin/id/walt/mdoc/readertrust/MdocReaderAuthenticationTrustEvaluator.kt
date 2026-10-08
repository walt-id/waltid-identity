package id.walt.mdoc.readertrust

import id.walt.certificate.x509.X509CertificateUtil
import id.walt.certificate.x509.profile.IsoMdocReaderAuthenticationX509CertificateProfile
import id.walt.certificate.x509.truststore.InMemoryTrustStore
import id.walt.certificate.x509.validation.ValidationResult
import id.walt.certificate.x509.validation.validator.X509CertificateAuthorityKeyIdValidator
import id.walt.certificate.x509.validation.validator.X509CertificateSignatureValidator
import id.walt.mdoc.readertrust.MdocReaderAuthenticationCertificateUtil.mdocReaderAuthentication
import kotlinx.coroutines.CancellationException
import kotlin.time.Clock

/**
 * Secure default: a verified signature alone never makes a reader trusted.
 *
 * Without a [trustStore] (or with an empty one) only the reader authentication certificate itself can
 * be checked (ISO/IEC 18013-5 reader-authentication profile and validity). Findings that need a trust
 * anchor — issuer signatures, authority key identifiers and the chain-length rule — are skipped, and the outcome is always
 * untrusted; only the reason tells a profile-conforming reader apart from a non-conforming one.
 *
 * With a non-empty [trustStore] its certificates are used as trust anchors: a reader whose chain
 * validates against one of them is [ReaderTrustState.TRUSTED]. The reader-supplied chain never
 * becomes a trust anchor by itself.
 */
open class MdocReaderAuthenticationTrustEvaluator(
    clock: Clock = Clock.System,
    private val trustStore: InMemoryTrustStore? = null,
) : ReaderTrustEvaluator {

    private val mdocReaderAuthenticationX509CertificateUtil = mdocReaderAuthentication(clock)

    override suspend fun evaluate(evidence: ReaderAuthenticationEvidence): ReaderTrustDecision {
        val chainDer = evidence.certificateChainDer
        if (chainDer.isEmpty()) return untrusted(NO_POLICY_REASON)
        val anchors = trustStore?.takeUnless { it.isEmpty() }
        val chain = try {
            chainDer.map { X509CertificateUtil.parseCertificateDerEncoded(it) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Throwable) {
            return untrusted("$INVALID_CERTIFICATE_REASON: Failed to parse certificate: ${e.message}")
        }
        val result = try {
            mdocReaderAuthenticationX509CertificateUtil
                .validateCertificateChain(chain, anchors ?: InMemoryTrustStore())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Throwable) {
            return untrusted("Reader authentication certificate chain could not be validated: ${e.message}")
        }
        val errors = result.errorLog
        val profileErrors = errors.filter { it.validatorId !in ISSUER_DEPENDENT_VALIDATOR_IDS }
        return when {
            profileErrors.isNotEmpty() ->
                untrusted("$INVALID_CERTIFICATE_REASON: ${profileErrors.joinToString { it.message }}")

            anchors == null -> untrusted(NO_POLICY_REASON)
            result.valid -> ReaderTrustDecision(ReaderTrustState.TRUSTED)
            else -> untrusted(
                "Reader authentication certificate chain does not validate against a configured trust anchor: " +
                        errors.joinToString { it.message }
            )
        }
    }

    private fun untrusted(reason: String) = ReaderTrustDecision(
        state = ReaderTrustState.VALID_BUT_UNTRUSTED,
        reason = reason,
    )

    /** Default instance evaluating certificate validity against [Clock.System]. */
    companion object Default : MdocReaderAuthenticationTrustEvaluator() {
        private const val INVALID_CERTIFICATE_REASON = "Reader authentication certificate is not valid"
        private const val NO_POLICY_REASON =
            "Reader authentication is cryptographically valid, but no reader trust policy is configured"

        // Findings about the issuer side of the path; without a trust anchor they cannot be evaluated, with one
        // they mean the reader does not chain to it rather than that its certificate is malformed.
        private val ISSUER_DEPENDENT_VALIDATOR_IDS = setOf(
            X509CertificateSignatureValidator.ID,
            X509CertificateAuthorityKeyIdValidator.ID,
            "${IsoMdocReaderAuthenticationX509CertificateProfile.ID}.chain-length",
        )
    }
}
