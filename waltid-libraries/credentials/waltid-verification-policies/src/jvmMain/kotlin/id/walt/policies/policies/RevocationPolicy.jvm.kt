package id.walt.policies.policies

import id.walt.policies.policies.status.StatusPolicyImplementation.verifyWithAttributes
import id.walt.policies.policies.status.Values
import id.walt.policies.policies.status.model.W3CStatusPolicyAttribute
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import love.forte.plugin.suspendtrans.annotation.JvmAsync
import love.forte.plugin.suspendtrans.annotation.JvmBlocking


@Serializable
actual class RevocationPolicy : RevocationPolicyMp() {

    @Transient
    private val attribute = W3CStatusPolicyAttribute(value = 0u, purpose = "revocation", type = Values.STATUS_LIST_2021)

    @JvmBlocking
    @JvmAsync
    actual override suspend fun verify(data: JsonObject, args: Any?, context: Map<String, Any>): Result<Any> =
        verifyWithAttributes(data, attribute)
}