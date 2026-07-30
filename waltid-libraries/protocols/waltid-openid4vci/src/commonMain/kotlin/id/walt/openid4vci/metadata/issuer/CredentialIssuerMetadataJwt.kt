package id.walt.openid4vci.metadata.issuer

import id.walt.crypto.keys.Key
import id.walt.openid4vci.tokens.jwt.JwtHeaderParams
import id.walt.openid4vci.tokens.jwt.JwtPayloadClaims
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.time.Clock
import kotlin.time.Instant

/** OpenID4VCI 1.0 signed Credential Issuer Metadata constants. */
object CredentialIssuerMetadataJwt {
    const val TYPE = "openidvci-issuer-metadata+jwt"
    const val MEDIA_TYPE = "application/jwt"
    const val TYPED_MEDIA_TYPE = "application/openidvci-issuer-metadata+jwt"

    internal val reservedPayloadClaims = setOf(
        JwtPayloadClaims.ISSUER,
        JwtPayloadClaims.SUBJECT,
        JwtPayloadClaims.ISSUED_AT,
        JwtPayloadClaims.EXPIRATION,
    )
}

/**
 * Signs this Credential Issuer Metadata as the compact JWS defined by OpenID4VCI 1.0 section 12.2.3.
 *
 * The public JWK is embedded in the protected header. Key lookup, certificate-chain selection, trust policy, and HTTP
 * content negotiation intentionally remain responsibilities of the embedding issuer service.
 */
suspend fun CredentialIssuerMetadata.toSignedJwt(
    signingKey: Key,
    issuedAt: Instant = Clock.System.now(),
): String {
    val algorithm = signingKey.keyType.jwsAlg
    require(
        algorithm.isNotBlank() &&
            !algorithm.equals("none", ignoreCase = true) &&
            !algorithm.startsWith("HS", ignoreCase = true)
    ) {
        "Credential Issuer Metadata must use an asymmetric JWS algorithm"
    }

    val keyId = signingKey.getKeyId()
    require(keyId.isNotBlank()) {
        "Credential Issuer Metadata signing key must have a key ID"
    }
    val publicJwk = buildJsonObject {
        signingKey.getPublicKey().exportJWKObject().forEach { (name, value) -> put(name, value) }
        put("kid", JsonPrimitive(keyId))
        put("alg", JsonPrimitive(algorithm))
        put("use", JsonPrimitive("sig"))
    }

    val metadataClaims = Json
        .encodeToJsonElement(CredentialIssuerMetadata.serializer(), this)
        .jsonObject
    val collisions = metadataClaims.keys.intersect(CredentialIssuerMetadataJwt.reservedPayloadClaims)
    require(collisions.isEmpty()) {
        "Credential Issuer Metadata parameters must not use reserved signed-metadata claims: ${collisions.sorted()}"
    }

    val payload = buildJsonObject {
        metadataClaims.forEach { (name, value) -> put(name, value) }
        put(JwtPayloadClaims.ISSUER, JsonPrimitive(credentialIssuer))
        put(JwtPayloadClaims.SUBJECT, JsonPrimitive(credentialIssuer))
        put(JwtPayloadClaims.ISSUED_AT, JsonPrimitive(issuedAt.epochSeconds))
    }
    val protectedHeader: Map<String, JsonElement> = mapOf(
        JwtHeaderParams.TYPE to JsonPrimitive(CredentialIssuerMetadataJwt.TYPE),
        JwtHeaderParams.KEY_ID to JsonPrimitive(keyId),
        JwtHeaderParams.JSON_WEB_KEY to publicJwk,
    )

    return signingKey.signJws(payload.toString().encodeToByteArray(), protectedHeader)
}
