package id.walt.webwallet.manifest.provider

import id.walt.crypto.utils.JsonUtils.toJsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

interface ManifestProvider {
    fun display(): JsonObject
    fun issuer(): JsonObject

    companion object{
        const val EntraManifestType = "msentra"
        fun new(manifest: String): ManifestProvider =
            when (manifest.toJsonElement().jsonObject["type"]?.jsonPrimitive?.content) {
                EntraManifestType -> EntraManifestProvider(manifest)
                else -> DefaultManifestProvider()
            }
    }
}