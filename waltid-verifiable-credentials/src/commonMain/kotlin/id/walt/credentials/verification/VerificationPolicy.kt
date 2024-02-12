package id.walt.credentials.verification

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
abstract class VerificationPolicy(
    open val name: String,
    @Transient open val description: String? = null,

    @Transient open val policyType: String? = null,
    @Transient open val argumentTypes: List<VerificationPolicyArgumentType>? = null
) {
    enum class VerificationPolicyArgumentType {
        NONE,
        URL,
        DID,
        DID_ARRAY,
        NUMBER,
        JSON
    }
}
