package id.walt.trust.decision

import id.walt.trust.model.TrustDecision
import id.walt.trust.model.TrustDecisionCode
import id.walt.trust.model.TrustedEntityType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.Instant

/** The question a trust decision answers. Adapters may hold different anchors per purpose. */
@Serializable
enum class TrustPurpose {
    /** Wallet: is the verifier that signed this authorization request trusted? */
    @SerialName("wallet_presentation_auth_request")
    WALLET_PRESENTATION_AUTH_REQUEST,

    /** Wallet: is the issuer certificate of an offered or received credential trusted? */
    @SerialName("wallet_issuance_issuer_cert")
    WALLET_ISSUANCE_ISSUER_CERT,

    /** Issuer: is the signer of a wallet unit or client attestation trusted? */
    @SerialName("issuer_wua_signer")
    ISSUER_WUA_SIGNER,

    /** Verifier: is the issuer of a presented credential trusted? */
    @SerialName("verifier_credential_issuer")
    VERIFIER_CREDENTIAL_ISSUER,
}

/** What is being judged. */
sealed interface TrustSubject {

    /** An X.509 chain, leaf first. Each entry is PEM or Base64 DER. */
    data class CertificateChain(val pemOrDer: List<String>) : TrustSubject {
        init {
            require(pemOrDer.isNotEmpty()) { "Certificate chain must not be empty" }
        }
    }
}

/**
 * @property expectedEntityTypes the registry roles that satisfy this request; any one is enough.
 *   These come from the caller's own configuration or profile, never from the counterparty's
 *   message (an offer, a request object, a credential).
 * @property at the reference time for freshness and validity.
 */
data class TrustDecisionRequest(
    val purpose: TrustPurpose,
    val subject: TrustSubject,
    val expectedEntityTypes: Set<TrustedEntityType> = emptySet(),
    val expectedServiceType: String? = null,
    val at: Instant = Clock.System.now(),
)

/**
 * Port that decides whether a subject is trusted for a purpose.
 *
 * Contract for implementations:
 * - Answer [TrustDecisionCode.UNKNOWN] when a remote trust source is unreachable or answers with an
 *   error, and [TrustDecisionCode.PROCESSING_ERROR] when the subject cannot be processed. Other
 *   failures (a local store error, a missing permission) propagate.
 * - Rethrow `CancellationException`.
 * - Report only what the source established. Do not claim freshness or source acceptance the source
 *   cannot vouch for, so that [TrustRequirement.RegistryFresh] can tell registry decisions apart.
 *
 * Callers combine the result with a [TrustRequirement] through [check], which fails closed. A denial
 * from one evaluator must never fall through to another, more permissive one.
 */
fun interface TrustDecisionEvaluator {
    suspend fun evaluate(request: TrustDecisionRequest): TrustDecision
}
