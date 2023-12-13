package id.walt.mdoc

import cbor.Cbor
import id.walt.mdoc.cose.AsyncCOSECryptoProvider
import id.walt.mdoc.cose.COSESign1
import id.walt.mdoc.cose.COSESign1Serializer
import korlibs.crypto.encoding.Hex
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.await
import kotlinx.coroutines.promise
import kotlin.js.Promise

@JsModule("cose-js")
@JsNonModule
external abstract class CoseJs {
  object sign {
    fun create(headers: dynamic, payload: dynamic, signers: dynamic): Promise<ByteArray>
  }
}

/**
 * Simple COSE crypto key info object for a given private and public key pair. For verification only, private key can be omitted.
 * @param keyID ID of this key info object
 * @param algorithmID Signing algorithm ID, e.g.: ECDSA_256
 * @param publicKey Public key for COSE verification
 * @param privateKey Private key for COSE signing
 * @param x5Chain certificate chain, including intermediate and signing key certificates, but excluding root CA certificate!
 * @param trustedRootCA enforce trusted root CA, if not publicly known, for certificate path validation
 */
data class COSECryptoProviderKeyInfo(
  val keyID: String,
  val algorithmID: String,
  val key: dynamic
)

class SimpleAsyncCOSECryptoProvider(keys: List<COSECryptoProviderKeyInfo>): JSAsyncCOSECryptoProvider {
  private val keyMap: Map<String, COSECryptoProviderKeyInfo>
  init {
    keyMap = keys.associateBy { it.keyID }
  }
  override suspend fun sign1(payload: ByteArray, keyID: String?): COSESign1 {
    val keyInfo = keyMap[keyID] ?: throw Exception("No key ID given, or key with given ID not found")
    val headers: dynamic = object {}
    val p: dynamic = object {}
    val u: dynamic = object {}
    p["alg"] = keyInfo.algorithmID
    u["kid"] = keyID
    headers["p"] = p
    headers["u"] = u
    val signer:dynamic = object {}//mapOf("key" to keyInfo.key)
    signer["key"] = keyInfo.key
    val buf = CoseJs.sign.create(headers, payload, signer).await()
    console.log("Signed message: " + Hex.encode(buf));
    return Cbor.decodeFromByteArray(COSESign1Serializer, buf)
  }

  override fun sign1Async(payload: dynamic, keyID: String?) = GlobalScope.promise {
    sign1(JSON.stringify(payload).encodeToByteArray(), keyID)
  }
}