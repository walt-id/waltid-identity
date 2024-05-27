package id.walt.verifier.oidc

import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jose.jwk.KeyUse
import com.nimbusds.jose.jwk.gen.ECKeyGenerator
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
import kotlinx.uuid.UUID
import kotlinx.uuid.generateUUID
import java.io.FileReader

object RequestSigningCryptoProvider: JWTCryptoProvider {
  val signingKey: ECKey? = ConfigManager.getConfig<OIDCVerifierServiceConfig>().requestSigningKeyFile?.let { runBlocking { ECKey.parseFromPEMEncodedObjects(FileReader(it).readText()).toECKey() } }
  val certificateChain: String? = ConfigManager.getConfig<OIDCVerifierServiceConfig>().requestSigningCertFile?.let { FileReader(it).readText() }
  val defaultSigningKey = ECKeyGenerator(Curve.P_256).keyUse(KeyUse.SIGNATURE).keyID(UUID.generateUUID().toString()).generate()

  override fun sign(payload: JsonObject, keyID: String?, typ: String): String {
    val key = signingKey ?: defaultSigningKey
    return SignedJWT(
      JWSHeader.Builder(JWSAlgorithm.ES256).keyID(key.keyID).type(JOSEObjectType.JWT).also {
        if(certificateChain != null) {
          it.x509CertChain(
            X509CertChainUtils.parse(certificateChain).map { Base64.encode(it.encoded) }
          )
        }
      }.build(),
      JWTClaimsSet.parse(payload.toString())
    ).also { it.sign(ECDSASigner(key)) }.serialize()
  }

  override fun verify(jwt: String): JwtVerificationResult {
    TODO("Not yet implemented")
  }
}
