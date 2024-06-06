package id.walt.webwallet.config

import id.walt.crypto.keys.KeyGenerationRequest
import id.walt.crypto.keys.KeyType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable
data class RegistrationDefaultsConfig(
    val defaultKeyConfig: DefaultKeyConfig = DefaultKeyConfig(
         backend = "jwk",
         keyType= KeyType.Ed25519,
         config= null,
    ),
    val defaultDidConfig: DefaultDidConfig = DefaultDidConfig(
        method = "key",
         config = null,
    ),
) : WalletConfig {
    @Serializable
    data class DefaultKeyConfig(
        val backend: String = "jwk",
        val keyType: KeyType = KeyType.Ed25519,
        val config: Map<String, String>?
    )
    @Serializable
    data class DefaultDidConfig(
        val method: String,
        val config: Map<String, String>?
    )

    val keyGenerationRequest = Json.decodeFromJsonElement<KeyGenerationRequest>(Json.encodeToJsonElement(defaultKeyConfig))

    val didMethod = defaultDidConfig.method
    val didConfig: Map<String, JsonPrimitive> = defaultDidConfig.config?.mapValues { JsonPrimitive(it.value) } ?: emptyMap()
}
