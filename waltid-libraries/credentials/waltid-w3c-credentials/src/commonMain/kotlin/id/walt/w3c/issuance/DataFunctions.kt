package id.walt.w3c.issuance

import id.walt.w3c.utils.CredentialDataMergeUtils
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.time.Duration
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalJsExport::class, ExperimentalUuidApi::class)
@JsExport
val dataFunctions = mapOf<String, suspend (call: CredentialDataMergeUtils.FunctionCall) -> JsonElement>(
    "subjectDid" to { it.fromContext() },
    "issuerDid" to { it.fromContext() },

    "context" to { it.context[it.args!!]!! },

    "timestamp-ebsi" to { JsonPrimitive(Clock.System.now().toIso8681WithoutSubSecondPrecision())},
    "timestamp-ebsi-in" to { JsonPrimitive((Clock.System.now() + Duration.parse(it.args!!)).toIso8681WithoutSubSecondPrecision()) },

    "timestamp" to { JsonPrimitive(Clock.System.now().toString()) },

    "timestamp" to { JsonPrimitive(Clock.System.now().toString()) },
    "timestamp-seconds" to { JsonPrimitive(Clock.System.now().epochSeconds) },

    "timestamp-in" to { JsonPrimitive((Clock.System.now() + Duration.parse(it.args!!)).toString()) },
    "timestamp-in-seconds" to { JsonPrimitive((Clock.System.now() + Duration.parse(it.args!!)).epochSeconds) },

    "timestamp-before" to { JsonPrimitive((Clock.System.now() - Duration.parse(it.args!!)).toString()) },
    "timestamp-before-seconds" to { JsonPrimitive((Clock.System.now() - Duration.parse(it.args!!)).epochSeconds) },

    "uuid" to { JsonPrimitive("urn:uuid:${Uuid.random()}") },
    "webhook" to { JsonPrimitive(HttpClient().get(it.args!!).bodyAsText()) },
    "webhook-json" to { Json.parseToJsonElement(HttpClient().get(it.args!!).bodyAsText()) },

    "last" to {
        it.history?.get(it.args!!)
            ?: throw IllegalArgumentException("No such function in history or no history: ${it.args}")
    }
)

private fun Instant.toIso8681WithoutSubSecondPrecision(): String =
    toString().substringBefore(".") + "Z"
