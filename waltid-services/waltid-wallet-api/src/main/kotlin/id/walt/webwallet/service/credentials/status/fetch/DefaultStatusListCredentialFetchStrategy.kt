package id.walt.webwallet.service.credentials.status.fetch

import id.walt.webwallet.service.JwsDecoder
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.serialization.json.JsonObject

class DefaultStatusListCredentialFetchStrategy(
    private val http: HttpClient,
    private val jwsDecoder: JwsDecoder,
) : StatusListCredentialFetchStrategy {

    override suspend fun fetch(url: String): JsonObject = http.get(url).bodyAsText().let {
        jwsDecoder.payload(it)
    }
}