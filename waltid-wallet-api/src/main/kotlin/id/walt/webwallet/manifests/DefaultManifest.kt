package id.walt.webwallet.manifests

import kotlinx.serialization.json.JsonObject

class DefaultManifest : Manifest {
    override suspend fun display(): JsonObject? = null

    override suspend fun issuer(): JsonObject? = null
}