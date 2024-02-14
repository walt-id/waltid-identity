package id.walt.webwallet.service.trust

import id.walt.webwallet.config.TrustConfig
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class DefaultTrustValidationService(
    private val http: HttpClient,
    trustItem: TrustConfig.TrustEntry.TrustItem?,
) : TrustValidationService {
    private val baseUrl = trustItem?.baseUrl ?: ""
    private val trustedRecordPath = trustItem?.trustRecordPath ?: ""
    private val json = Json {
        ignoreUnknownKeys = true
    }

    override suspend fun validate(did: String, type: String): Boolean =
        http.get(String.format("$baseUrl/$trustedRecordPath", did, type)).bodyAsText().let {
            tryParseResponse<SuccessResponse>(it)?.run {
                validate(this)
            } ?: tryParseResponse<FailResponse>(it)?.let { error(it.detail) } ?: error(it)
        }

    private inline fun <reified T> tryParseResponse(response: String): T? = json.decodeFromString<T>(response)

    private fun validate(record: SuccessResponse): Boolean {
        val from = Instant.parse(record.validFromDT)
        val until = Instant.parse(record.validUntilDT)
        val now = Clock.System.now()
        return now > from && now < until && record.status == "current"
    }

    @Serializable
    data class SuccessResponse(
        val identifier: String,
        val entityType: String,
        val credentialType: String,
        val governanceFrameworkURI: String,
        val DIDDocument: String,
        val status: String,
        val statusDetail: String,
        val validFromDT: String,
        val validUntilDT: String,
    )

    @Serializable
    data class FailResponse(
        val type: String,
        val title: String,
        val status: String,
        val detail: String,
        val instance: String,
    )
}