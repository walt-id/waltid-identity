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
import id.walt.crypto.utils.JsonUtils.toJsonObject
import id.walt.oid4vc.data.ClientIdScheme
import id.walt.sdjwt.JWTCryptoProvider
import id.walt.sdjwt.JwtVerificationResult
import id.walt.verifier.config.OIDCVerifierServiceConfig
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import id.walt.did.dids.document.DidWebDocument

import java.io.File
import java.io.FileReader
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
object RequestSigningCryptoProvider : JWTCryptoProvider {

    fun getSigningDid() = ConfigManager.getConfig<OIDCVerifierServiceConfig>().requestSigningDid ?: throw IllegalArgumentException("Missing requestSigningDid from config file")

    fun getSigningDidDocument() = DidWebDocument(getSigningDid(), getSigningKey(ClientIdScheme.Did).keyID, getSigningKey(ClientIdScheme.Did).toJSONObject().toJsonObject())

    fun getSigningKey(clientIdScheme: ClientIdScheme? = ClientIdScheme.RedirectUri ): ECKey =
        when (clientIdScheme) {
            ClientIdScheme.Did -> {
                ConfigManager.getConfig<OIDCVerifierServiceConfig>().requestSigningJwkFile?.let {
                    runBlocking {
                        if (File(it).exists()) {
                            val jwkJson = File(it).readText()
                            ECKey.parse(jwkJson)
                        }
                        else
                            null
                    }
                } ?: throw IllegalArgumentException("Missing requestSigningJwkKey from config file")
            }
            else -> {
                ConfigManager.getConfig<OIDCVerifierServiceConfig>().requestSigningKeyFile?.let {
                    runBlocking {
                        if (File(it).exists())
                            ECKey.Builder(Curve.P_256, (ECKey.parseFromPEMEncodedObjects(FileReader(it).readText()).toECKey().toECPublicKey()))
                                .privateKey(ECKey.parseFromPEMEncodedObjects(FileReader(it).readText()).toECKey().toECPrivateKey())
                                .keyID(Uuid.random().toString())
                                .build()
                        else
                            null
                    }
                } ?: ECKeyGenerator(Curve.P_256).keyUse(KeyUse.SIGNATURE).keyID(Uuid.random().toString()).generate()
            }  }

    private val certificateChain: String? = ConfigManager.getConfig<OIDCVerifierServiceConfig>().requestSigningCertFile?.let {
        runBlocking {
            if (File(it).exists()) FileReader(it).readText() else null
        }
    }

    override fun sign(payload: JsonObject, keyID: String?, typ: String, headers: Map<String, Any>): String {

        //1. get clientIdScheme from payload
        val clientIdScheme = payload["client_id_scheme"]?.jsonPrimitive?.content

        //2. get signingKey for the clientIdScheme
         val signingKey = getSigningKey(ClientIdScheme.fromValue(clientIdScheme!!) ?: throw IllegalArgumentException("Client Id Scheme $clientIdScheme not found"))

        //3. Set Headers
         val headerKid = when (ClientIdScheme.fromValue(clientIdScheme) ) {
            ClientIdScheme.Did -> getSigningDidDocument().verificationMethod!!.first().id
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


/*
dids

{
    "content": {
        "@context": [
            "https://www.w3.org/ns/did/v1",
            "https://w3id.org/security/suites/jws-2020/v1"
        ],
        "id": "did:web:25c6-2a02-85f-e467-8a09-8d2f-b844-f06c-47b9.ngrok-free.app:openid4vc:did",
        "verificationMethod": [
            {
                "id": "did:web:25c6-2a02-85f-e467-8a09-8d2f-b844-f06c-47b9.ngrok-free.app:openid4vc:did#iTgtb1glgUylLXBDTpW1_qh1qhoWAQmW3elX3gKr9Zg",
                "type": "JsonWebKey2020",
                "controller": "did:web:25c6-2a02-85f-e467-8a09-8d2f-b844-f06c-47b9.ngrok-free.app:openid4vc:did",
                "publicKeyJwk": {
                    "kty": "OKP",
                    "crv": "Ed25519",
                    "kid": "iTgtb1glgUylLXBDTpW1_qh1qhoWAQmW3elX3gKr9Zg",
                    "x": "-HZbaOuDavj5ZEvS0Ui2yioAy0_PbrS7rBuQFwTXEOg"
                }
            }
        ],
        "assertionMethod": [
            "did:web:25c6-2a02-85f-e467-8a09-8d2f-b844-f06c-47b9.ngrok-free.app:openid4vc:did#iTgtb1glgUylLXBDTpW1_qh1qhoWAQmW3elX3gKr9Zg"
        ],
        "authentication": [
            "did:web:25c6-2a02-85f-e467-8a09-8d2f-b844-f06c-47b9.ngrok-free.app:openid4vc:did#iTgtb1glgUylLXBDTpW1_qh1qhoWAQmW3elX3gKr9Zg"
        ],
        "capabilityInvocation": [
            "did:web:25c6-2a02-85f-e467-8a09-8d2f-b844-f06c-47b9.ngrok-free.app:openid4vc:did#iTgtb1glgUylLXBDTpW1_qh1qhoWAQmW3elX3gKr9Zg"
        ],
        "capabilityDelegation": [
            "did:web:25c6-2a02-85f-e467-8a09-8d2f-b844-f06c-47b9.ngrok-free.app:openid4vc:did#iTgtb1glgUylLXBDTpW1_qh1qhoWAQmW3elX3gKr9Zg"
        ],
        "keyAgreement": [
            "did:web:25c6-2a02-85f-e467-8a09-8d2f-b844-f06c-47b9.ngrok-free.app:openid4vc:did#iTgtb1glgUylLXBDTpW1_qh1qhoWAQmW3elX3gKr9Zg"
        ]
    }
}

{
    "kty": "OKP",
    "d": "vHuh7V2GzF1yhRj1cO8rZCeMEc_V1R409c2X8_vE19A",
    "crv": "Ed25519",
    "kid": "iTgtb1glgUylLXBDTpW1_qh1qhoWAQmW3elX3gKr9Zg",
    "x": "-HZbaOuDavj5ZEvS0Ui2yioAy0_PbrS7rBuQFwTXEOg"
}
 */
