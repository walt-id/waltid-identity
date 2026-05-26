package id.walt.issuer2.service.openid4vci

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyManager
import id.walt.issuer2.config.Issuer2ServiceConfig
import id.walt.issuer2.service.CredentialProfileService
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

class JwksService(
    private val serviceConfig: Issuer2ServiceConfig,
    private val profileService: CredentialProfileService,
) {
    suspend fun listJwks(): JsonObject {
        val tokenSigningKey = KeyManager.resolveSerializedKey(serviceConfig.ciTokenKey)
        val profileIssuerKeys = profileService.listProfiles()
            .map { profile -> KeyManager.resolveSerializedKey(profile.issuerKey) }

        return buildJsonObject {
            put("keys", buildJsonArray {
                (listOf(tokenSigningKey) + profileIssuerKeys)
                    .map { key -> key.getPublicJwkWithKid() }
                    .deduplicated()
                    .forEach { add(it) }
            })
        }
    }

    private suspend fun Key.getPublicJwkWithKid(): JsonObject {
        val publicJwk = getPublicKey().exportJWKObject()
        return JsonObject(publicJwk.toMutableMap().apply {
            putIfAbsent("kid", JsonPrimitive(getKeyId()))
        })
    }

    private fun List<JsonObject>.deduplicated(): List<JsonObject> {
        val seen = mutableSetOf<String>()
        return filter { jwk ->
            val keys = jwk.deduplicationKeys()
            if (keys.any { it in seen }) {
                false
            } else {
                seen.addAll(keys)
                true
            }
        }
    }

    private fun JsonObject.deduplicationKeys(): Set<String> =
        setOfNotNull(
            this["kid"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }?.let { "kid:$it" },
            JsonObject(filterKeys { it != "kid" }).toString().let { "jwk:$it" },
        )
}
