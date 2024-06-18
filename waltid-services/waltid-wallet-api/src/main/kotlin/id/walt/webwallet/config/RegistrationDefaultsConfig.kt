package id.walt.webwallet.config

import id.walt.crypto.keys.KeyGenerationRequest
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonPrimitive

@Serializable
data class RegistrationDefaultsConfig(
    val defaultKeyConfig: KeyGenerationRequest = KeyGenerationRequest(),

    val defaultDidConfig: DidMethodConfig = DidMethodConfig(),
) : WalletConfig() {
    @Serializable
    data class DidMethodConfig(
        val didMethod: String = "jwk",
        val didConfig: Map<String, JsonPrimitive> = emptyMap(),
    )

    @Transient
    val didMethod = defaultDidConfig.didMethod
    @Transient
    val didConfig: Map<String, JsonPrimitive> = defaultDidConfig.didConfig
}
