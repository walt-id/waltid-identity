package id.walt.policies.policies

import id.walt.policies.policies.status.StatusPolicyImplementation.verifyWithAttributes
import id.walt.policies.policies.status.content.JsonElementParser
import id.walt.policies.policies.status.model.StatusPolicyArgument
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.serializer
import love.forte.plugin.suspendtrans.annotation.JvmAsync
import love.forte.plugin.suspendtrans.annotation.JvmBlocking


@Serializable
actual class StatusPolicy : StatusPolicyMp() {

    @Transient
    @Contextual
    private val argumentParser = JsonElementParser(serializer<StatusPolicyArgument>())

    @JvmBlocking
    @JvmAsync
    actual override suspend fun verify(data: JsonObject, args: Any?, context: Map<String, Any>): Result<Any> {
        requireNotNull(args) { "args required" }
        require(args is JsonElement) { "args must be JsonElement" }
        val arguments = argumentParser.parse(args)
        return verifyWithAttributes(data, arguments)
    }
}