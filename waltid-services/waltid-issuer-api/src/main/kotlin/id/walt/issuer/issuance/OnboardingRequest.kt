package id.walt.issuer.issuance

import id.walt.crypto.keys.KeyGenerationRequest
import id.walt.crypto.utils.JsonUtils.toJsonObject
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.*

@Serializable
data class OnboardingRequest(
    val key: JsonObject = buildJsonObject {
        put("backend", JsonPrimitive("jwk"))
        put("keyType", JsonPrimitive("Ed25519"))
    },

    val did: JsonObject = buildJsonObject {
        put("method", JsonPrimitive("jwk"))
    },
) {
    constructor(key: Map<String, *>, did: Map<String, *>) : this(key = key.toJsonObject(), did = did.toJsonObject())

    @Transient
    val keyGenerationRequest = Json.decodeFromJsonElement<KeyGenerationRequest>(key)

    @Transient
    val didMethod = did["method"]!!.jsonPrimitive.content

    @Transient
    val didConfig: Map<String, JsonPrimitive> = did["config"]?.jsonObject?.mapValues { it.value.jsonPrimitive } ?: emptyMap()
}
