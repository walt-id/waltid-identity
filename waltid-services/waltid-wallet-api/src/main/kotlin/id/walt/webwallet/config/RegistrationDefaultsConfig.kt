package id.walt.webwallet.config

import id.walt.crypto.keys.KeyGenerationRequest
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonPrimitive

@Serializable
data class RegistrationDefaultsConfig(
    val defaultKeyConfig: KeyGenerationRequest = KeyGenerationRequest(),
    val defaultDidConfig: DidMethodConfig = DidMethodConfig(),
    val defaultIssuerConfig: IssuerConfig? = null
) : WalletConfig() {

    @Serializable
    data class DidMethodConfig(
        val method: String = "jwk",
        val config: Map<String, JsonPrimitive> = emptyMap(),
    )

    @Serializable
    data class IssuerConfig(
        val did: String,
        val description: String,
        val uiEndpoint: String,
        val configurationEndpoint: String,
        val authorized: Boolean
    )

    @Transient
    val didMethod = defaultDidConfig.method

    @Transient
    val didConfig: Map<String, JsonPrimitive>? = defaultDidConfig.config
}
