package id.walt.webwallet.service.credentials.status.fetch

import id.walt.crypto.utils.JsonUtils.toJsonElement
import id.walt.webwallet.service.JwsDecoder
import id.walt.webwallet.service.dids.DidResolverService
import id.walt.webwallet.service.endpoint.ServiceEndpointProvider
import id.walt.webwallet.utils.Base64Utils
import id.walt.webwallet.utils.HttpUtils
import id.walt.webwallet.utils.JsonUtils
import kotlinx.serialization.json.*

class EntraStatusListCredentialFetchStrategy(
    private val serviceEndpointProvider: ServiceEndpointProvider,
    private val didResolverService: DidResolverService,
    private val jwsDecoder: JwsDecoder,
) : StatusListCredentialFetchStrategy {
    private val json = Json { ignoreUnknownKeys = true }
    override suspend fun fetch(url: String): JsonObject = url.substringBefore("?").let { did ->
        didResolverService.resolve(did)?.let {
            val parameters = HttpUtils.parseQueryParam(url.substringAfter("?"))
            val serviceEndpoint = getServiceEndpoint(it, parameters["service"])
            parameters["queries"]?.let {
                parseQueries(it).let {
                    serviceEndpointProvider.get(serviceEndpoint, did, it).let {
                        Json.decodeFromString<JsonElement>(it)
                    }.jsonObject.let {
                        JsonUtils.tryGetData(it, "replies.entries.data")?.let {
                            Base64Utils.decode(it.jsonPrimitive.content).decodeToString().let { jwsDecoder.payload(it) }
                        }
                    }
                } ?: error("Failed to parse service-endpoint reponse: $serviceEndpoint")
            } ?: error("Failed to parse descriptor from 'queries' parameter")
        } ?: error("Failed to resolve did: $did")
    }

    private fun parseQueries(queries: String) =
        Base64Utils.decode(queries).decodeToString().let {
            Json.decodeFromString<JsonElement>(it)
        }.toJsonElement().let {
            when (it) {
                is JsonArray -> it[0].jsonObject
                else -> it.jsonObject
            }
        }

    private fun getServiceEndpoint(did: JsonObject, service: String?) =
        JsonUtils.tryGetData(did, "service")?.jsonArray?.firstOrNull {
            it.jsonObject["type"]?.jsonPrimitive?.content == service
        }?.let {
            JsonUtils.tryGetData(it.jsonObject, "serviceEndpoint.instances")?.let {
                it.jsonArray.firstOrNull()?.jsonPrimitive?.content
            } ?: error("Failed to extract service endpoint from did document")
        } ?: error("Failed to extract service from did document")


}