package id.walt.webwallet.service.endpoint

import kotlinx.serialization.json.JsonObject

interface ServiceEndpointProvider {
    suspend fun get(url: String, did: String, descriptor: JsonObject): String
}