package id.walt.entrawallet.core.manifest.provider

import kotlinx.serialization.json.JsonObject

class DefaultManifestProvider : ManifestProvider {
    override fun display(): JsonObject = JsonObject(emptyMap())

    override fun issuer(): JsonObject = JsonObject(emptyMap())
}
