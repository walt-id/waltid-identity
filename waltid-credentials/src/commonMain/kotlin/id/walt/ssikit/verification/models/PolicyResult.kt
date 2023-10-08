package id.walt.ssikit.verification.models

import id.walt.ssikit.utils.JsonUtils.toJsonElement
import id.walt.ssikit.verification.SerializableRuntimeException
import kotlinx.serialization.json.*

data class PolicyResult(val request: PolicyRequest, val result: Result<Any?>) {

    fun isSuccess() = result.isSuccess

    fun toJsonResult() =
        buildJsonObject {
            put("policy", request.policy.name)
            put("description", request.policy.description)

            request.args?.let { put("args", request.args) }

            put("is_success", result.isSuccess)

            result.fold(
                onSuccess = {
                    if (it != Unit && it != null) {
                        put("result", it.toJsonElement())
                    }
                }, onFailure = { e ->
                    val error = when (e) {
                        is SerializableRuntimeException -> Json.encodeToJsonElement(e)
                        else -> JsonPrimitive(e.stackTraceToString())
                    }
                    put("error", error)
                }
            )
        }
}
