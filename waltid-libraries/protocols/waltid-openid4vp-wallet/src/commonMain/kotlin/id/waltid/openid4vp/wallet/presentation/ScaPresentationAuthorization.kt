package id.waltid.openid4vp.wallet.presentation

import id.walt.verifier.openid.models.openid.OpenID4VPResponseMode
import kotlinx.serialization.json.*

/**
 * Authorizes this exact TS12 proof and declares the factors its successful signing will establish.
 *
 * Factors must either have been applied to this operation already, or be guaranteed by the
 * enforced policy of the exact key that will sign it. The latter allows native authentication
 * during signing, without a separate prompt. A merely requested policy or one allowing several
 * alternative authentication routes is insufficient. The presenter releases no proof if signing
 * fails or its coroutine is cancelled; returning methods here alone proves no authentication.
 *
 * This is a trusted application callback, never verifier-supplied metadata. Implementations bind
 * it to their wallet, reviewed action and actual key object; identifiers alone are not authority.
 * They must reject denial, expiry, changed selection and insufficient factor evidence. Action
 * lifetime and delivery remain the caller's responsibility. No authorization is cached here.
 */
fun interface ScaPresentationAuthorizer {
    suspend fun authorize(presentation: ScaPresentation): ScaAuthenticationMethods
}

/** Values are derived by the presenter from the selected credential and resolved request. */
class ScaPresentation internal constructor(
    /** Fresh identifier for this proof attempt; becomes the signed jti claim. */
    val proofId: String,
    val credentialId: String,
    val holderKeyId: String,
    val signingAlgorithm: String,
    val audience: String,
    val nonce: String,
    val responseMode: OpenID4VPResponseMode,
    val sdHash: String,
    /** Exact encoded transaction entries bound into this credential's KB-JWT. */
    val transactionData: List<String>,
)

/** TS12 section 3.6: at least two different categories, with one method per category. */
sealed interface ScaAuthenticationMethods {
    data class KnowledgeAndPossession(val knowledge: Knowledge, val possession: Possession) : ScaAuthenticationMethods
    data class KnowledgeAndInherence(val knowledge: Knowledge, val inherence: Inherence) : ScaAuthenticationMethods
    data class PossessionAndInherence(val possession: Possession, val inherence: Inherence) : ScaAuthenticationMethods
    data class All(val knowledge: Knowledge, val possession: Possession, val inherence: Inherence) : ScaAuthenticationMethods

    enum class Knowledge(val value: String) {
        PIN_LESS_THAN_6_DIGITS("pin_less_than_6_digits"),
        PIN_6_OR_MORE_DIGITS("pin_6_or_more_digits"),
        PASSPHRASE_LESS_THAN_8_CHARS("passphrase_less_than_8_chars"),
        PASSPHRASE_8_TO_11_CHARS("passphrase_8_to_11_chars"),
        PASSPHRASE_12_OR_MORE_CHARS("passphrase_12_or_more_chars"),
        PATTERN("pattern"), OTHER("other"),
    }

    enum class Possession(val value: String) {
        KEY_IN_REMOTE_WSCD("key_in_remote_wscd"),
        KEY_IN_LOCAL_EXTERNAL_WSCD("key_in_local_external_wscd"),
        KEY_IN_LOCAL_INTERNAL_WSCD("key_in_local_internal_wscd"),
        KEY_IN_LOCAL_NATIVE_WSCD("key_in_local_native_wscd"), OTHER("other"),
    }

    enum class Inherence(val value: String) {
        FINGERPRINT_DEVICE("fingerprint_device"), FINGERPRINT_EXTERNAL("fingerprint_external"),
        FACE_DEVICE("face_device"), FACE_EXTERNAL("face_external"), OTHER("other"),
    }
}

internal fun ScaAuthenticationMethods.toJson(): JsonArray {
    val methods = when (this) {
        is ScaAuthenticationMethods.KnowledgeAndPossession -> listOf(
            "knowledge" to knowledge.value, "possession" to possession.value,
        )
        is ScaAuthenticationMethods.KnowledgeAndInherence -> listOf(
            "knowledge" to knowledge.value, "inherence" to inherence.value,
        )
        is ScaAuthenticationMethods.PossessionAndInherence -> listOf(
            "possession" to possession.value, "inherence" to inherence.value,
        )
        is ScaAuthenticationMethods.All -> listOf(
            "knowledge" to knowledge.value, "possession" to possession.value, "inherence" to inherence.value,
        )
    }
    return JsonArray(methods.map { (category, method) -> buildJsonObject { put(category, method) } })
}

internal data class ScaKeyBindingContext(
    val credentialId: String,
    val responseMode: OpenID4VPResponseMode,
    val authorizer: ScaPresentationAuthorizer,
)

internal const val TS12_PAYMENT_TYPE = "urn:eudi:sca:payment:1"
