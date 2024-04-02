package id.walt.webwallet.service.credentials.status.fetch

import id.walt.crypto.utils.JsonUtils.toJsonElement
import id.walt.crypto.utils.JwsUtils.decodeJws
import id.walt.did.dids.DidService
import id.walt.webwallet.utils.Base64Utils
import id.walt.webwallet.utils.HttpUtils
import id.walt.webwallet.utils.JsonUtils
import io.ktor.client.*
import io.ktor.client.request.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.uuid.UUID

class EntraStatusListCredentialFetchStrategy(
    private val http: HttpClient,
) : StatusListCredentialFetchStrategy {
    override suspend fun fetch(url: String): JsonObject = url.substringBefore("?").let { did ->
        DidService.resolve(did).getOrNull()?.let {
            val parameters = HttpUtils.parseQueryParam(url.substringAfter("?"))
            val serviceEndpoint = getServiceEndpoint(it, parameters["service"])
            parameters["queries"]?.toJsonElement()?.jsonObject?.let {
                getServiceEndpointResponse(serviceEndpoint, did, it).toJsonElement().jsonObject.let {
                    JsonUtils.tryGetData(it, "replies.entries.data")?.let {
                        Base64Utils.decode(it.jsonPrimitive.content).decodeToString()
                            .decodeJws(allowMissingSignature = true).payload
                    } ?: error("Failed to parse service-endpoint reponsed: $serviceEndpoint")
                }
            } ?: error("Failed to parse descriptor from 'queries' parameter")
        } ?: error("Failed to resolve did: $did")
    }

    private fun getServiceEndpoint(did: JsonObject, service: String?) =
        JsonUtils.tryGetData(did, "service")?.jsonArray?.firstOrNull {
            it.jsonObject["type"]?.jsonPrimitive?.content == service
        }?.let {
            JsonUtils.tryGetData(it.jsonObject, "serviceEndpoint.instances")?.let {
                it.jsonArray.firstOrNull()?.jsonPrimitive?.content
            } ?: error("Failed to extract service endpoint from did document")
        } ?: error("Failed to extract service from did document")

    private suspend fun getServiceEndpointResponse(url: String, did: String, descriptor: JsonObject) = http.post(url) {
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
    }

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