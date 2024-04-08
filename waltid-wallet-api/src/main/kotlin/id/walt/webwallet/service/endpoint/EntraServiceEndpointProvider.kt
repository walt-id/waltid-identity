package id.walt.webwallet.service.endpoint

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.uuid.UUID

class EntraServiceEndpointProvider(
    private val http: HttpClient,
) : ServiceEndpointProvider {
    override suspend fun get(url: String, did: String, descriptor: JsonObject): String = http.post(url) {
        setBody(
            IdentityHubRequest(
                requestId = UUID().toString(),
                target = did,
                messages = listOf(
                    IdentityHubRequest.Message(
                        descriptor = descriptor,
                    )
                )
            )
        )
    }.bodyAsText()

    @Serializable
    private data class IdentityHubRequest(
        val requestId: String,
        val target: String,
        val messages: List<Message>,
    ) {
        @Serializable
        data class Message(
            val descriptor: JsonObject,
        )
    }
}