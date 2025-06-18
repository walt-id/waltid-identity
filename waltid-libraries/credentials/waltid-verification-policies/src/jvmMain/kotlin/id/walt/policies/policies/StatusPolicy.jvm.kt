package id.walt.policies.policies

import id.walt.policies.policies.status.StatusPolicyImplementation.verifyWithAttributes
import id.walt.policies.policies.status.model.StatusPolicyArgument
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import love.forte.plugin.suspendtrans.annotation.JvmAsync
import love.forte.plugin.suspendtrans.annotation.JvmBlocking


@Serializable
actual class StatusPolicy : StatusPolicyMp() {

    @JvmBlocking
    @JvmAsync
    actual override suspend fun verify(data: JsonObject, args: Any?, context: Map<String, Any>): Result<Any> {
        requireNotNull(args) { "args required" }
        require(args is StatusPolicyArgument) { "args must be a CredentialStatusPolicyArgument" }
        return verifyWithAttributes(data, args)
    }
}