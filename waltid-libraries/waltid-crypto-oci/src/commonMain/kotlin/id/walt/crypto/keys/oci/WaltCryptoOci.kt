package id.walt.crypto.keys.oci

import id.walt.crypto.keys.KeyGenerationRequest
import id.walt.crypto.keys.KeyManager
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement

object WaltCryptoOci {
    fun init() {
        KeyManager.register<OCIKey>("oci") { generateRequest: KeyGenerationRequest ->
            OCIKey.generateKey(
                Json.decodeFromJsonElement(generateRequest.config!!)
            )
        }
    }
}
