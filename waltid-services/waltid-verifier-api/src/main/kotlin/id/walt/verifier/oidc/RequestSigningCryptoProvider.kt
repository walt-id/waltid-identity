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
import id.walt.commons.config.ConfigManager
import id.walt.crypto.utils.UuidUtils.randomUUIDString
import id.walt.sdjwt.JWTCryptoProvider
import id.walt.sdjwt.JwtVerificationResult
import id.walt.verifier.config.OIDCVerifierServiceConfig
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import java.io.File
import java.io.FileReader

object RequestSigningCryptoProvider : JWTCryptoProvider {
    val signingKey: ECKey = ConfigManager.getConfig<OIDCVerifierServiceConfig>().requestSigningKeyFile?.let {
        runBlocking {
            if (File(it).exists())
                ECKey.Builder(
                    Curve.P_256,
                    (ECKey.parseFromPEMEncodedObjects(FileReader(it).readText()).toECKey().toECPublicKey())
                )
                    .privateKey(ECKey.parseFromPEMEncodedObjects(FileReader(it).readText()).toECKey().toECPrivateKey())
                    .keyID(randomUUIDString())
                    .build()
            else
                null
        }
    }
        ?: ECKeyGenerator(Curve.P_256).keyUse(KeyUse.SIGNATURE).keyID(randomUUIDString()).generate()

    val certificateChain: String? = ConfigManager.getConfig<OIDCVerifierServiceConfig>().requestSigningCertFile?.let {
        runBlocking {
            if (File(it).exists()) FileReader(it).readText() else null
        }
    }

    override fun sign(payload: JsonObject, keyID: String?, typ: String, headers: Map<String, Any>): String {
        return SignedJWT(
            JWSHeader.Builder(JWSAlgorithm.ES256).keyID(signingKey.keyID).type(JOSEObjectType.JWT).customParams(headers)
                .also {
                    if (certificateChain != null) {
                        it.x509CertChain(
                            X509CertChainUtils.parse(certificateChain).map { Base64.encode(it.encoded) }
                        )
                    }
                }.build(),
            JWTClaimsSet.parse(payload.toString())
        ).also { it.sign(ECDSASigner(signingKey)) }.serialize()
    }

    override fun verify(jwt: String, keyID: String?): JwtVerificationResult {
        TODO("Not yet implemented")
    }
}
