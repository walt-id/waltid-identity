package id.walt.oid4vc.interfaces

import id.walt.crypto.keys.Key
import id.walt.crypto.utils.Base64Utils.base64UrlDecode
import id.walt.mdoc.dataelement.MapElement
import id.walt.oid4vc.providers.TokenTarget
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

interface ITokenProvider {
    /**
     * Signs and returns the given [payload] with the optionally specified additional [header]s as _JWT token_,
     * using the appropriate key and algorithm for the given token [target], and/or the optionally given [keyId].
     */
    fun signToken(target: TokenTarget, payload: JsonObject, header: JsonObject? = null, keyId: String? = null, privKey: Key? = null): String

    /**
     * Signs and returns the given [payload] with the optionally specified additional [header]s as _CWT token_,
     * using the appropriate key and algorithm for the given token [target], and/or the optionally given [keyId] or [privKey].
     */
    fun signCWTToken(target: TokenTarget, payload: MapElement, header: MapElement? = null, keyId: String? = null, privKey: Key? = null): String

    /**
     * Verifies the signature of the given _JWT_ [token], ensuring the appropriate key and algorithm for the given token [target] was used.
     * @return Returns `true` if the signature is valid or `false` otherwise
     */
    fun verifyTokenSignature(target: TokenTarget, token: String): Boolean

    /**
     * Verifies the signature of the given _CoseSign1_ [token] (CWT), ensuring the appropriate key and algorithm for the given token [target] was used.
     * @return Returns `true` if the signature is valid or `false` otherwise
     */
    fun verifyCOSESign1Signature(target: TokenTarget, token: String): Boolean

    fun parseTokenPayload(token: String): JsonObject {
        return token.substringAfter(".").substringBefore(".").let {
            Json.decodeFromString(it.base64UrlDecode().decodeToString())
        }
    }

    fun parseTokenHeader(token: String): JsonObject {
        return token.substringBefore(".").let {
            Json.decodeFromString(it.base64UrlDecode().decodeToString())
        }
    }
}
