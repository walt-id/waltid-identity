package id.walt.w3c.schemes

import id.walt.crypto.exceptions.CryptoArgumentException
import id.walt.crypto.exceptions.VerificationException
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.JsonUtils.toJsonObject
import id.walt.crypto.utils.JwsUtils.decodeJws
import id.walt.did.dids.DidService
import id.walt.did.dids.DidUtils
import id.walt.sdjwt.JWTCryptoProvider
import id.walt.sdjwt.SDJwt
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
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
        

        val x5cHeader = jwsParsed.header[JwsHeader.X5C]
        if (x5cHeader != null && x5cHeader is JsonArray && x5cHeader.isNotEmpty()) {
            log.trace { "Found x5c header with ${x5cHeader.size} certificate(s), extracting key from leaf certificate" }
            val key = extractKeyFromX5cHeader(x5cHeader)
            val keyId = jwsParsed.header[JwsHeader.KEY_ID]?.jsonPrimitive?.content 
                ?: key.getKeyId()
            return KeyInfo(keyId, key)
        }
        
        // Fall back to DID-based key resolution
        val keyId =
            jwsParsed.header[JwsHeader.KEY_ID]?.jsonPrimitive?.content ?: throw IllegalArgumentException("Missing key ID in JWS header")
        val issuerId = (jwsParsed.payload[JwsOption.ISSUER]?.jsonPrimitive?.content ?: keyId)
        val key = if (DidUtils.isDidUrl(issuerId)) {
            log.trace { "Resolving key from issuer did: $issuerId" }
            DidService.resolveToKey(issuerId)
                .also {
                    if (log.isTraceEnabled()) {
                        val exportedJwk = it.getOrNull()?.getPublicKey()?.exportJWK()
                        log.trace { "Imported key: $it from did: $issuerId, public is: $exportedJwk" }
                    }
                }
                .getOrThrow()
        } else
            throw UnsupportedOperationException("W3C credentials require either x5c header or DID-based issuer ID for signature verification.")
        return KeyInfo(keyId, key)
    }

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    suspend fun getIssuerKeysInfo(jws: String): KeysInfo {
        val jwsParsed = jws.decodeJws()
        

        val x5cHeader = jwsParsed.header[JwsHeader.X5C]
        if (x5cHeader != null && x5cHeader is JsonArray && x5cHeader.isNotEmpty()) {
            log.trace { "Found x5c header with ${x5cHeader.size} certificate(s), extracting key from leaf certificate" }
            val key = extractKeyFromX5cHeader(x5cHeader)
            val keyId = jwsParsed.header[JwsHeader.KEY_ID]?.jsonPrimitive?.content 
                ?: key.getKeyId()
            return KeysInfo(keyId, setOf(key))
        }
        
        // Fall back to DID-based key resolution
        val keyId =
            jwsParsed.header[JwsHeader.KEY_ID]?.jsonPrimitive?.content ?: throw IllegalArgumentException("Missing key ID in JWS header")
        val issuerId = (jwsParsed.payload[JwsOption.ISSUER]?.jsonPrimitive?.content ?: keyId)
        val keys = if (DidUtils.isDidUrl(issuerId)) {
            log.trace { "Resolving keys from issuer did: $issuerId" }
            DidService.resolveToKeys(issuerId)
                .also {
                    if (log.isTraceEnabled()) {
                        log.trace { "Imported keys: ${it.getOrNull()?.size} from did: $issuerId" }
                    }
                }
                .getOrThrow()
        } else
            throw UnsupportedOperationException("W3C credentials require either x5c header or DID-based issuer ID for signature verification.")
        return KeysInfo(keyId, keys)
    }
    
    /**
     * Extracts the public key from the leaf certificate in an x5c header.
     * Per RFC 7515 Section 4.1.6, the leaf certificate (containing the signing key) MUST be first in the array.
     */
    private suspend fun extractKeyFromX5cHeader(x5cHeader: JsonArray): Key {
        if (x5cHeader.isEmpty()) throw IllegalArgumentException("Certificate chain in 'x5c' must not be empty.")
        val leafCertBase64 = x5cHeader.first().jsonPrimitive.content
        return JWKKey.importDERorPEM(leafCertBase64).getOrThrow()
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
