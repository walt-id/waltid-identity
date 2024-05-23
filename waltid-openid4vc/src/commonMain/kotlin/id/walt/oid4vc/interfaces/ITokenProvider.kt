package id.walt.oid4vc.interfaces

import id.walt.crypto.keys.Key
import id.walt.oid4vc.providers.TokenTarget
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

interface ITokenProvider {
    /**
     * Signs and returns the given [payload] with the optionally specified additional [header]s as _JWT token_,
     * using the appropriate key and algorithm for the given token [target], and/or the optionally given [keyId].
     */
    fun signToken(target: TokenTarget, payload: JsonObject, header: JsonObject? = null, keyId: String? = null, privKey: Key? = null): String

    /**
     * Verifies the signature of the given _JWT_ [token], ensuring the appropriate key and algorithm for the given token [target] was used.
     * @return Returns `true` if the signature is valid or `false` otherwise
     */
    fun verifyTokenSignature(target: TokenTarget, token: String): Boolean

    @OptIn(ExperimentalEncodingApi::class)
    fun parseTokenPayload(token: String): JsonObject {
        return token.substringAfter(".").substringBefore(".").let {
            Json.decodeFromString(Base64.UrlSafe.decode(it).decodeToString())
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    fun parseTokenHeader(token: String): JsonObject {
        return token.substringBefore(".").let {
            Json.decodeFromString(Base64.UrlSafe.decode(it).decodeToString())
        }
    }
}
