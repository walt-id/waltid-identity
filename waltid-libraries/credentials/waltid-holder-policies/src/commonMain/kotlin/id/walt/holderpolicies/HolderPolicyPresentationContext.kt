package id.walt.holderpolicies

import kotlinx.serialization.json.JsonObject

/**
 * Evidence from the selected presentation. Null denotes unavailable evidence, while an empty list
 * denotes confirmed absence. The engine calls [disclosures] only when a check needs it.
 */
data class HolderPolicyPresentationContext(
    val disclosures: (suspend () -> List<HolderPolicyDisclosedCredential>?)? = null,
    val transactionData: List<HolderPolicyTransactionData>? = null,
)

/** The projected claims for one selected credential under its presentation query ID. */
data class HolderPolicyDisclosedCredential(
    val credentialId: String,
    val claims: JsonObject?,
)

/** The validated transaction type, selected query bindings, and type-specific fields for one item. */
data class HolderPolicyTransactionData(
    val type: String,
    val credentialIds: Set<String>,
    val data: JsonObject,
)
