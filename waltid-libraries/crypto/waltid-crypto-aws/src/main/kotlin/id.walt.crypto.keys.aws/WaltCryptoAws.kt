package id.walt.crypto.keys.aws

import id.walt.crypto.keys.KeyGenerationRequest
import id.walt.crypto.keys.KeyManager
import id.walt.crypto.keys.KeySerialization
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement

object WaltCryptoAws {
    fun init() {
        KeyManager.register<AWSKey>("aws") { generateRequest: KeyGenerationRequest ->
            AWSKey.generateKey(
                generateRequest.keyType,
                Json.decodeFromJsonElement(generateRequest.config!!)
            )
        }
        KeySerialization.registerExternalKeyType(AWSKey::class)

    }
}

