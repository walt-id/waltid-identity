package id.walt.webwallet.manifests

import kotlinx.serialization.json.JsonObject

class DefaultManifestExtractor : ManifestExtractor {
    override suspend fun extract(offerRequestUrl: String): JsonObject = JsonObject(emptyMap())
}