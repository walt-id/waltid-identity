package id.walt.webwallet.manifest.provider

import id.walt.crypto.utils.JsonUtils.toJsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

class EntraManifestProvider(private val manifest: String) : ManifestProvider {

    override fun display(): JsonObject =
        manifest.toJsonElement().jsonObject["display"]?.jsonObject?.get("card")?.jsonObject ?: JsonObject(emptyMap())

    override fun issuer(): JsonObject =
        manifest.toJsonElement().jsonObject["input"]?.jsonObject ?: JsonObject(emptyMap())
}