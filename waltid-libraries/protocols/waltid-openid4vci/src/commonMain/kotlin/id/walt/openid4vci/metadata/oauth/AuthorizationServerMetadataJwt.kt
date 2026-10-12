package id.walt.openid4vci.metadata.oauth

import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.keys.Key
import id.walt.openid4vci.metadata.signMetadataJwt
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.time.Clock
import kotlin.time.Instant

/** Signs RFC 8414 metadata with `iss = issuer` for the JSON response's `signed_metadata` member. */
suspend fun AuthorizationServerMetadata.toSignedJwt(
    signingKey: Key,
    algorithm: JwsAlgorithm,
    certificateChain: List<String>? = null,
    issuedAt: Instant = Clock.System.now(),
    keyId: String = signingKey.id.value,
): String {
    val metadataClaims = Json.encodeToJsonElement(AuthorizationServerMetadata.serializer(), copy(signedMetadata = null)).jsonObject
    val collisions = metadataClaims.keys.intersect(setOf("iss", "sub", "iat", "exp", "nbf", "aud", "jti"))
    require(collisions.isEmpty()) {
        "Authorization Server Metadata parameters must not use reserved JWT claims: ${collisions.sorted()}"
    }
    val payload = buildJsonObject {
        metadataClaims.forEach { (name, value) -> put(name, value) }
        put("iss", JsonPrimitive(issuer))
        put("iat", JsonPrimitive(issuedAt.epochSeconds))
    }
    return signMetadataJwt(payload, "JWT", signingKey, algorithm, certificateChain, keyId)
}
