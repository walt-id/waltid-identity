package id.walt.credentials.verification.models

import id.walt.credentials.verification.SerializableRuntimeException
import id.walt.crypto.utils.JsonUtils.toJsonElement
import kotlinx.serialization.json.*
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
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
