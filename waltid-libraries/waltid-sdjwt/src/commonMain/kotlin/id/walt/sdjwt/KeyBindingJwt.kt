package id.walt.sdjwt

import korlibs.crypto.SHA256
import korlibs.crypto.encoding.ASCII
import kotlinx.datetime.Clock
import kotlinx.serialization.json.*
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@Suppress("NON_EXPORTABLE_TYPE")
@OptIn(ExperimentalJsExport::class)
@JsExport
class KeyBindingJwt(jwt: String, header: JsonObject, payload: SDPayload): SDJwt(jwt, header, payload) {

  val issuedAt
    get() = fullPayload["iat"]!!.jsonPrimitive.long
  val audience
    get() = fullPayload["aud"]!!.jsonPrimitive.content
  val nonce
    get() = fullPayload["nonce"]!!.jsonPrimitive.content
  val sdHash
    get() = fullPayload["sd_hash"]!!.jsonPrimitive.content

  // TODO: make use of Key interface from waltid-crypto lib instead or also?
  fun verifyKB(jwtCryptoProvider: JWTCryptoProvider, reqAudience: String, reqNonce: String, sdJwt: SDJwt, keyId: String? = null): Boolean {
    return type == KB_JWT_TYPE && audience == reqAudience && nonce == reqNonce && sdJwt.isPresentation &&
        getSdHash(sdJwt.toString(formatForPresentation = true, withKBJwt = false)) == sdHash &&
        verify(jwtCryptoProvider, keyId).verified
  }

  companion object {
    const val KB_JWT_TYPE = "kb+jwt"

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
    fun sign(presentedSdJwt: String, audience: String, nonce: String, cryptoProvider: JWTCryptoProvider, keyId: String? = null): KeyBindingJwt = parse(
      cryptoProvider.sign(payload = buildJsonObject {
        put("iat", JsonPrimitive(Clock.System.now().epochSeconds))
        put("aud", audience)
        put("nonce", nonce)
        put("sd_hash", getSdHash(presentedSdJwt))
      }, keyID = keyId, typ = KB_JWT_TYPE)
    )

    fun getSdHash(presentedSdJwt: String) = SHA256.digest(ASCII.encode(presentedSdJwt)).base64Url
  }
}
