package id.walt.sdjwt

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.promise
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Expected default implementation for JWTCryptoProvider on each platform
 * Implemented in platform specific modules
 * @see JWTCryptoProvider
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
open class SimpleAsyncJWTCryptoProvider(
    private val algorithm: String,
    private val keyParam: dynamic,
    private val options: dynamic,
) : JSAsyncJWTCryptoProvider {
    @JsExport.Ignore
    override suspend fun sign(
        payload: JsonObject,
        keyID: String?,
        typ: String,
        headers: Map<String, Any>
    ): String = suspendCancellableCoroutine { continuation ->
        console.log("SIGNING", payload.toString())
        jose.SignJWT(JSON.parse(payload.toString())).setProtectedHeader(buildJsonObject {
            put("alg", algorithm)
            put("typ", typ)
            keyID?.also { put("kid", it) }
            headers.forEach { (key, value) -> put(key, value.toString()) }
        }.let { JSON.parse(it.toString()) }).sign(keyParam, options).then({
            console.log("SIGNED")
            continuation.resume(it)
        }, {
            console.log("ERROR SIGNING", it.message)
            continuation.resumeWithException(Throwable(it.message ?: "JWT signing failed"))
        })
    }

    @JsExport.Ignore
    override suspend fun verify(jwt: String): JwtVerificationResult =
        suspendCancellableCoroutine { continuation ->
            console.log("Verifying JWT: $jwt")
            jose.jwtVerify(jwt, keyParam, options ?: js("{}")).then(
                {
                    console.log("Verified.")
                    continuation.resume(JwtVerificationResult(true))
                },
                {
                    console.log("Verification failed (SimpleAsyncJWTCryptoProvider): ${it.message}")
                    continuation.resume(JwtVerificationResult(false, it.message))
                }
            )
        }

    @OptIn(DelicateCoroutinesApi::class)
    override fun signAsync(payload: dynamic, keyID: String?) = GlobalScope.promise {
        sign(Json.parseToJsonElement(JSON.stringify(payload)).jsonObject, keyID)
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun verifyAsync(jwt: String) = GlobalScope.promise {
        verify(jwt)
    }
}
