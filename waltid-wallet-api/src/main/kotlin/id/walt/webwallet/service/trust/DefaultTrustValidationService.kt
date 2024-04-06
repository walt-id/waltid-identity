package id.walt.webwallet.service.trust

import id.walt.webwallet.config.TrustConfig
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class DefaultTrustValidationService(
    private val http: HttpClient,
    trustRecord: TrustConfig.TrustRecord?,
) : TrustValidationService {
    private val baseUrl = trustRecord?.baseUrl ?: ""
    private val trustedRecordPath = trustRecord?.trustRecordPath ?: ""
    private val json = Json {
        ignoreUnknownKeys = true
    }

    override suspend fun validate(did: String, type: String, egfUri: String): Boolean = runCatching {
        http.get(String.format("$baseUrl/$trustedRecordPath", did, type, egfUri)).bodyAsText().let {
            tryParseResponse<JsonObject>(it)?.run {
                validate(this)
            } ?: tryParseResponse<JsonObject>(it)?.let {
                error(it.jsonObject["detail"]?.jsonPrimitive?.content ?: "")
            } ?: error(it)
        }
    }.fold(onSuccess = {
        it
    }, onFailure = { false })

    private inline fun <reified T> tryParseResponse(response: String): T? = json.decodeFromString<T>(response)

    private fun validate(record: JsonObject): Boolean {
        val from = record.jsonObject["validFromDT"]?.jsonPrimitive?.content?.let { Instant.parse(it) } ?: Instant.DISTANT_FUTURE
        val until = record.jsonObject["validUntilDT"]?.jsonPrimitive?.content?.let { Instant.parse(it) } ?: Instant.DISTANT_PAST
        val status = record.jsonObject["status"]?.jsonPrimitive?.content
        val now = Clock.System.now()
        return now in from..until && "current" == status
    }
}
