package id.walt.webwallet.manifests

import kotlinx.serialization.json.JsonObject

interface ManifestProvider {
    fun display(): JsonObject
    fun issuer(): JsonObject
}