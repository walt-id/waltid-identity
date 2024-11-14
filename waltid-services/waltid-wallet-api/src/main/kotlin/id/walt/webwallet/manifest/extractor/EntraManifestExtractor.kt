package id.walt.webwallet.manifest.extractor

import id.walt.crypto.utils.JsonUtils.toJsonElement
import id.walt.oid4vc.requests.AuthorizationRequest
import id.walt.oid4vc.requests.EntraIssuanceRequest
import id.walt.webwallet.manifest.provider.ManifestProvider
import io.ktor.http.*
import io.ktor.util.*
import kotlinx.serialization.json.JsonObject

class EntraManifestExtractor : ManifestExtractor {

    override suspend fun extract(offerRequestUrl: String): JsonObject =
        JsonObject(getManifest(offerRequestUrl).plus(mapOf("type" to ManifestProvider.EntraManifestType.toJsonElement())))

    private suspend fun getManifest(offerRequestUrl: String) = runCatching {
        parseQueryString(Url(offerRequestUrl).encodedQuery).toMap().let {
            AuthorizationRequest.fromHttpParametersAuto(it)
        }.let {
            EntraIssuanceRequest.fromAuthorizationRequest(it)
        }
    }.fold(onSuccess = { it.manifest }, onFailure = { JsonObject(emptyMap()) })
}