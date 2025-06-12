package id.walt.policies.policies

import id.walt.policies.policies.status.StatusPolicyImplementation.getStatusEntryElementExtractor
import id.walt.policies.policies.status.StatusPolicyImplementation.processStatusEntry
import id.walt.policies.policies.status.Values
import id.walt.policies.policies.status.model.W3CStatusPolicyAttribute
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import love.forte.plugin.suspendtrans.annotation.JvmAsync
import love.forte.plugin.suspendtrans.annotation.JvmBlocking


@Serializable
actual class RevocationPolicy : RevocationPolicyMp() {

    @JvmBlocking
    @JvmAsync
    actual override suspend fun verify(data: JsonObject, args: Any?, context: Map<String, Any>): Result<Any> {
        val attribute = W3CStatusPolicyAttribute(
            value = 0u,
            purpose = "revocation",
            type = Values.STATUS_LIST_2021
        )
        val statusElement = getStatusEntryElementExtractor(attribute).extract(data)
            ?: return Result.success(JsonObject(mapOf("policy_available" to JsonPrimitive(false))))
        val result = processStatusEntry(statusElement, attribute)
        return result
    }
}