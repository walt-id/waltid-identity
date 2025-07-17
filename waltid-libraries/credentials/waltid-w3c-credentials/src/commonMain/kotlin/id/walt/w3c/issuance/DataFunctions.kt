package id.walt.w3c.issuance

import id.walt.crypto.utils.UuidUtils.randomUUID
import id.walt.w3c.utils.CredentialDataMergeUtils
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.*
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.time.Duration
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalJsExport::class, ExperimentalUuidApi::class)
@JsExport
val dataFunctions = mapOf<String, suspend (call: CredentialDataMergeUtils.FunctionCall) -> JsonElement>(
    "subjectDid" to { it.fromContext() },
    "issuerDid" to { it.fromContext() },
    "context" to { it.context[it.args!!]!! },
    "display" to {
        val context = it.context
        val displayList =
            context["display"]?.jsonArray ?: throw IllegalArgumentException("No display available for this credential")
        val displayJsonArray = JsonArray(
            displayList.map { entry ->
                val display = entry.jsonObject
                JsonObject(
                    buildMap {
                        put("name", display["name"]!!)
                        display["description"]?.let { put("description", it) }
                        display["locale"]?.let { put("locale", it) }
                        display["logo"]?.jsonObject?.let { logo ->
                            put(
                                "logo", JsonObject(
                                    mapOf(
                                        "url" to logo["url"]!!,
                                        "altText" to logo["alt_text"]!!,
                                    )
                                )
                            )
                        }
                        display["background_color"]?.let { put("backgroundColor", it) }
                        display["text_color"]?.let { put("textColor", it) }
                        display["background_image"]?.jsonObject?.let { bgImage ->
                            put(
                                "backgroundImage", JsonObject(
                                    mapOf(
                                        "url" to bgImage["url"]!!,
                                        "altText" to bgImage["alt_text"]!!,
                                    )
                                )
                            )
                        }
                        display["customParameters"]?.jsonObject?.get("secondary_image")?.jsonObject?.let { secImage ->
                            put(
                                "secondaryImage", JsonObject(
                                    mapOf(
                                        "url" to secImage["url"]!!,
                                        "altText" to secImage["alt_text"]!!,
                                    )
                                )
                            )
                        }
                    }
                )
            }
        )
        displayJsonArray
    },
    "timestamp-ebsi" to { JsonPrimitive(Clock.System.now().toIso8681WithoutSubSecondPrecision()) },
    "timestamp-ebsi-in" to { JsonPrimitive((Clock.System.now() + Duration.parse(it.args!!)).toIso8681WithoutSubSecondPrecision()) },

    "timestamp" to { JsonPrimitive(Clock.System.now().toString()) },

    "timestamp" to { JsonPrimitive(Clock.System.now().toString()) },
    "timestamp-seconds" to { JsonPrimitive(Clock.System.now().epochSeconds) },

    "timestamp-in" to { JsonPrimitive((Clock.System.now() + Duration.parse(it.args!!)).toString()) },
    "timestamp-in-seconds" to { JsonPrimitive((Clock.System.now() + Duration.parse(it.args!!)).epochSeconds) },

    "timestamp-before" to { JsonPrimitive((Clock.System.now() - Duration.parse(it.args!!)).toString()) },
    "timestamp-before-seconds" to { JsonPrimitive((Clock.System.now() - Duration.parse(it.args!!)).epochSeconds) },

    "uuid" to { JsonPrimitive("urn:uuid:${randomUUID()}") },
    "webhook" to { JsonPrimitive(HttpClient().get(it.args!!).bodyAsText()) },
    "webhook-json" to { Json.parseToJsonElement(HttpClient().get(it.args!!).bodyAsText()) },

    "last" to {
        it.history?.get(it.args!!)
            ?: throw IllegalArgumentException("No such function in history or no history: ${it.args}")
    }
)

private fun Instant.toIso8681WithoutSubSecondPrecision(): String =
    toString().substringBefore(".") + "Z"
