package id.walt.issuer

import id.walt.crypto.keys.KeyGenerationRequest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable
data class OnboardingRequest(
    private val key: JsonObject = buildJsonObject {
        put("backend", JsonPrimitive("jwk"))
        put("keyType", JsonPrimitive("Ed25519"))
    },

    private val did: JsonObject = buildJsonObject {
        put("method", JsonPrimitive("jwk"))
    }
) {
    val keyGenerationRequest = Json.decodeFromJsonElement<KeyGenerationRequest>(key)

    val didMethod = did["method"]!!.jsonPrimitive.content
    val didConfig: Map<String, JsonPrimitive> = did["config"]?.jsonObject?.mapValues { it.value.jsonPrimitive } ?: emptyMap()
}
