package id.walt.webwallet.config

import id.walt.crypto.keys.KeyGenerationRequest
import id.walt.crypto.keys.KeyType
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.*

@Serializable
data class RegistrationDefaultsConfig(
    val defaultKeyConfig: DefaultKeyConfig = DefaultKeyConfig(),

    val defaultDidConfig: DidMethodConfig = DidMethodConfig(),
) : WalletConfig() {

    @Serializable
    data class DefaultKeyConfig(
        val backend: String = "jwk",
        val keyType: KeyType = KeyType.Ed25519,
        val config: Map<String, String>? = null,
    )

    @Serializable
    data class DidMethodConfig(
        val didMethod: String = "jwk",
        val didConfig: Map<String, JsonPrimitive> = emptyMap(),
    )

    val keyGenerationRequest: KeyGenerationRequest = KeyGenerationRequest(
        backend = defaultKeyConfig.backend,
        keyType = defaultKeyConfig.keyType,
        config = defaultKeyConfig.config?.let { 
            Json.encodeToJsonElement(it).jsonObject 
        } ?: JsonObject(emptyMap())
    )

    @Transient
    val didMethod = defaultDidConfig.didMethod
    @Transient
    val didConfig: Map<String, JsonPrimitive> = defaultDidConfig.didConfig
}
