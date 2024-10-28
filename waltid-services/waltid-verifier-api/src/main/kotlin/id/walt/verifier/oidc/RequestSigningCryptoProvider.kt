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
import id.walt.did.dids.document.DidDocument
import id.walt.oid4vc.data.ClientIdScheme
import id.walt.sdjwt.JWTCryptoProvider
import id.walt.sdjwt.JwtVerificationResult
import id.walt.verifier.config.OIDCVerifierServiceConfig
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import java.io.File
import java.io.FileReader
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
object RequestSigningCryptoProvider : JWTCryptoProvider {

    fun getSigningDid() = getSigningDidDocument()["content"]!!.jsonObject["id"]?.jsonPrimitive?.content ?: throw IllegalStateException("No id in diddocument file")

    fun getSigningDidDocument() : DidDocument {
        return runBlocking {
            val jsonString =
                File(ConfigManager.getConfig<OIDCVerifierServiceConfig>().requestSigningDidDocumentFile!!).readText()
            val didDocumentJson = Json.parseToJsonElement(jsonString).jsonObject
            DidDocument(didDocumentJson)
        }
    }

    fun getSigningKeyForDid(): ECKey {
        return getKeyFromFile(ConfigManager.getConfig<OIDCVerifierServiceConfig>().requestSigningKeyForDidFile) ?: throw IllegalStateException("requestSigningKeyForDidFile is missing/incorrect")
    }

    fun getSigningKey(): ECKey {
        return getKeyFromFile(ConfigManager.getConfig<OIDCVerifierServiceConfig>().requestSigningKeyFile) ?: randomGeneratedSigningKey
    }

    private val randomGeneratedSigningKey: ECKey = ECKeyGenerator(Curve.P_256).keyUse(KeyUse.SIGNATURE).keyID(Uuid.random().toString()).generate()

    private fun parseKeyFromFileContent(fileContent: String): ECKey {
        return try {
            // Attempt to parse as JWK (JSON format)
            ECKey.parse(fileContent)
        } catch (e: Exception) {
            // If JWK parsing fails, fall back to PEM
            val ecKey = ECKey.parseFromPEMEncodedObjects(fileContent).toECKey()
            ECKey.Builder(Curve.P_256, ecKey.toECPublicKey())
                .privateKey(ecKey.toECPrivateKey())
                .keyID(Uuid.random().toString())
                .build()
        }
    }

    private fun getKeyFromFile(filePath: String?): ECKey? {
        return filePath?.let { path ->
            runBlocking {
                val file = File(path)
                if (file.exists()) {
                    val fileContent = file.readText()
                    parseKeyFromFileContent(fileContent)  // Attempt to parse JWK or PEM
                } else {
                    null
                }
            }
        }
    }

    private val certificateChain: String? = ConfigManager.getConfig<OIDCVerifierServiceConfig>().requestSigningCertFile?.let {
        runBlocking {
            if (File(it).exists()) FileReader(it).readText() else null
        }
    }

    override fun sign(payload: JsonObject, keyID: String?, typ: String, headers: Map<String, Any>): String {

        //1. get clientIdScheme from payload
        val clientIdScheme = ClientIdScheme.fromValue(payload["client_id_scheme"]?.jsonPrimitive?.content ?: throw IllegalArgumentException("Client Id Scheme not found"))

        //2. get signingKey for the clientIdScheme
        val signingKey = when (clientIdScheme) {
            ClientIdScheme.Did -> getSigningKeyForDid()
            else -> getSigningKey()
        }

        //3. Set Headers
         val headerKid = when (clientIdScheme) {
            ClientIdScheme.Did -> getSigningDidDocument()["content"]!!.jsonObject["verificationMethod"]!!.jsonArray[0].jsonObject["id"]?.jsonPrimitive?.content
            else -> signingKey.keyID
         }

        return SignedJWT(
            JWSHeader.Builder(JWSAlgorithm.ES256).keyID(headerKid).type(JOSEObjectType.JWT).customParams(headers).also {
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
