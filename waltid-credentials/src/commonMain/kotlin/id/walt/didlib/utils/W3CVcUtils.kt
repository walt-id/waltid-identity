package id.walt.didlib.utils

import id.walt.didlib.vc.list.W3CVC
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.datetime.Clock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

object W3CVcUtils {

    fun W3CVC.overwrite(map: Map<String, JsonElement>): W3CVC = W3CVC(
        this.toJsonObject().toMutableMap().apply {
            map.forEach { (k, v) ->
                this[k] = v
            }
        })

    fun W3CVC.update(key: String, map: Map<String, JsonElement>): W3CVC {
        return W3CVC(this.toMutableMap().apply {
            this[key] = JsonObject(this[key]!!.jsonObject.toMutableMap().apply {
                map.forEach { (k, v) ->
                    this[k] = v
                }
            })
        })
    }

}

fun JsonPrimitive.isTemplate() =
    this.content.let { it.first() == '<' && it.last() == '>' && it.length > 2 && !it.contains(" ") }

suspend fun MutableMap<String, JsonElement>.patch(
    k: String,
    v: JsonElement,
    data: Map<String, suspend (String?) -> JsonElement>
): MutableMap<String, JsonElement> {
    //println("PATCH: $k -> $v (we are: $this)")

    suspend fun getTemplateData(k: String): JsonElement {
        val cmdLine = k.substring(1, k.length - 1)
        val cmd = cmdLine.substringBefore(":")
        val func = data[cmd] ?: throw IllegalArgumentException("Unknown template variable: $k")

        val hasArgs = cmd.length < cmdLine.length

        return if (hasArgs) {
            val args = cmdLine.substring(cmd.length + 1)
            func.invoke(args)
        } else {
            func.invoke(null)
        }
    }

    when (v) {
        is JsonPrimitive -> {
            when {
                v.isTemplate() -> this[k] = getTemplateData(v.content)
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

                this[k] = JsonObject(this[k]!!.jsonObject.toMutableMap().patch(k2, v2, data))
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

suspend fun mergeIntoVc(
    vc: W3CVC, mapping: JsonObject,/*, data: Map<String, String>*/
    data: Map<String, suspend (String?) -> JsonElement>
): W3CVC {
    val vcm = vc.toMutableMap()
    mapping.forEach { (k, v) ->
        vcm.patch(k, v, data)
    }
    return W3CVC(vcm)
}

operator fun String.times(n: Int): String {
    val sb = StringBuilder()
    repeat(n * 1) {
        sb.append(this)
    }
    return sb.toString()
}

private val prettyJson = Json { prettyPrint = true }

suspend fun main() {
    //language=JSON
    val mappingStr = """
        {
          "id": "<uuid>",
          "credentialSubject": {"id": "<subjectDid>"},
          "issuedAt": "<timestamp>",
          "data": {"mydata": "<webhook-json:https://random-data-api.com/api/v2/users>"},
          "data-in-string": "<webhook:https://cdn.jsdelivr.net/gh/fawazahmed0/currency-api@1/latest/currencies/eur/jpy.json>"
        }
    """.trimIndent()

    //language=JSON
    val vcStr = """
        {
            "@context": ["ctx"],
            "type": ["type"],
            "credentialSubject": {"name": "Muster", "image": {"url": "URL"}}
        }
    """.trimIndent()

    val vc = W3CVC.fromJson(vcStr)
    println("--- VC: \n${vc.toPrettyJson()}\n")

    val mapping = Json.parseToJsonElement(mappingStr).jsonObject
    println("--- Mapping: \n${prettyJson.encodeToString(mapping)}\n")

    val data = mapOf<String, suspend (String?) -> JsonElement>(
        "subjectDid" to { JsonPrimitive("xxSubjectDidxx") },
        "timestamp" to { JsonPrimitive(Clock.System.now().toString()) },
        "uuid" to { JsonPrimitive("urn:uuid:${randomUUID()}") },
        "webhook" to { JsonPrimitive(HttpClient().get(it!!).bodyAsText()) },
        "webhook-json" to { Json.parseToJsonElement(HttpClient().get(it!!).bodyAsText()) }
    )
    //println("--- Data: \n${data.keys.joinToString()}\n")

    println("--- Merged & evaluated:")
    println(mergeIntoVc(vc, mapping, data).toPrettyJson())
}
