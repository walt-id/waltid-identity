package id.walt.trust.decision

import id.walt.certificate.x509.X509Certificate
import id.walt.certificate.x509.X509CertificateTrustStore
import id.walt.certificate.x509.X509CertificateUtil
import id.walt.trust.model.FreshnessState
import id.walt.trust.model.TrustDecision
import id.walt.trust.model.TrustDecisionCode
import id.walt.trust.model.TrustEvidence
import kotlinx.io.bytestring.ByteString
import kotlin.coroutines.cancellation.CancellationException
import kotlin.io.encoding.Base64

/**
 * Validates certificate chains against configured anchors per purpose (PKIX path validation through
 * [X509CertificateUtil]).
 *
 * The chain is validated up to the first certificate that is itself one of the anchors (same DER), so a
 * pinned leaf or intermediate may still be presented with its own issuers. This is the rule of
 * `X5cTrustAnchors` in `waltid-credential-key-resolver`, so a pin gets the same answer from both.
 * A certificate that is only in the chain, such as a root with an anchor's name, is never an anchor.
 *
 * Static anchors carry no registry roles and no freshness: decisions report [FreshnessState.UNKNOWN]
 * and an unaccepted source, so they satisfy [TrustRequirement.Trusted] but never
 * [TrustRequirement.RegistryFresh]. The anchors configured per purpose stand in for the role check, so
 * decisions name no entity and carry [EVIDENCE_TYPE] evidence instead. Certificate validity is checked
 * at the current time, not at [TrustDecisionRequest.at].
 *
 * A purpose without anchors yields [TrustDecisionCode.UNSUPPORTED_SOURCE].
 */
class StaticTrustDecisionEvaluator(
    private val anchors: Map<TrustPurpose, X509CertificateTrustStore>,
) : TrustDecisionEvaluator {

    override suspend fun evaluate(request: TrustDecisionRequest): TrustDecision {
        val store = anchors[request.purpose]
            ?: return decision(TrustDecisionCode.UNSUPPORTED_SOURCE, "No static trust anchors are configured for ${request.purpose}")

        val chain = when (val subject = request.subject) {
            is TrustSubject.CertificateChain -> try {
                subject.pemOrDer.map(::parseCertificate)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                return decision(TrustDecisionCode.PROCESSING_ERROR, "Certificate chain could not be parsed")
            }
        }

        val anchorIndex = chain.indexOfFirst { certificate ->
            store.findCertificateBySubjectDn(certificate.data.subjectDn).any { it.encodedDer == certificate.encodedDer }
        }
        val path = if (anchorIndex < 0) chain else chain.take(anchorIndex + 1)

        val result = try {
            X509CertificateUtil.validateCertificateChain(path, store)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return decision(TrustDecisionCode.PROCESSING_ERROR, "Certificate chain could not be validated")
        }

        if (!result.valid) {
            return decision(TrustDecisionCode.NOT_TRUSTED, "Certificate chain does not validate against the static anchors for ${request.purpose}")
        }
        return TrustDecision(
            decision = TrustDecisionCode.TRUSTED,
            sourceFreshness = FreshnessState.UNKNOWN,
            evidence = listOf(
                TrustEvidence(
                    type = EVIDENCE_TYPE,
                    value = "Chain validated against static anchors for ${request.purpose}",
                    metadata = mapOf("leafCertificateSha256" to chain.first().fingerprintSha256Hex),
                )
            ),
        )
    }

    private fun decision(code: TrustDecisionCode, warning: String) = TrustDecision(
        decision = code,
        sourceFreshness = FreshnessState.UNKNOWN,
        warnings = listOf(warning),
    )

    private fun parseCertificate(pemOrDer: String): X509Certificate =
        if (pemOrDer.contains("-----BEGIN")) {
            X509CertificateUtil.parseCertificatePem(pemOrDer)
        } else {
            X509CertificateUtil.parseCertificateDerEncoded(ByteString(Base64.decode(pemOrDer.filterNot(Char::isWhitespace))))
        }

    companion object {
        const val EVIDENCE_TYPE = "STATIC_TRUST_ANCHOR"
    }
}
