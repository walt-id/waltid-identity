package id.walt.credentials.signatures

import id.walt.credentials.keyresolver.JwtKeyResolver
import id.walt.did.dids.document.models.verification.relationship.VerificationRelationshipType
import id.walt.credentials.keyresolver.Crypto2JwtKeyResolver
import id.walt.credentials.keyresolver.ResolvedJwtVerificationKey
import id.walt.crypto.keys.Key
import kotlinx.serialization.json.JsonObject

interface JwtBasedSignature {
    val jwtHeader: JsonObject?

    /**
     * Resolves the signing key from the credential data or JWT header.
     */
    @Deprecated("Use getCrypto2JwtBasedIssuer(credentialData, resolver)")
    suspend fun getJwtBasedIssuer(credentialData: JsonObject): Key? =
        JwtKeyResolver.resolveFromJwt(jwtHeader, credentialData)

    suspend fun getCrypto2JwtBasedIssuer(
        credentialData: JsonObject,
        resolver: Crypto2JwtKeyResolver = Crypto2JwtKeyResolver(
            verificationRelationship = VerificationRelationshipType.AssertionMethod,
        ),
    ): ResolvedJwtVerificationKey? = resolver.resolveFromJwt(jwtHeader, credentialData)
}
