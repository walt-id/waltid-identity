package id.walt.openid4vci.handlers.credential

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.cose.CoseKey
import id.walt.cose.JWKKeyCoseTransform.getCosePublicKey
import id.walt.crypto.utils.Base64Utils.decodeFromBase64Url
import id.walt.did.dids.DidService
import id.walt.did.dids.DidUtils
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal object JwtProofUtils {
    fun parseJwtHeader(jwt: String): JsonObject =
        jwt.substringBefore(".").let {
            Json.decodeFromString(it.decodeFromBase64Url().decodeToString())
        }

    suspend fun resolveHolderKey(jwt: String): CoseKey {
        val header = parseJwtHeader(jwt)

        return when {
            JWT_HEADER_JWK in header -> {
                val holderJwk = requireNotNull(header[JWT_HEADER_JWK]?.jsonObject) {
                    "Proof JWT header contains jwk but it is not a JSON object"
                }
                JWKKey.importJWK(holderJwk.toString()).getOrThrow().getCosePublicKey()
            }

            JWT_HEADER_KID in header -> {
                val holderKid = header[JWT_HEADER_KID]?.jsonPrimitive?.content
                    ?: throw IllegalArgumentException("Proof JWT header contains kid but it is blank")

                require(DidUtils.isDidUrl(holderKid)) {
                    "Proof JWT kid must be a DID URL when using kid-based holder key resolution: $holderKid"
                }

                val holderKey: Key = DidService.resolveToKey(holderKid.substringBefore("#")).getOrThrow()
                JWKKey.importJWK(holderKey.exportJWK()).getOrThrow().getCosePublicKey()
            }

            else -> throw IllegalArgumentException("Proof JWT header must contain kid or jwk claim")
        }
    }

    private const val JWT_HEADER_JWK = "jwk"
    private const val JWT_HEADER_KID = "kid"
}
