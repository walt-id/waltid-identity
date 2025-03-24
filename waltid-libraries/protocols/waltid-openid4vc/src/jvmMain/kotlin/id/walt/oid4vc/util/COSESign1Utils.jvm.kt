package id.walt.oid4vc.util

import org.cose.java.AlgorithmID
import org.cose.java.OneKey
import cbor.Cbor
import com.nimbusds.jose.util.X509CertUtils
import com.upokecenter.cbor.CBORObject
import id.walt.crypto.utils.Base64Utils.base64UrlDecode
import id.walt.mdoc.COSECryptoProviderKeyInfo
import id.walt.mdoc.SimpleCOSECryptoProvider
import id.walt.mdoc.cose.COSESign1
import id.walt.mdoc.dataelement.ByteStringElement
import id.walt.mdoc.dataelement.ListElement
import id.walt.mdoc.dataelement.MapKey
import id.walt.oid4vc.data.ProofOfPossession
import id.walt.oid4vc.providers.TokenTarget
import kotlinx.serialization.decodeFromByteArray

actual object COSESign1Utils {
  actual fun verifyCOSESign1Signature(target: TokenTarget, token: String): Boolean {
    // May not be required anymore (removed from https://openid.net/specs/openid-4-verifiable-credential-issuance-1_0.html#cwt-proof-type)
    println("Verifying JWS: $token")
    println("JWS Verification: target: $target")
    val coseSign1 = Cbor.decodeFromByteArray<COSESign1>(token.base64UrlDecode())
    val keyInfo = extractHolderKey(coseSign1)
    val cryptoProvider = SimpleCOSECryptoProvider(listOf(keyInfo))
    return cryptoProvider.verify1(coseSign1, "pub-key")
  }

  fun extractHolderKey(coseSign1: COSESign1): COSECryptoProviderKeyInfo {
    val tokenHeader = coseSign1.decodeProtectedHeader()
    return if (tokenHeader.value.containsKey(MapKey(ProofOfPossession.CWTProofBuilder.HEADER_LABEL_COSE_KEY))) {
      val rawKey = (tokenHeader.value[MapKey(ProofOfPossession.CWTProofBuilder.HEADER_LABEL_COSE_KEY)] as ByteStringElement).value
      COSECryptoProviderKeyInfo(
        "pub-key", AlgorithmID.ECDSA_256,
        OneKey(CBORObject.DecodeFromBytes(rawKey)).AsPublicKey()
      )
    } else {
      val x5c = tokenHeader.value[MapKey(ProofOfPossession.CWTProofBuilder.HEADER_LABEL_X5CHAIN)]
      val x5Chain = when (x5c) {
        is ListElement -> x5c.value.map { X509CertUtils.parse((it as ByteStringElement).value) }
        else -> listOf(X509CertUtils.parse((x5c as ByteStringElement).value))
      }
      COSECryptoProviderKeyInfo(
        "pub-key", AlgorithmID.ECDSA_256,
        x5Chain.first().publicKey, x5Chain = x5Chain
      )
    }
  }
}
