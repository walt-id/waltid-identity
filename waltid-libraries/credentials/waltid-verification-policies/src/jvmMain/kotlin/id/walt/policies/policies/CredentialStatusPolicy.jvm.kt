package id.walt.policies.policies

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import love.forte.plugin.suspendtrans.annotation.JvmAsync
import love.forte.plugin.suspendtrans.annotation.JvmBlocking


@Serializable
actual class CredentialStatusPolicy : CredentialStatusPolicyMp() {

    @JvmBlocking
    @JvmAsync
    actual override suspend fun verify(data: JsonObject, args: Any?, context: Map<String, Any>): Result<Any> {
        TODO("Not yet implemented")
    }
}