package id.walt.openid4vci.tokens

import io.ktor.utils.io.core.toByteArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Default JWT signer that follows callback-based key resolution. It chooses the JWS alg
 * from the key metadata, builds a compact JWT, and exposes both the token and signature.
 */
open class DefaultJwtAccessTokenSigner(
    private val resolver: SigningKeyResolver,
    private val json: Json = Json { encodeDefaults = true },
) : AccessTokenSigner {

    @OptIn(ExperimentalEncodingApi::class)
    override suspend fun sign(
        claims: Map<String, Any?>,
        header: Map<String, Any?>,
    ): SignedAccessToken {
        val key = resolver.resolveSigningKey()

        val protectedHeader = buildJsonObject {
            put("typ", JsonPrimitive("JWT"))
            put("alg", JsonPrimitive(key.keyType.jwsAlg))
            put("kid", JsonPrimitive(key.getKeyId()))
            header.forEach { (k, v) -> put(k, v.toJsonElement()) }
        }


        val payloadElement = claims.toJsonObject()

//        val headerB64 =
//            Base64.UrlSafe.encode(json.encodeToString(JsonObject.serializer(), protectedHeader).encodeToByteArray())

//        val payloadB64 =
//            Base64.UrlSafe.encode(json.encodeToString(JsonObject.serializer(), payloadElement).encodeToByteArray())

//        val signingInput = "$headerB64.$payloadB64"

//        // signJws returns a compact JWS (header.payload.signature) using the key's native algorithm
//        val compactJwt = key.signJws(signingInput.encodeToByteArray())

        val compactJwt = key.signJws(payloadElement.toString().toByteArray(), protectedHeader)

        return SignedAccessToken(
            token = compactJwt,
            signature = extractSignature(compactJwt),
        )
    }

    private fun extractSignature(token: String): String {
        val parts = token.split(".")
        require(parts.size == 3) { "Invalid compact JWS: expected 3 parts" }
        return parts[2]
    }
}
