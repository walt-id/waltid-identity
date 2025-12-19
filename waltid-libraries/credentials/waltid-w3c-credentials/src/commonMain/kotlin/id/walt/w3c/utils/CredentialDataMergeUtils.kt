package id.walt.w3c.utils

import id.walt.crypto.utils.JsonUtils.toJsonObject
import id.walt.w3c.vc.vcs.W3CVC
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.*
import love.forte.plugin.suspendtrans.annotation.JsPromise
import love.forte.plugin.suspendtrans.annotation.JvmAsync
import love.forte.plugin.suspendtrans.annotation.JvmBlocking
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
object CredentialDataMergeUtils {

    private val log = KotlinLogging.logger { }

    fun JsonPrimitive.isTemplate() =
        this.content.let { it.first() == '<' && it.last() == '>' && it.length > 2 && !it.contains(" ") }

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    suspend fun getTemplateData(
        functionCall: String,
        dataFunctions: Map<String, suspend (FunctionCall) -> JsonElement>,
        context: Map<String, JsonElement>,
        functionHistory: MutableMap<String, JsonElement>
    ): JsonElement {
        val cmdLine = functionCall.substring(1, functionCall.length - 1)
        val cmd = cmdLine.substringBefore(":")
        val func = dataFunctions[cmd]
            ?: throw IllegalArgumentException("Unknown dynamic data function \"$cmd\" at call: $functionCall")

        val hasArgs = cmd.length < cmdLine.length

        val result = if (hasArgs) {
            val args = cmdLine.substring(cmd.length + 1)
            func.invoke(FunctionCall(cmd, functionHistory, context, args))
        } else {
            try {
                func.invoke(FunctionCall(cmd, null, context, null))
            } catch (e: NullPointerException) {
                log.error { e }
                throw IllegalArgumentException("Could not execute dynamic data function \"$cmd\" - missing argument! At function call: $cmdLine")
            }
        }
        if (result is JsonPrimitive) {
            functionHistory[cmd] = result
        }

        log.debug { "Called function: $functionCall, got: $result" }
        return result
    }

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    suspend fun MutableMap<String, JsonElement>.patch(
        k: String,
        v: JsonElement,
        dataFunctions: Map<String, suspend (FunctionCall) -> JsonElement>,
        context: Map<String, JsonElement>,
        functionHistory: MutableMap<String, JsonElement>
    ): MutableMap<String, JsonElement> {
        when (v) {
            is JsonPrimitive -> {
                when {
                    v.isTemplate() -> this[k] = getTemplateData(v.content, dataFunctions, context, functionHistory)
                    else -> this[k] = v
                }
            }

            is JsonObject -> {
                v.jsonObject.forEach { (k2, v2) ->
                    if (!this.containsKey(k)) {
                        this[k] = JsonObject(emptyMap())
                    }

                    val kJson = runCatching { this[k]?.jsonObject }.getOrElse { ex ->
                        throw IllegalArgumentException(
                            "Invalid mapping for credential, when processing \"$k\": ${ex.message}",
                            ex
                        )
                    }
                        ?: throw IllegalArgumentException("This key does not exist to map to: $k")

                    this[k] =
                        JsonObject(
                            kJson.toMutableMap().patch(k2, v2, dataFunctions, context, functionHistory)
                        )
                }
            }

            is JsonArray -> {
                if (!this.containsKey(k)) {
                    this[k] = JsonArray(emptyList())
                }

                when {
                    this[k] is JsonArray -> this[k] =
                        JsonArray(this[k]!!.jsonArray.toMutableList().apply { addAll(v.toList()) })

                    else -> this[k] = v
                }
            }
        }

        return this
    }


    data class MergeResult(val vc: W3CVC, val results: Map<String, JsonElement>)
    data class JsonMergeResult(val vc: JsonObject, val results: Map<String, JsonElement>)


    data class FunctionCall(
        val func: String,
        val history: Map<String, JsonElement>?,
        val context: Map<String, JsonElement>,
        val args: String?
    ) {
        fun fromContext(): JsonElement {
            log.debug { "CONTEXT: $context" }
            return context[func] ?: throw IllegalArgumentException("Cannot find in context: $func")
        }
    }

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    suspend fun W3CVC.mergeWithMapping(
        mapping: JsonObject,
        context: Map<String, JsonElement>,
        data: Map<String, suspend (FunctionCall) -> JsonElement>
    ): MergeResult {
        val vcm = this.toMutableMap()

        val results = HashMap<String, JsonElement>()
        val functionHistory = HashMap<String, JsonElement>()

        mapping.forEach { (k, v) ->
            if (!k.startsWith("jwt:")) {
                vcm.patch(k, v, data, context, functionHistory)
            } else {
                results[k] = getTemplateData(v.jsonPrimitive.content, data, context, functionHistory)
            }
        }
        return MergeResult(W3CVC(vcm), results)
    }

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    suspend fun JsonObject.mergeSDJwtVCPayloadWithMapping(
        mapping: JsonObject,
        context: Map<String, JsonElement>,
        data: Map<String, suspend (FunctionCall) -> JsonElement>
    ): JsonObject {
        val vcm = this.toMutableMap()

        val functionHistory = HashMap<String, JsonElement>()

        mapping.forEach { (k, v) ->
            if (!k.startsWith("jwt:")) {
                vcm.patch(k, v, data, context, functionHistory)
            } else {
                vcm[k.removePrefix("jwt:")] = getTemplateData(v.jsonPrimitive.content, data, context, functionHistory)
            }
        }
        return vcm.toJsonObject()
    }
}
