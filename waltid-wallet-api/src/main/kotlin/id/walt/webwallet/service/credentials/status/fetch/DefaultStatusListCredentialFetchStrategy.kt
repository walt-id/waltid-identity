package id.walt.webwallet.service.credentials.status.fetch

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

class DefaultStatusListCredentialFetchStrategy(
    private val http: HttpClient,
) : StatusListCredentialFetchStrategy {

    private val json = Json { ignoreUnknownKeys = true }
    override suspend fun fetch(url: String): JsonObject = http.get(url).bodyAsText().let {
        json.decodeFromString(it)
    }

}