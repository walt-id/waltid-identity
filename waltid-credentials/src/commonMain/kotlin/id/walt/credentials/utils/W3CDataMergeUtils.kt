package id.walt.credentials.utils

import id.walt.credentials.vc.vcs.W3CVC
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.*
import kotlin.collections.HashMap
import kotlin.collections.Map
import kotlin.collections.MutableMap
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.emptyList
import kotlin.collections.emptyMap
import kotlin.collections.forEach
import kotlin.collections.set
import kotlin.collections.toList
import kotlin.collections.toMutableList
import kotlin.collections.toMutableMap

object W3CDataMergeUtils {

    val log = KotlinLogging.logger {  }

    fun JsonPrimitive.isTemplate() =
        this.content.let { it.first() == '<' && it.last() == '>' && it.length > 2 && !it.contains(" ") }

    suspend fun getTemplateData(
        functionCall: String,
        dataFunctions: Map<String, suspend (FunctionCall?) -> JsonElement>,
        functionHistory: MutableMap<String, JsonElement>
    ): JsonElement {
        val cmdLine = functionCall.substring(1, functionCall.length - 1)
        val cmd = cmdLine.substringBefore(":")
        val func = dataFunctions[cmd]
            ?: throw IllegalArgumentException("Unknown dynamic data function \"$cmd\" at call: $functionCall")

        val hasArgs = cmd.length < cmdLine.length

        val result = if (hasArgs) {
            val args = cmdLine.substring(cmd.length + 1)
            func.invoke(FunctionCall(functionHistory, args))
        } else {
            try {
                func.invoke(null)
            } catch (e: NullPointerException) {
                throw IllegalArgumentException("Could not execute dynamic data function \"$cmd\": Missing argument!")
            }
        }
        if (result is JsonPrimitive) {
            functionHistory[cmd] = result
        }

        log.debug { "Called function: $functionCall, got: $result" }
        return result
    }

    suspend fun MutableMap<String, JsonElement>.patch(
        k: String,
        v: JsonElement,
        dataFunctions: Map<String, suspend (FunctionCall?) -> JsonElement>,
        functionHistory: MutableMap<String, JsonElement>
    ): MutableMap<String, JsonElement> {
        when (v) {
            is JsonPrimitive -> {
                when {
                    v.isTemplate() -> this[k] = getTemplateData(v.content, dataFunctions, functionHistory)
                    else -> this[k] = v
                }
            }

            is JsonObject -> {
                v.jsonObject.forEach { (k2, v2) ->
                    if (!this.containsKey(k)) {
                        //println("Creating for $k: (to do: $v)")
                        this[k] = JsonObject(emptyMap())
                        //println("We now have: $this")
                    }

                    //println("Sub-patching for $k: (current is: ${this[k]})")

                    this[k] =
                        JsonObject(this[k]!!.jsonObject.toMutableMap().patch(k2, v2, dataFunctions, functionHistory))
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

            else -> {
                println("Unsupported: $v")
            }
        }

        return this
    }


    data class MergeResult(val vc: W3CVC, val results: Map<String, JsonElement>)


    data class FunctionCall(val history: Map<String, JsonElement>, val args: String?)

    suspend fun W3CVC.mergeWithMapping(
        mapping: JsonObject,
        data: Map<String, suspend (FunctionCall?) -> JsonElement>
    ): MergeResult {
        val vcm = this.toMutableMap()

        val results = HashMap<String, JsonElement>()
        val functionHistory = HashMap<String, JsonElement>()

        mapping.forEach { (k, v) ->
            if (!k.startsWith("jwt:")) {
                vcm.patch(k, v, data, functionHistory)
            } else {
                results[k] = getTemplateData(v.jsonPrimitive.content, data, functionHistory)
            }
        }
        return MergeResult(W3CVC(vcm), results)
    }
}
