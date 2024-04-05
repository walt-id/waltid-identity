package id.walt.webwallet.service.issuers

import id.walt.webwallet.config.TrustConfig
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

class DefaultIssuerNameResolutionService(
    private val http: HttpClient,
    trustRecord: TrustConfig.TrustRecord?,
) : IssuerNameResolutionService {
    private val baseUrl = trustRecord?.baseUrl ?: ""
    private val governanceRecordPath = trustRecord?.governanceRecordPath ?: ""
    private val json = Json {
        ignoreUnknownKeys = true
    }

    override suspend fun resolve(did: String): Result<String> = runCatching {
        http.get(String.format("$baseUrl/$governanceRecordPath", did)).bodyAsText().let {
            json.decodeFromString<JsonObject>(it).let {
                it["name"]?.jsonPrimitive?.content
            }
        } ?: error("Error resolving issuer name for: $did")
    }
}