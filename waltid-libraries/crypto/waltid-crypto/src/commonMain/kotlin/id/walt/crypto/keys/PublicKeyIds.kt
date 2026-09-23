package id.walt.crypto.keys

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Public-facing key identifiers vs KMS locators.
 *
 * Azure Key Vault (and similar) expose `https://…` URLs as JWK `kid`. Those are
 * operational locators and must never be published as JWT/JAR/JWKS/DID kids.
 * The default public id is the RFC 7638 JWK thumbprint of the public key.
 */
object PublicKeyIds {

    fun isHttpKeyId(id: String): Boolean =
        id.startsWith("http://") || id.startsWith("https://")

    /**
     * Stable public key id for JWKS entries, JWT/JAR headers, and DID fragments
     * (when the DID method does not mandate a different VM id, e.g. did:key).
     */
    suspend fun Key.publicKeyId(): String {
        val publicKey = getPublicKey()
        val id = publicKey.getKeyId()
        return if (isHttpKeyId(id)) publicKey.getThumbprint() else id
    }

    /**
     * Public JWK suitable for DID documents / JWKS: material plus a non-HTTP `kid`
     * (RFC 7638 thumbprint when the source kid was a vault/URL locator).
     */
    suspend fun Key.publicJwkForPublish(): JsonObject {
        val publicKey = getPublicKey()
        val thumbprint = publicKey.getThumbprint()
        val jwk = publicKey.exportJWKObject().toMutableMap()
        val existingKid = jwk["kid"]?.jsonPrimitive?.contentOrNull
        if (existingKid == null || isHttpKeyId(existingKid)) {
            jwk["kid"] = JsonPrimitive(thumbprint)
        }
        return JsonObject(jwk)
    }

    fun sanitizePublicJwk(jwk: JsonObject, thumbprint: String): JsonObject {
        val existingKid = jwk["kid"]?.jsonPrimitive?.contentOrNull
        if (existingKid != null && !isHttpKeyId(existingKid)) return jwk
        return JsonObject(jwk.toMutableMap().apply {
            put("kid", JsonPrimitive(thumbprint))
        })
    }
}
