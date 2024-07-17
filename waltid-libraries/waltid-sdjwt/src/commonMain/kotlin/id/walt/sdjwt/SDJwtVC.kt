package id.walt.sdjwt
import kotlinx.datetime.Clock
import kotlinx.serialization.json.*

class SDJwtVC(sdJwt: SDJwt): SDJwt(sdJwt.jwt, sdJwt.header, sdJwt.sdPayload, sdJwt.keyBindingJwt, sdJwt.isPresentation) {

  val cnfObject: JsonObject? = undisclosedPayload["cnf"]?.jsonObject
  val holderDid: String? = cnfObject?.get("kid")?.jsonPrimitive?.content
  val holderKeyJWK: JsonObject? = cnfObject?.get("jwk")?.jsonObject
  val issuer = undisclosedPayload["iss"]?.jsonPrimitive?.content
  val notBefore = undisclosedPayload["nbf"]?.jsonPrimitive?.long
  val expiration = undisclosedPayload["exp"]?.jsonPrimitive?.long
  val vct = undisclosedPayload["vct"]?.jsonPrimitive?.content
  val status = undisclosedPayload["status"]?.jsonPrimitive?.content

  protected fun verifyHolderKeyBinding(jwtCryptoProvider: JWTCryptoProvider, requiresHolderKeyBinding: Boolean,
                                       audience: String? = null, nonce: String? = null): Boolean {
    return if(!holderDid.isNullOrEmpty()) TODO("Holder DID verification not yet supported")
    else if(holderKeyJWK != null) {
      isPresentation && keyBindingJwt != null && !audience.isNullOrEmpty() && !nonce.isNullOrEmpty() &&
          keyBindingJwt.verifyKB(jwtCryptoProvider, audience, nonce, this, holderKeyJWK["kid"]?.jsonPrimitive?.content)
    } else
      !requiresHolderKeyBinding
  }

  // TODO: issuer DID/key needs to be resolved outside, to initialize the required crypto provider. Needs improvement!
  // TODO: should resolve issuer key from "iss" property
  // TODO: make use of Key interface from waltid-crypto lib instead or also?
  fun verifyVC(jwtCryptoProvider: JWTCryptoProvider, requiresHolderKeyBinding: Boolean,
               audience: String? = null, nonce: String? = null): VCVerificationResult {
    var message: String = ""
    return VCVerificationResult(
      this, verify(jwtCryptoProvider),
(notBefore?.let { Clock.System.now().epochSeconds > it } ?: true).also {
        if(!it) message = "$message, VC is not valid before $notBefore"
      } &&
      (expiration?.let { Clock.System.now().epochSeconds < it } ?: true).also {
        if(!it) message = "$message, VC is not valid after $expiration"
      } &&
      !vct.isNullOrEmpty().also {
        if(!it) message = "$message, VC has no verifiable credential type property (vct)"
      } &&
      verifyHolderKeyBinding(jwtCryptoProvider, requiresHolderKeyBinding, audience, nonce).also {
        if(!it) message = "$message, holder key binding could not be verified"
      }
    )
  }

  companion object {

    fun parse(sdJwt: String) = SDJwtVC(SDJwt.parse(sdJwt))

    /**
     * Parse SD-JWT VC from a token string and verify it
     * @return SD-JWT VC verification result, with parsed SD-JWT VC
     * @throws Exception if SD-JWT VC cannot be parsed
     */
    fun verifyAndParse(sdJwtVC: String, jwtCryptoProvider: JWTCryptoProvider, requiresHolderKeyBinding: Boolean,
                       audience: String? = null, nonce: String? = null): VCVerificationResult {
      val parsedVC = parse(sdJwtVC)
      return parsedVC.verifyVC(jwtCryptoProvider, requiresHolderKeyBinding, audience, nonce)
    }

    fun sign(
      sdPayload: SDPayload,
      jwtCryptoProvider: JWTCryptoProvider,
      issuerDid: String,
      holderDid: String,
      issuerKeyId: String? = null,
      vct: String, nbf: Long? = null, exp: Long? = null, status: String? = null,
      /** Set additional options in the JWT header */
      additionalJwtHeader: Map<String, String> = emptyMap()
    ): SDJwtVC = doSign(sdPayload, jwtCryptoProvider, issuerDid, buildJsonObject {
      put("kid", holderDid)
    }, issuerKeyId, vct, nbf, exp, status, additionalJwtHeader)

    fun sign(
      sdPayload: SDPayload,
      jwtCryptoProvider: JWTCryptoProvider,
      issuerDid: String,
      holderKeyJWK: JsonObject,
      issuerKeyId: String? = null,
      vct: String, nbf: Long? = null, exp: Long? = null, status: String? = null,
      /** Set additional options in the JWT header */
      additionalJwtHeader: Map<String, String> = emptyMap()
    ): SDJwtVC = doSign(sdPayload, jwtCryptoProvider, issuerDid, buildJsonObject {
      put("jwk", holderKeyJWK)
    }, issuerKeyId, vct, nbf, exp, status, additionalJwtHeader)

    private fun doSign(
      sdPayload: SDPayload,
      jwtCryptoProvider: JWTCryptoProvider,
      issuerDid: String,
      cnf: JsonObject,
      issuerKeyId: String? = null,
      vct: String, nbf: Long? = null, exp: Long? = null, status: String? = null,
      /** Set additional options in the JWT header */
      additionalJwtHeader: Map<String, String> = emptyMap()
    ): SDJwtVC {
      val undisclosedPayload = sdPayload.undisclosedPayload.toMutableMap().apply {
        put("iss", JsonPrimitive(issuerDid))
        put("cnf", cnf)
        put("vct", JsonPrimitive(vct))
        nbf?.let { put("nbf", JsonPrimitive(it)) }
        exp?.let { put("exp", JsonPrimitive(it)) }
        status?.let { put("status", JsonPrimitive(it)) }
      }.let { JsonObject(it) }

      val sdPayload = SDPayload(undisclosedPayload, sdPayload.digestedDisclosures)
      return SDJwtVC(sign(sdPayload, jwtCryptoProvider, issuerKeyId, typ = "vc+sd-jwt", additionalJwtHeader))
    }
  }
}
