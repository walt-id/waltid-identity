package id.walt.credentials.signatures

import id.walt.credentials.keyresolver.IssuerKeyResolver
import id.walt.crypto.keys.Key
import kotlinx.serialization.json.JsonObject

interface JwtBasedSignature {
    val jwtHeader: JsonObject?
    //val x5cList: X5CList?

    /**
     * Get issuer key from credential data or jwt header
     */
    suspend fun getJwtBasedIssuer(credentialData: JsonObject): Key? =
        IssuerKeyResolver.resolveForJwtSignedCredential(jwtHeader, credentialData)
}
