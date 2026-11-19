package id.walt.itb

import id.walt.policies2.vc.policies.status.model.IETFEntry
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

internal fun checkStatusListReferences(claims: List<JsonElement?>) {
    require(claims.size in 1..2)
    val references = claims.map { claim ->
        Json.decodeFromJsonElement(IETFEntry.serializer(), claim ?: throw ItbWalletRejection("status_missing_list_reference"))
    }
    if (references.size == 2) {
        val (first, second) = references
        if (first.uri != second.uri || first.index == second.index) {
            throw ItbWalletRejection("status_expected_shared_list_with_distinct_indices")
        }
    }
}
