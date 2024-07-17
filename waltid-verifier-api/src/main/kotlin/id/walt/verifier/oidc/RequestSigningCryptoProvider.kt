package id.walt.verifier.oidc

import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jose.util.Base64
import com.nimbusds.jose.util.X509CertChainUtils
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import id.walt.sdjwt.JWTCryptoProvider
import id.walt.sdjwt.JwtVerificationResult
import id.walt.verifier.base.config.ConfigManager
import id.walt.verifier.base.config.OIDCVerifierServiceConfig
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import java.io.FileReader

object RequestSigningCryptoProvider: JWTCryptoProvider {
  val signingKey: ECKey? = ConfigManager.getConfig<OIDCVerifierServiceConfig>().requestSigningKeyFile?.let { runBlocking { ECKey.parseFromPEMEncodedObjects(FileReader(it).readText()).toECKey() } }
  val certificateChain: String? = ConfigManager.getConfig<OIDCVerifierServiceConfig>().requestSigningCertFile?.let { FileReader(it).readText() }

  override fun sign(payload: JsonObject, keyID: String?, typ: String, headers: Map<String, Any>): String {
    if(signingKey == null) throw UnsupportedOperationException("No request signing key was configured, or key could not be loaded")
    if(!signingKey.isPrivate) throw UnsupportedOperationException("Request signing key is no private key")
    if(certificateChain.isNullOrEmpty()) throw UnsupportedOperationException("No certificate chain for request signing key could be loaded")

    return SignedJWT(
      JWSHeader.Builder(JWSAlgorithm.ES256).keyID(signingKey.keyID).type(JOSEObjectType.JWT).x509CertChain(
        X509CertChainUtils.parse(certificateChain).map { Base64.encode(it.encoded) }
      ).customParams(headers).build(),
      JWTClaimsSet.parse(payload.toString())
    ).also { it.sign(ECDSASigner(signingKey)) }.serialize()
  }

  override fun verify(jwt: String, keyID: String?): JwtVerificationResult {
    TODO("Not yet implemented")
  }
}
