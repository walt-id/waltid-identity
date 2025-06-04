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
        val x5c = sdJwt.header.get("x5c")?.jsonArray?.lastOrNull()
            ?: throw IllegalArgumentException("x5c header parameter is missing or empty.")
        JWKKey.importPEM(x5c.jsonPrimitive.content).getOrThrow().let { JWKKey(it.exportJWK(), kid) }
    }
  }
  
  private suspend fun resolveIssuerKeysFromSdJwt(sdJwt: SDJwtVC): Set<Key> {
    val kid = sdJwt.issuer ?: randomUUID()
    return if(DidUtils.isDidUrl(kid)) {
      DidService.resolveToKeys(kid).getOrThrow()
    } else {
        val x5c = sdJwt.header.get("x5c")?.jsonArray?.lastOrNull()
            ?: throw IllegalArgumentException("x5c header parameter is missing or empty.")
        val key = JWKKey.importPEM(x5c.jsonPrimitive.content).getOrThrow().let { JWKKey(it.exportJWK(), kid) }
        setOf(key)
    }
  }

  @OptIn(ExperimentalJsExport::class)
  @JvmBlocking
  @JvmAsync
  @JsPromise
  @JsExport.Ignore
  override suspend fun verify(credential: String, args: Any?, context: Map<String, Any>): Result<Any> {
    return runCatching {
      val sdJwtVC = SDJwtVC.parse(credential)
      
      if(!sdJwtVC.isPresentation) {
        // Get all possible issuer keys from the DID document
        val issuerKeys = resolveIssuerKeysFromSdJwt(sdJwtVC)
        
        if (issuerKeys.isEmpty()) {
          throw VerificationException("No issuer keys found in the DID document")
        }
        
        // Try to verify with each key
        val results = issuerKeys.map { issuerKey ->
          runCatching { issuerKey.verifyJws(credential) }
        }
        
        // Return the first successful result or the last error
        val successResult = results.firstOrNull { it.isSuccess }
        successResult?.getOrNull()
          ?: throw results.last().exceptionOrNull() 
            ?: VerificationException("Verification failed with all keys from the DID document")
        
      } else {
        // For presentations, get all possible issuer keys
        val issuerKeys = resolveIssuerKeysFromSdJwt(sdJwtVC)
        val issuerKey = issuerKeys.firstOrNull() 
          ?: throw VerificationException("No issuer keys found in the DID document")
        
        val holderKey = JWKKey.importJWK(sdJwtVC.holderKeyJWK.toString()).getOrThrow()
        
        // Create a map of all possible issuer keys by their key IDs
        val keyMap = issuerKeys.associateBy { it.getKeyId() }.toMutableMap()

        // Add the default key ID mapping
        keyMap[sdJwtVC.keyID ?: issuerKey.getKeyId()] = issuerKey
        // Add the holder key
        keyMap[holderKey.getKeyId()] = holderKey
        
        val verificationResult = sdJwtVC.verifyVC(
          JWTCryptoProviderManager.getDefaultJWTCryptoProvider(keyMap),
          requiresHolderKeyBinding = true,
          context["clientId"]?.toString(),
          context["challenge"]?.toString()
        )
        
        if (!verificationResult.verified) {
          throw VerificationException("SD-JWT verification failed")
        }
        
        sdJwtVC.undisclosedPayload
      }
    }
  }

}
