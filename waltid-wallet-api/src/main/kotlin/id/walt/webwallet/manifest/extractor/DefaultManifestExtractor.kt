package id.walt.webwallet.manifest.extractor

import id.walt.crypto.utils.JsonUtils.toJsonElement
import id.walt.webwallet.manifest.provider.ManifestProvider
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

class DefaultManifestExtractor() : ManifestExtractor {
    override suspend fun extract(offerRequestUrl: String): JsonObject =
        JsonObject(createManifestObject(offerRequestUrl).plus(mapOf("type" to "oidc".toJsonElement())))


    private fun createManifestObject(offerRequestUrl: String): JsonObject =
        ManifestProvider.json.decodeFromString<JsonObject>(offerRequestUrl).let {
            JsonObject(
                mapOf(
                    "issuer" to it.jsonObject["credential_issuer"]!!,
                    "credentials" to it.jsonObject["credentials"]!!.jsonArray
                )
            )
        }
}