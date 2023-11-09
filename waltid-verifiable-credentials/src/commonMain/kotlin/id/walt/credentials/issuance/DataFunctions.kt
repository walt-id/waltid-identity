package id.walt.credentials.issuance

import id.walt.credentials.utils.W3CDataMergeUtils
import id.walt.did.utils.randomUUID
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Duration

val dataFunctions = mapOf<String, suspend (call: W3CDataMergeUtils.FunctionCall) -> JsonElement>(
    "subjectDid" to { it.fromContext() },
    "issuerDid" to { it.fromContext() },

    "context" to { it.context[it.args!!]!! },

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
