package id.walt.webwallet.manifest.extractor

import kotlinx.serialization.json.JsonObject

class DefaultManifestExtractor : ManifestExtractor {
    override suspend fun extract(offerRequestUrl: String): JsonObject = JsonObject(emptyMap())


    private fun createManifestObject(offerRequestUrl: String): JsonObject = JsonObject(emptyMap())
}