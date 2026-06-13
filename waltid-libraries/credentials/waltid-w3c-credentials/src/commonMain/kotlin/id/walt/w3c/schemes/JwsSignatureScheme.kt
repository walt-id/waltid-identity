package id.walt.w3c.schemes

import id.walt.credentials.keyresolver.JwtKeyResolver
import id.walt.crypto.exceptions.CryptoArgumentException
import id.walt.crypto.exceptions.VerificationException
import id.walt.crypto.keys.Key
import id.walt.crypto.utils.JsonUtils.toJsonObject
import id.walt.crypto.utils.JwsUtils.decodeJws
import id.walt.sdjwt.JWTCryptoProvider
import id.walt.sdjwt.SDJwt
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import love.forte.plugin.suspendtrans.annotation.JsPromise
import love.forte.plugin.suspendtrans.annotation.JvmAsync
import love.forte.plugin.suspendtrans.annotation.JvmBlocking
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

private val log = KotlinLogging.logger { }

@OptIn(ExperimentalJsExport::class)
@JsExport
class JwsSignatureScheme : SignatureScheme {

    object JwsHeader {
        const val KEY_ID = "kid"
    }

    object JwsOption {
        const val SUBJECT = "sub"
        const val ISSUER = "iss"
        const val EXPIRATION = "exp"
        const val NOT_BEFORE = "nbf"
        const val VC_ID = "jti"
        const val VC = "vc"
    }

    data class KeyInfo(val keyId: String, val key: Key)
    data class KeysInfo(val keyId: String, val keys: Set<Key>)

    fun toPayload(data: JsonObject, jwtOptions: Map<String, JsonElement> = emptyMap(), wrapInVc: Boolean = true) =
        if (wrapInVc) {
            mapOf(
                JwsOption.ISSUER to jwtOptions[JwsOption.ISSUER],
                JwsOption.SUBJECT to jwtOptions[JwsOption.SUBJECT],
                JwsOption.VC to data,
                *(jwtOptions.entries.map { it.toPair() }.toTypedArray())
            ).toJsonObject()
        } else {
            mapOf(
                JwsOption.ISSUER to jwtOptions[JwsOption.ISSUER],
                JwsOption.SUBJECT to jwtOptions[JwsOption.SUBJECT],
                *(data.entries.map { it.toPair() }.toTypedArray()),
                *(jwtOptions.entries.map { it.toPair() }.toTypedArray())
            ).toJsonObject()
        }

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    suspend fun getIssuerKeyInfo(jws: String): KeyInfo {
        val jwsParsed = jws.substringBefore("~").decodeJws()
        val keyId = jwsParsed.header[JwsHeader.KEY_ID]?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("Missing key ID in JWS header")
        val issuerId = jwsParsed.payload[JwsOption.ISSUER]?.jsonPrimitive?.content ?: keyId
        val key = JwtKeyResolver.resolveFromJwt(jwsParsed.header, jwsParsed.payload)
            ?: throw IllegalArgumentException("Could not resolve issuer key for '$issuerId'")
        return KeyInfo(keyId, key)
    }

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    suspend fun getIssuerKeysInfo(jws: String): KeysInfo {
        val jwsParsed = jws.decodeJws()
        val keyId = jwsParsed.header[JwsHeader.KEY_ID]?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("Missing key ID in JWS header")
        val issuerId = jwsParsed.payload[JwsOption.ISSUER]?.jsonPrimitive?.content ?: keyId
        val key = JwtKeyResolver.resolveFromJwt(jwsParsed.header, jwsParsed.payload)
            ?: throw IllegalArgumentException("Could not resolve issuer key for '$issuerId'")
        return KeysInfo(keyId, setOf(key))
    }

    /**
     * args:
     * - kid: Key ID
     * - subjectDid: Holder DID
     * - issuerDid: Issuer DID
     */
    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    suspend fun sign(
        data: JsonObject, key: Key,
        /** Set additional options in the JWT header */
        jwtHeaders: Map<String, JsonElement> = emptyMap(),
        /** Set additional options in the JWT payload */
        jwtOptions: Map<String, JsonElement> = emptyMap(),
        wrapInVc: Boolean = true
    ): String {
        val payload = Json.encodeToString(
            toPayload(data, jwtOptions, wrapInVc)
        ).encodeToByteArray()

        return key.signJws(payload, jwtHeaders)
    }

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    suspend fun verify(data: String): Result<JsonElement> = runCatching {
        // Try to verify with all keys from the issuer's DID document
        val keysInfo = getIssuerKeysInfo(data)
        val jws = data.split("~")[0]

        // Try each key until one succeeds
        var lastException: Throwable? = null
        for (key in keysInfo.keys) {
            val result = try {
                key.verifyJws(jws)
            } catch (e: Exception) {
                lastException = e
                Result.failure(e)
            }

            if (result.isSuccess) {
                log.trace { "Verification successful with one of the keys from the DID document" }
                return result
            }
        }

        // If we get here, all keys failed
        return Result.failure(lastException ?: CryptoArgumentException("Verification failed with all keys from the DID document"))
    }

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    suspend fun verifySDJwt(data: String, jwtCryptoProvider: JWTCryptoProvider): Result<JsonElement> = runCatching {
        return SDJwt.verifyAndParse(data, jwtCryptoProvider).let {
            if (it.verified)
                Result.success(it.sdJwt.fullPayload)
            else
                Result.failure(VerificationException(it.message ?: "Verification failed"))
        }
    }
}
