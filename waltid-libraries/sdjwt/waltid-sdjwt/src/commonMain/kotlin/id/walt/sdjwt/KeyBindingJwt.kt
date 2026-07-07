package id.walt.sdjwt

import id.walt.sdjwt.utils.Base64Utils.encodeToBase64Url
import korlibs.encoding.ASCII
import kotlinx.serialization.json.*
import org.kotlincrypto.hash.sha2.SHA256
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.time.Clock

@Suppress("NON_EXPORTABLE_TYPE")
@OptIn(ExperimentalJsExport::class)
class KeyBindingJwt(jwt: String, header: JsonObject, payload: SDPayload) : SDJwt(jwt, header, payload) {

    val issuedAt
        get() = fullPayload["iat"]!!.jsonPrimitive.long
    val audience
        get() = fullPayload["aud"]!!.jsonPrimitive.content
    val nonce
        get() = fullPayload["nonce"]!!.jsonPrimitive.content
    val sdHash
        get() = fullPayload["sd_hash"]!!.jsonPrimitive.content

    /**
     * Verify that this key-binding JWT binds the presented SD-JWT to the expected audience and nonce.
     * @param jwtCryptoProvider JWT crypto provider that verifies this key-binding JWT signature
     * @param reqAudience Expected `aud` claim value
     * @param reqNonce Expected `nonce` claim value
     * @param sdJwt Presented SD-JWT without the key-binding JWT included in the hash input
     * @param keyId Optional key ID to select the verification key, if required by the crypto provider
     */
    fun verifyKB(
        jwtCryptoProvider: JWTCryptoProvider,
        reqAudience: String,
        reqNonce: String,
        sdJwt: SDJwt,
        keyId: String? = null
    ): Boolean {
        return type == KB_JWT_TYPE && audience == reqAudience && nonce == reqNonce && sdJwt.isPresentation &&
                getSdHash(sdJwt.toString(formatForPresentation = true, withKBJwt = false)) == sdHash &&
                verify(jwtCryptoProvider, keyId).verified
    }

    /**
     * Verify that this key-binding JWT binds the presented SD-JWT to the expected audience and nonce.
     * @param jwtCryptoProvider Async JWT crypto provider that verifies this key-binding JWT signature
     * @param reqAudience Expected `aud` claim value
     * @param reqNonce Expected `nonce` claim value
     * @param sdJwt Presented SD-JWT without the key-binding JWT included in the hash input
     */
    @JsExport.Ignore
    suspend fun verifyKBAsync(
        jwtCryptoProvider: AsyncJWTCryptoProvider,
        reqAudience: String,
        reqNonce: String,
        sdJwt: SDJwt,
    ): Boolean {
        return type == KB_JWT_TYPE && audience == reqAudience && nonce == reqNonce && sdJwt.isPresentation &&
                getSdHash(sdJwt.toString(formatForPresentation = true, withKBJwt = false)) == sdHash &&
                verifyAsync(jwtCryptoProvider).verified
    }

    companion object {
        const val KB_JWT_TYPE = "kb+jwt"

        private val sha256 = SHA256()

        fun parse(kbJwt: String): KeyBindingJwt {
            return SDJwt.parse(kbJwt).let { KeyBindingJwt(it.jwt, it.header, SDPayload(it.fullPayload)) }
        }

        /**
         * Sign key binding JWT using provided properties, key and crypto provider, and return as KeyBindingJwt object
         * @param presentedSdJwt  Presented SD-JWT without holder key binding, in presentation format
         * @param audience  Audience to set in required "aud" property of the jwt body
         * @param nonce   Nonce value for the required "nonce" property of the jwt body
         * @param cryptoProvider  Crypto provider to sign the JWT with the given holder key
         * @param keyId Optional key ID of the key to be used for signature, if required by crypto provider
         */
        fun sign(
            presentedSdJwt: String,
            audience: String,
            nonce: String,
            cryptoProvider: JWTCryptoProvider,
            keyId: String? = null
        ): KeyBindingJwt = parse(
            cryptoProvider.sign(
                payload = buildJsonObject {
                    put("iat", JsonPrimitive(Clock.System.now().epochSeconds))
                    put("aud", audience)
                    put("nonce", nonce)
                    put("sd_hash", getSdHash(presentedSdJwt))
                },
                keyID = keyId,
                typ = KB_JWT_TYPE
            )
        )

        fun getSdHash(presentedSdJwt: String) = sha256.digest(ASCII.encode(presentedSdJwt)).encodeToBase64Url()
    }
}
