package id.walt.openid4vci.clientauth.attestation.verifier

import id.walt.certificate.x509.X509Certificate
import id.walt.certificate.x509.validation.X509CertificateChain
import id.walt.trust.decision.TrustCheck
import id.walt.trust.decision.TrustDecisionEvaluator
import id.walt.trust.decision.TrustDecisionRequest
import id.walt.trust.decision.TrustPurpose
import id.walt.trust.decision.TrustRequirement
import id.walt.trust.decision.TrustSubject
import id.walt.trust.decision.check
import id.walt.trust.model.TrustDecisionCode
import id.walt.trust.model.TrustedEntityType
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.JsonObject
import kotlin.io.encoding.Base64

/**
 * Trusts a client attestation whose x5c chain, leaf first, a trust registry lists for one of
 * [expectedEntityTypes], from an accepted source that is fresh ([TrustRequirement.RegistryFresh]).
 *
 * The x5c must be one leaf-first chain, and the signature is verified with the leaf key, before the
 * registry is asked, so a malformed or forged attestation never reaches it. A registry denial is [ClientAttestationVerificationResult.Rejected]. When the issuer
 * cannot decide, verification throws [IllegalStateException] instead, so the client is not blamed for a
 * server fault: no [evaluator] is configured, or the trust source is unavailable
 * ([TrustDecisionCode.UNKNOWN]). Evaluator exceptions propagate unchanged.
 */
class X509TrustRegistryClientAttestationVerifier(
    private val evaluator: TrustDecisionEvaluator?,
    private val expectedEntityTypes: Set<TrustedEntityType>,
    private val requireAuthenticatedSource: Boolean = false,
) : ClientAttestationVerifier {

    init {
        require(expectedEntityTypes.isNotEmpty()) { "expectedEntityTypes must not be empty" }
    }

    @Suppress("UNUSED_PARAMETER")
    override suspend fun verifyAttestationJwt(
        jwt: String,
        header: JsonObject,
        payload: JsonObject,
    ): ClientAttestationVerificationResult {
        val certificateChain = clientAttestationX5c(header) { return it }
        if (!certificateChain.isOneLeafFirstChain()) {
            return ClientAttestationVerificationResult.Rejected("Client attestation x5c must contain one leaf-first certificate chain")
        }
        val verified = verifyClientAttestationLeafSignature(jwt, header)
        if (verified is ClientAttestationVerificationResult.Rejected) return verified

        val evaluator = evaluator
            ?: error("Client attestation trust registry is not configured for this issuer")
        val request = TrustDecisionRequest(
            purpose = TrustPurpose.ISSUER_WUA_SIGNER,
            subject = TrustSubject.CertificateChain(certificateChain.map { Base64.encode(it.encodedDer.toByteArray()) }),
            expectedEntityTypes = expectedEntityTypes,
        )
        return when (val check = evaluator.check(request, TrustRequirement.RegistryFresh(requireAuthenticatedSource))) {
            is TrustCheck.Accepted -> verified
            is TrustCheck.Rejected -> {
                if (check.decision?.decision == TrustDecisionCode.UNKNOWN) {
                    log.warn { "Client attestation trust source is unavailable: ${check.decision?.warnings}" }
                    error("Client attestation trust source is unavailable")
                }
                log.info { "Client attestation signer is not trusted: ${check.reason}" }
                ClientAttestationVerificationResult.Rejected("Client attestation signer is not trusted")
            }

            TrustCheck.Skipped -> error("A registry trust requirement is never skipped")
        }
    }

    private companion object {
        val log = KotlinLogging.logger { }
    }
}

/** Each certificate exactly once, leaf first, each followed by its issuer, as key attestations also require. */
private fun List<X509Certificate>.isOneLeafFirstChain(): Boolean =
    try {
        X509CertificateChain.of(this).isProvidedLeafFirst
    } catch (_: IllegalArgumentException) {
        false
    }
