package id.walt.webwallet.manifest.provider

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

interface ManifestProvider {
    fun display(): JsonObject
    fun issuer(): JsonObject

    companion object {
        const val EntraManifestType = "msentra"
        val json = Json { ignoreUnknownKeys }
        fun new(manifest: String): ManifestProvider =
            when (json.decodeFromString<JsonObject>(manifest).jsonObject["type"]?.jsonPrimitive?.content) {
                EntraManifestType -> EntraManifestProvider(manifest)
                else -> DefaultManifestProvider()
            }
    }
}
