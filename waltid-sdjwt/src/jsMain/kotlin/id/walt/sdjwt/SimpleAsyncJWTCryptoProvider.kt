package id.walt.sdjwt

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.promise
import kotlinx.serialization.json.*
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

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
    private val options: dynamic
) : JSAsyncJWTCryptoProvider {
    @JsExport.Ignore
    override suspend fun sign(payload: JsonObject, keyID: String?): String = suspendCoroutine { continuation ->
        console.log("SIGNING", payload.toString())
        jose.SignJWT(JSON.parse(payload.toString())).setProtectedHeader(buildJsonObject {
            put("alg", algorithm)
            put("typ", "JWT")
            //put("cty", "credential-claims-set+json")
            //put("typ", "vc+sd-jwt")
            keyID?.also { put("kid", it) }
        }.let { JSON.parse(it.toString()) }).sign(keyParam, options).then({
            console.log("SIGNED")
            continuation.resume(it)
        }, {
            console.log("ERROR SIGNING", it.message)
        })
    }

    @JsExport.Ignore
    override suspend fun verify(jwt: String): JwtVerificationResult = suspendCoroutine { continuation ->
        console.log("Verifying JWT: $jwt")
        jose.jwtVerify(jwt, keyParam, options ?: js("{}")).then(
            {
                console.log("Verified.")
                continuation.resume(JwtVerificationResult(true))
            },
            {
                console.log("Verification failed: ${it.message}")
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
