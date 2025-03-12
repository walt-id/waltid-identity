package id.walt.policies.policies

import id.walt.credentials.utils.VCFormat
import id.walt.credentials.utils.randomUUID
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
  
  private suspend fun resolveIssuerKeysFromSdJwt(sdJwt: SDJwtVC): Set<Key> {
    val kid = sdJwt.issuer ?: randomUUID()
    return if(DidUtils.isDidUrl(kid)) {
      DidService.resolveToKeys(kid).getOrThrow()
    } else {
      sdJwt.header.get("x5c")?.jsonArray?.last()?.let { x5c ->
        val key = JWKKey.importPEM(x5c.jsonPrimitive.content).getOrThrow().let { JWKKey(it.exportJWK(), kid) }
        return setOf(key)
      } ?: throw UnsupportedOperationException("Resolving issuer key from SD-JWT is only supported for issuer did in kid header and PEM cert in x5c header parameter")
    }
  }

  @JvmBlocking
  @JvmAsync
  @JsPromise
  @JsExport.Ignore
  override suspend fun verify(credential: String, args: Any?, context: Map<String, Any>): Result<Any> {
    val sdJwtVC = SDJwtVC.parse(credential)
    
    if(!sdJwtVC.isPresentation) {
      // Get all possible issuer keys from the DID document
      val issuerKeys = resolveIssuerKeysFromSdJwt(sdJwtVC)
      
      // Try to verify with each key
      var lastError: Throwable? = null
      for (issuerKey in issuerKeys) {
        val result = try {
          issuerKey.verifyJws(credential)
        } catch (e: Exception) {
          lastError = e
          Result.failure(e)
        }
        
        if (result.isSuccess) {
          return result
        }
      }
      
      // If we get here, all keys failed
      return Result.failure(lastError ?: VerificationException("Verification failed with all keys from the DID document"))
    } else {
      // For presentations, we'll still use the first key for now but create a map of all possible issuer keys
      val issuerKeys = resolveIssuerKeysFromSdJwt(sdJwtVC)
      val issuerKey = issuerKeys.firstOrNull() ?: 
        return Result.failure(VerificationException("No issuer keys found in the DID document"))
      
      val holderKey = JWKKey.importJWK(sdJwtVC.holderKeyJWK.toString()).getOrThrow()
      
      // Create a map of all possible issuer keys by their key IDs
      val keyMap = mutableMapOf<String, Key>()
      issuerKeys.forEach { key ->
        keyMap[key.getKeyId()] = key
      }
      // Add the default key ID mapping
      keyMap[sdJwtVC.keyID ?: issuerKey.getKeyId()] = issuerKey
      // Add the holder key
      keyMap[holderKey.getKeyId()] = holderKey
      
      return sdJwtVC.verifyVC(
        JWTCryptoProviderManager.getDefaultJWTCryptoProvider(keyMap),
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
