package id.walt.issuer2.service

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyManager
import id.walt.issuer2.config.Issuer2ServiceConfig
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

class IssuerKeyResolver(
    private val serviceConfig: Issuer2ServiceConfig,
) {
    suspend fun resolveSigningKey(keyId: String): Key =
        KeyManager.resolveSerializedKey(serializedKeyFor(keyId))

    suspend fun listPublicJwks(): JsonObject = buildJsonObject {
        put("keys", buildJsonArray {
            configuredSerializedKeys().forEach { (keyId, serializedKey) ->
                val publicJwk = KeyManager.resolveSerializedKey(serializedKey)
                    .getPublicKey()
                    .exportJWKObject()
                add(JsonObject(publicJwk.toMutableMap().apply {
                    putIfAbsent("kid", JsonPrimitive(keyId))
                }))
            }
        })
    }

    private fun serializedKeyFor(keyId: String): String =
        configuredSerializedKeys()[keyId]
            ?: keyId.takeIf { it.trimStart().startsWith("{") }
            ?: throw IllegalArgumentException("Unknown issuer key id: $keyId")

    private fun configuredSerializedKeys(): Map<String, String> =
        buildMap {
            put(DEFAULT_KEY_ID, serviceConfig.ciTokenKey)
            putAll(serviceConfig.issuerKeys)
        }

    companion object {
        const val DEFAULT_KEY_ID = "default"
    }
}
