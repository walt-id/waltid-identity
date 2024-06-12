package id.walt.webwallet.config

import id.walt.crypto.keys.KeyGenerationRequest
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.*

@Serializable
data class RegistrationDefaultsConfig(
    val defaultKeyConfig: KeyGenerationRequest = KeyGenerationRequest(),
    /*
    : JsonObject = buildJsonObject {
        put("backend", JsonPrimitive("jwk"))
        put("keyType", JsonPrimitive("Ed25519"))
    },*/

    val defaultDidConfig: DidMethodConfig = DidMethodConfig()
    /*private val defaultDidConfig: JsonObject = buildJsonObject {
        put("method", JsonPrimitive("jwk"))
    }*/
) : WalletConfig() {
//    val keyGenerationRequest = Json.decodeFromJsonElement<KeyGenerationRequest>(defaultKeyConfig)

    @Serializable
    data class DidMethodConfig(
        val didMethod: String = "jwk",
        val didConfig: Map<String, JsonPrimitive> = emptyMap()
    )

    @Transient val didMethod = defaultDidConfig.didMethod
    @Transient val didConfig: Map<String, JsonPrimitive> = defaultDidConfig.didConfig
}
