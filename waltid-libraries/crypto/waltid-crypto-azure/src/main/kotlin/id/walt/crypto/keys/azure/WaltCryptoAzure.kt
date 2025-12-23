package id.walt.crypto.keys.azure

import id.walt.crypto.keys.KeyGenerationRequest
import id.walt.crypto.keys.KeyManager
import id.walt.crypto.keys.KeySerialization
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement


object WaltCryptoAzure {
    fun init() {
        KeyManager.register<AzureKey>("azure") { generateRequest: KeyGenerationRequest ->
            AzureKey.generateKey(
                generateRequest.keyType,
                Json.decodeFromJsonElement(generateRequest.config!!)
            )
        }
        KeySerialization.registerExternalKeyType(AzureKey::class)
    }
}
