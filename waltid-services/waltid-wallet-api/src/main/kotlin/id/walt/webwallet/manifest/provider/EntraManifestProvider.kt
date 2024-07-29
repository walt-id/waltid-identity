package id.walt.webwallet.manifest.provider

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

class EntraManifestProvider(private val manifest: String) : ManifestProvider {

    override fun display(): JsonObject =
        ManifestProvider.json.decodeFromString<JsonObject>(manifest).jsonObject["display"]?.jsonObject?.get("card")?.jsonObject
            ?: JsonObject(emptyMap())

    override fun issuer(): JsonObject =
        ManifestProvider.json.decodeFromString<JsonObject>(manifest).jsonObject["input"]?.jsonObject ?: JsonObject(
            emptyMap()
        )
}