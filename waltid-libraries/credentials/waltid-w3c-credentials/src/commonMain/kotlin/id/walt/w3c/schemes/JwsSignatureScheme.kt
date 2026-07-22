package id.walt.w3c.schemes

import id.walt.credentials.keyresolver.JwtKeyResolver
import id.walt.credentials.keyresolver.Crypto2JwtKeyResolver
import id.walt.credentials.keyresolver.ResolvedJwtVerificationKey
import id.walt.crypto.exceptions.CryptoArgumentException
import id.walt.crypto.keys.Key
import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.keys.Key as Crypto2Key
import id.walt.crypto.utils.JsonUtils.toJsonObject
import id.walt.crypto.utils.JwsUtils.decodeJws
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.CancellationException
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
        const val X5C = "x5c"
    }

    object JwsOption {
        const val SUBJECT = "sub"
        const val ISSUER = "iss"
        const val EXPIRATION = "exp"
        const val NOT_BEFORE = "nbf"
        const val VC_ID = "jti"
        const val VC = "vc"
    }

    @Deprecated("Use Crypto2KeyInfo")
    data class KeyInfo(val keyId: String, val key: Key)
    @Deprecated("Use Crypto2KeyInfo")
    data class KeysInfo(val keyId: String, val keys: Set<Key>)
    @JsExport.Ignore
    data class Crypto2KeyInfo(val keyId: String?, val resolved: ResolvedJwtVerificationKey)

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

    @Deprecated("Use getIssuerCrypto2KeyInfo")
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

    @Deprecated("Use getIssuerCrypto2KeyInfo")
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
    @Deprecated("Use the crypto2 overload accepting a Key and JwsAlgorithm")
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

    @JsExport.Ignore
    suspend fun sign(
        data: JsonObject,
        key: Crypto2Key,
        algorithm: JwsAlgorithm,
        jwtHeaders: Map<String, JsonElement> = emptyMap(),
        jwtOptions: Map<String, JsonElement> = emptyMap(),
        wrapInVc: Boolean = true,
    ): String {
        val payload = Json.encodeToString(toPayload(data, jwtOptions, wrapInVc)).encodeToByteArray()
        return CompactJws.sign(
            payload = payload,
            key = key,
            algorithm = algorithm,
            protectedHeader = JsonObject(jwtHeaders),
        )
    }

    @JsExport.Ignore
    suspend fun getIssuerCrypto2KeyInfo(
        jws: String,
        resolver: Crypto2JwtKeyResolver = Crypto2JwtKeyResolver(),
    ): Crypto2KeyInfo {
        val compact = jws.substringBefore('~')
        val decoded = CompactJws.decodeUnverified(compact)
        val payload = Json.parseToJsonElement(decoded.payload.decodeToString()) as? JsonObject
            ?: throw IllegalArgumentException("JWS payload must be a JSON object")
        val resolved = resolver.resolveFromJwt(decoded.protectedHeader, payload)
            ?: throw IllegalArgumentException("Could not resolve issuer key")
        return Crypto2KeyInfo(
            keyId = decoded.protectedHeader[JwsHeader.KEY_ID]?.jsonPrimitive?.content,
            resolved = resolved,
        )
    }

    @JsExport.Ignore
    suspend fun verifyCrypto2(
        data: String,
        allowedAlgorithms: Set<JwsAlgorithm> = JwsAlgorithm.entries.toSet(),
        resolver: Crypto2JwtKeyResolver = Crypto2JwtKeyResolver(),
    ): Result<JsonElement> = resultOfSuspend {
        val compact = data.substringBefore('~')
        val info = getIssuerCrypto2KeyInfo(compact, resolver)
        val verified = CompactJws.verify(compact, info.resolved.key, allowedAlgorithms)
        Json.parseToJsonElement(verified.payload.decodeToString())
    }

    @JsExport.Ignore
    suspend fun verifyCrypto2(
        data: String,
        key: Crypto2Key,
        allowedAlgorithms: Set<JwsAlgorithm>,
    ): Result<JsonElement> = resultOfSuspend {
        val verified = CompactJws.verify(data.substringBefore('~'), key, allowedAlgorithms)
        Json.parseToJsonElement(verified.payload.decodeToString())
    }

    @Deprecated("Use verifyCrypto2")
    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    suspend fun verify(data: String): Result<JsonElement> = runCatching {
        // Get keys from either x5c header or DID document
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
                log.trace { "Verification successful with key" }
                return result
            }
        }

        // If we get here, all keys failed
        return Result.failure(lastException ?: CryptoArgumentException("Verification failed with all available keys"))
    }

}

private suspend fun <T> resultOfSuspend(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (cause: CancellationException) {
    throw cause
} catch (cause: Throwable) {
    Result.failure(cause)
}
