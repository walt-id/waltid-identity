package id.walt.policies.policies

import id.walt.w3c.utils.VCFormat
import id.walt.w3c.utils.randomUUID
import id.walt.crypto.exceptions.VerificationException
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.did.dids.DidService
import id.walt.did.dids.DidUtils
import id.walt.policies.JwtVerificationPolicy
import id.walt.sdjwt.JWTCryptoProvider
import id.walt.sdjwt.SDJwtVC
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import love.forte.plugin.suspendtrans.annotation.JsPromise
import love.forte.plugin.suspendtrans.annotation.JvmAsync
import love.forte.plugin.suspendtrans.annotation.JvmBlocking
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

expect object JWTCryptoProviderManager {
  fun getDefaultJWTCryptoProvider(keys: Map<String, Key>): JWTCryptoProvider
}

class SdJwtVCSignaturePolicy(): JwtVerificationPolicy() {
  override val name = "signature_sd-jwt-vc"
  override val description =
    "Checks a SD-JWT-VC credential by verifying its cryptographic signature using the key referenced by the DID in `iss`."
  override val supportedVCFormats = setOf(VCFormat.sd_jwt_vc)

  private suspend fun resolveIssuerKeyFromSdJwt(sdJwt: SDJwtVC): Key {
    val kid = sdJwt.issuer ?: randomUUID()
    return if(DidUtils.isDidUrl(kid)) {
      DidService.resolveToKey(kid).getOrThrow()
    } else {
      sdJwt.header.get("x5c")?.jsonArray?.last()?.let { x5c ->
        return JWKKey.importPEM(x5c.jsonPrimitive.content).getOrThrow().let { JWKKey(it.exportJWK(), kid) }
      } ?: throw UnsupportedOperationException("Resolving issuer key from SD-JWT is only supported for issuer did in kid header and PEM cert in x5c header parameter")
    }
  }

  @OptIn(ExperimentalJsExport::class)
  @JvmBlocking
  @JvmAsync
  @JsPromise
  @JsExport.Ignore
  override suspend fun verify(credential: String, args: Any?, context: Map<String, Any>): Result<Any> {
    val sdJwtVC = SDJwtVC.parse(credential)
    val issuerKey = resolveIssuerKeyFromSdJwt(sdJwtVC)
    if(!sdJwtVC.isPresentation)
      return issuerKey.verifyJws(credential)
    else {
      val holderKey = JWKKey.importJWK(sdJwtVC.holderKeyJWK.toString()).getOrThrow()
      return sdJwtVC.verifyVC(
        JWTCryptoProviderManager.getDefaultJWTCryptoProvider(mapOf(
          (sdJwtVC.keyID ?: issuerKey.getKeyId()) to issuerKey,
          holderKey.getKeyId() to holderKey)
        ),
        requiresHolderKeyBinding = true,
        context["clientId"]?.toString(),
        context["challenge"]?.toString()
      ).let {
        if(it.verified)
          Result.success(sdJwtVC.undisclosedPayload)
        else
          Result.failure(VerificationException("SD-JWT verification failed"))
      }
    }
  }

}
