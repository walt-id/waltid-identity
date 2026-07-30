package id.walt.openid4vci.metadata.issuer

import id.walt.crypto.keys.Key
import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.Jwk
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.jose.exportPublicJwkObject
import id.walt.crypto2.keys.Key as Crypto2Key
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

    val reservedPayloadClaims = setOf(
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
 *
 * [keyId] is the published JOSE `kid` and defaults to the crypto2 key ID. They are not always the same thing: a crypto2
 * `KeyId` identifies a stored record, which for a KMS-backed key is a resource path, whereas the `kid` a relying party
 * resolves is whatever the issuer already publishes in its JWKS and its credentials - usually the RFC 7638 thumbprint.
 * Callers whose record ID is not the published `kid` must pass it explicitly, or verifiers stop finding the key.
 */
suspend fun CredentialIssuerMetadata.toSignedJwt(
    signingKey: Crypto2Key,
    algorithm: JwsAlgorithm,
    issuedAt: Instant = Clock.System.now(),
    keyId: String = signingKey.id.value,
): String {
    require(!algorithm.identifier.startsWith("HS", ignoreCase = true)) {
        "Credential Issuer Metadata must use an asymmetric JWS algorithm"
    }
    require(keyId.isNotBlank()) {
        "Credential Issuer Metadata signing key must have a key ID"
    }
    val publicJwk = buildJsonObject {
        signingKey.exportPublicJwkObject().forEach { (name, value) -> put(name, value) }
        put("kid", JsonPrimitive(keyId))
        put("alg", JsonPrimitive(algorithm.identifier))
        put("use", JsonPrimitive("sig"))
    }
    require(!Jwk.containsPrivateMaterial(publicJwk)) {
        "Credential Issuer Metadata signing key must not expose private material"
    }

    return CompactJws.sign(
        payload = signedMetadataPayload(issuedAt).toString().encodeToByteArray(),
        key = signingKey,
        algorithm = algorithm,
        protectedHeader = buildJsonObject {
            put(JwtHeaderParams.TYPE, JsonPrimitive(CredentialIssuerMetadataJwt.TYPE))
            put(JwtHeaderParams.KEY_ID, JsonPrimitive(keyId))
            put(JwtHeaderParams.JSON_WEB_KEY, publicJwk)
        },
    )
}

/** Builds the signed-metadata payload shared by the crypto2 and legacy signing paths. */
private fun CredentialIssuerMetadata.signedMetadataPayload(issuedAt: Instant) = buildJsonObject {
    val metadataClaims = Json
        .encodeToJsonElement(CredentialIssuerMetadata.serializer(), this@signedMetadataPayload)
        .jsonObject
    val collisions = metadataClaims.keys.intersect(CredentialIssuerMetadataJwt.reservedPayloadClaims)
    require(collisions.isEmpty()) {
        "Credential Issuer Metadata parameters must not use reserved signed-metadata claims: ${collisions.sorted()}"
    }
    metadataClaims.forEach { (name, value) -> put(name, value) }
    put(JwtPayloadClaims.ISSUER, JsonPrimitive(credentialIssuer))
    put(JwtPayloadClaims.SUBJECT, JsonPrimitive(credentialIssuer))
    put(JwtPayloadClaims.ISSUED_AT, JsonPrimitive(issuedAt.epochSeconds))
}

@Deprecated("Use the Crypto2Key overload")
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

    val payload = signedMetadataPayload(issuedAt)
    val protectedHeader: Map<String, JsonElement> = mapOf(
        JwtHeaderParams.TYPE to JsonPrimitive(CredentialIssuerMetadataJwt.TYPE),
        JwtHeaderParams.KEY_ID to JsonPrimitive(keyId),
        JwtHeaderParams.JSON_WEB_KEY to publicJwk,
    )

    return signingKey.signJws(payload.toString().encodeToByteArray(), protectedHeader)
}
