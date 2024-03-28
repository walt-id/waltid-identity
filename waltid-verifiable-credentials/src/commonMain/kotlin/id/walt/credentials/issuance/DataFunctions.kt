package id.walt.credentials.issuance

import id.walt.credentials.utils.W3CDataMergeUtils
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.uuid.UUID
import kotlinx.uuid.generateUUID
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.time.Duration

@OptIn(ExperimentalJsExport::class)
@JsExport
val dataFunctions = mapOf<String, suspend (call: W3CDataMergeUtils.FunctionCall) -> JsonElement>(
    "subjectDid" to { it.fromContext() },
    "issuerDid" to { it.fromContext() },

    "context" to { it.context[it.args!!]!! },

    // Add this because clock.now returns millis and the timestamps for exp etc claims must be identical
    "timestamp-ebsi" to { JsonPrimitive(Clock.System.now().toString().replaceRange(19..28, ""))},
    "timestamp-ebsi-in" to { JsonPrimitive((Clock.System.now() + Duration.parse(it.args!!)).toString().replaceRange(19..28, "")) },

    "timestamp" to { JsonPrimitive(Clock.System.now().toString()) },

    "timestamp" to { JsonPrimitive(Clock.System.now().toString()) },
    "timestamp-seconds" to { JsonPrimitive(Clock.System.now().epochSeconds) },

    "timestamp-in" to { JsonPrimitive((Clock.System.now() + Duration.parse(it.args!!)).toString()) },
    "timestamp-in-seconds" to { JsonPrimitive((Clock.System.now() + Duration.parse(it.args!!)).epochSeconds) },

    "timestamp-before" to { JsonPrimitive((Clock.System.now() - Duration.parse(it.args!!)).toString()) },
    "timestamp-before-seconds" to { JsonPrimitive((Clock.System.now() - Duration.parse(it.args!!)).epochSeconds) },

    "uuid" to { JsonPrimitive("urn:uuid:${UUID.generateUUID()}") },
    "webhook" to { JsonPrimitive(HttpClient().get(it.args!!).bodyAsText()) },
    "webhook-json" to { Json.parseToJsonElement(HttpClient().get(it.args!!).bodyAsText()) },

    "last" to {
        it.history?.get(it.args!!)
            ?: throw IllegalArgumentException("No such function in history or no history: ${it.args}")
    }
)
