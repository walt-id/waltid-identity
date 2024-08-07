package id.walt.credentials.verification.models

import id.walt.credentials.verification.SerializableRuntimeException
import id.walt.crypto.utils.JsonUtils.toJsonElement
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport


@Serializable
data class PolicyResultSurrogate(
    val policy: String,
    val description: String? = null,
    val args: JsonElement? = null,
    @SerialName("is_success")
    val isSuccess: Boolean,

    val result: JsonElement? = null,
    val error: JsonElement? = null,
) {
    init {
        check(result != null || error != null) { "Either result or error has to exist in PolicyResult" }
    }

    constructor(value: PolicyResult) : this(
        policy = value.request.policy.name,
        description = value.request.policy.description,

        args = value.request.args,
        isSuccess = value.result.isSuccess,

        result = if (value.result.isSuccess) value.result.getOrThrow().toJsonElement() else null,
        error = if (value.result.isFailure) value.result.exceptionOrNull().let { e ->
            when (e!!) {
                is SerializableRuntimeException -> Json.encodeToJsonElement(e)
                else -> JsonPrimitive(e.stackTraceToString())
            }
        } else null
    )
}

object PolicyResultSerializer : KSerializer<PolicyResult> {
    override val descriptor: SerialDescriptor = PolicyResultSurrogate.serializer().descriptor

    override fun serialize(encoder: Encoder, value: PolicyResult) {
        val surrogate = PolicyResultSurrogate(value)
        encoder.encodeSerializableValue(PolicyResultSurrogate.serializer(), surrogate)
    }

    override fun deserialize(decoder: Decoder): PolicyResult {
//        val surrogate = decoder.decodeSerializableValue(PolicyResultSurrogate.serializer())
        throw UnsupportedOperationException("only serialization is supported for PolicyResult")
    }
}

@Serializable(with = PolicyResultSerializer::class)
@OptIn(ExperimentalJsExport::class)
@JsExport
data class PolicyResult(val request: PolicyRequest, val result: Result<Any?>) {

    fun isSuccess() = result.isSuccess
}
