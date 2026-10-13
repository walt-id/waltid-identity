package id.walt.openid4vci.metadata

import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.Jwk
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.jose.exportPublicJwkObject
import id.walt.crypto2.keys.Key
import id.walt.openid4vci.tokens.jwt.JwtHeaderParams
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/** Shared JOSE construction; each metadata protocol supplies its own claims and JWT type. */
internal suspend fun signMetadataJwt(
    payload: JsonObject,
    type: String,
    signingKey: Key,
    algorithm: JwsAlgorithm,
    certificateChain: List<String>?,
    keyId: String,
): String {
    require(!algorithm.identifier.startsWith("HS", ignoreCase = true)) {
        "Signed metadata must use an asymmetric JWS algorithm"
    }
    require(keyId.isNotBlank()) {
        "Signed metadata signing key must have a key ID"
    }
    require(certificateChain == null || certificateChain.isNotEmpty()) {
        "Signed metadata certificate chain must not be empty"
    }
    val publicJwk = if (certificateChain == null) buildJsonObject {
        signingKey.exportPublicJwkObject().forEach { (name, value) -> put(name, value) }
        put("kid", JsonPrimitive(keyId))
        put("alg", JsonPrimitive(algorithm.identifier))
        put("use", JsonPrimitive("sig"))
    } else null
    require(publicJwk == null || !Jwk.containsPrivateMaterial(publicJwk)) {
        "Signed metadata signing key must not expose private material"
    }

    return CompactJws.sign(
        payload = payload.toString().encodeToByteArray(),
        key = signingKey,
        algorithm = algorithm,
        protectedHeader = buildJsonObject {
            put(JwtHeaderParams.TYPE, JsonPrimitive(type))
            put(JwtHeaderParams.KEY_ID, JsonPrimitive(keyId))
            if (certificateChain != null) {
                put("x5c", JsonArray(certificateChain.map(::JsonPrimitive)))
            } else {
                put(JwtHeaderParams.JSON_WEB_KEY, requireNotNull(publicJwk))
            }
        },
    )
}
