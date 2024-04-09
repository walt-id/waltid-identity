package id.walt.webwallet.service.entity

import id.walt.webwallet.config.TrustConfig
import id.walt.webwallet.utils.JsonUtils
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

class DefaultNameResolutionService(
    private val http: HttpClient,
    trustRecord: TrustConfig.TrustRecord?,
) : EntityNameResolutionService {
    private val baseUrl = trustRecord?.baseUrl ?: ""
    private val governanceRecordPath = trustRecord?.governanceRecordPath ?: ""
    private val json = Json {
        ignoreUnknownKeys = true
    }

    override suspend fun resolve(did: String): Result<String> = runCatching {
        http.get(String.format("$baseUrl/$governanceRecordPath", did)).bodyAsText().let {
            json.decodeFromString<JsonObject>(it).let {
                JsonUtils.tryGetData(it, "name")?.jsonPrimitive?.content
            }
        } ?: error("Error resolving entity name for: $did")
    }
}