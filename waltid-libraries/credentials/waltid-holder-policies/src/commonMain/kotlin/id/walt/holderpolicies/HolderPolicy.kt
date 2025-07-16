package id.walt.holderpolicies

import id.walt.holderpolicies.checks.BasicHolderPolicyCheck
import id.walt.holderpolicies.checks.HolderPolicyCheck
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class HolderPolicy(
    //val id: String? = null,
    val priority: Int,
    val description: String? = null,

    val direction: HolderPolicyDirection? = null,

    /**
     * Does this policy apply to the operation?
     *
     * If null: applies to all
     */
    val apply: HolderPolicyCheck? = null,
    val check: HolderPolicyCheck? = null,
    val action: HolderPolicyAction
) {
    @Serializable
    enum class HolderPolicyAction {
        ALLOW,
        BLOCK
    }

    @Serializable
    enum class HolderPolicyDirection {
        RECEIVE,
        PRESENT
    }

    fun serialized() = Json.encodeToString(this)

    companion object {
        val EXAMPLE = HolderPolicy(
            priority = 10,
            description = "Example holder policy",
            direction = HolderPolicyDirection.PRESENT,
            apply = BasicHolderPolicyCheck(issuer = "did:web:issuer.example.org"),
            check = BasicHolderPolicyCheck(claimsPresent = listOf("claim1")),
            action = HolderPolicyAction.BLOCK
        )
    }
}
