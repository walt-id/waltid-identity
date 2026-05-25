package id.walt.credentials.signatures

import id.walt.credentials.keyresolver.JwtKeyResolver
import id.walt.crypto.keys.Key
import kotlinx.serialization.json.JsonObject

interface JwtBasedSignature {
    val jwtHeader: JsonObject?

    /**
     * Resolves the signing key from the credential data or JWT header.
     */
    suspend fun getJwtBasedIssuer(credentialData: JsonObject): Key? =
        JwtKeyResolver.resolveFromJwt(jwtHeader, credentialData)
}
