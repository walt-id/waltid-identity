package id.walt.webwallet.manifest.extractor

import id.walt.crypto.utils.JsonUtils.toJsonElement
import kotlinx.serialization.json.JsonObject

class DefaultManifestExtractor : ManifestExtractor {
    override suspend fun extract(offerRequestUrl: String): JsonObject =
        JsonObject(mapOf("type" to "oidc".toJsonElement()))
}