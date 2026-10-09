package id.walt.openid4vci.proofs

import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.keys.*
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.crypto2.serialization.BinaryData
import id.walt.openid4vci.*
import id.walt.openid4vci.core.buildOAuth2Provider
import id.walt.openid4vci.handlers.endpoints.credential.*
import id.walt.openid4vci.metadata.issuer.*
import id.walt.openid4vci.proofs.attestation.*
import id.walt.openid4vci.requests.credential.DefaultCredentialRequest
import id.walt.openid4vci.responses.credential.CredentialResponseResult
import id.walt.sdjwt.SDJwtVC
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*
import kotlin.time.Clock

class LegacyKeyAttestationIssuanceTest {
    @Test
    fun `real legacy keys work with both attestation proof types and signing APIs`() = runTest {
        val attester = JWKKey.generate(KeyType.secp256r1)
        val holder = JWKKey.generate(KeyType.secp256r1)
        val issuerKey = JWKKey.generate(KeyType.secp256r1)
        val crypto2IssuerKey = CryptoRuntime(defaultSoftwareKeyProviders()).restore(
            EncodedKey.Jwk(BinaryData(issuerKey.exportJWK().encodeToByteArray()), privateMaterial = true)
                .toStoredSoftwareKey(KeyId("issuer"), setOf(KeyUsage.SIGN, KeyUsage.VERIFY)),
        )
        val issuer = "https://issuer.example"
        val now = Clock.System.now()
        val attestation = attester.signJws(buildJsonObject {
            put("iat", now.epochSeconds)
            put("exp", now.epochSeconds + 300)
            put("nonce", "nonce")
            put("attested_keys", JsonArray(listOf(JsonObject(holder.getPublicKey().exportJWKObject() - "kid"))))
        }.toString().encodeToByteArray(), mapOf("typ" to JsonPrimitive("key-attestation+jwt")))
        val nested = holder.signJws(buildJsonObject {
            put("aud", issuer); put("iat", now.epochSeconds); put("nonce", "nonce")
        }.toString().encodeToByteArray(), mapOf(
            "typ" to JsonPrimitive("openid4vci-proof+jwt"),
            "jwk" to holder.getPublicKey().exportJWKObject(),
            "key_attestation" to JsonPrimitive(attestation),
        ))
        val nestedKid = holder.signJws(buildJsonObject {
            put("aud", issuer); put("iat", now.epochSeconds); put("nonce", "nonce")
        }.toString().encodeToByteArray(), mapOf(
            "typ" to JsonPrimitive("openid4vci-proof+jwt"),
            "kid" to JsonPrimitive("0"),
            "key_attestation" to JsonPrimitive(attestation),
        ))
        val nonceService = object : CredentialNonceService {
            override suspend fun issue(binding: CredentialNonceBinding) = IssuedCredentialNonce("nonce")
            override suspend fun validate(nonce: String, binding: CredentialNonceBinding) =
                if (nonce == "nonce") CredentialNonceValidationResult.VALID else CredentialNonceValidationResult.INVALID
        }
        val context = CredentialProofValidationContext(
            issuer,
            nonceValidation = CredentialNonceValidationContext(
                nonceService, CredentialNonceBinding(issuer, "$issuer/credential", "$issuer/nonce"),
            ),
            keyAttestation = KeyAttestationConfig(
                KeyAttestationVerificationMethod.StaticJwk(attester.getPublicKey().exportJWKObject()),
            ).toVerificationOptions(),
        )
        val configuration = CredentialConfiguration(
            CredentialFormat.SD_JWT_VC, vct = "identity",
            cryptographicBindingMethodsSupported = setOf(CryptographicBindingMethod.Jwk),
            proofTypesSupported = mapOf(
                ProofType.JWT.value to ProofTypeMetadata(setOf("ES256"), KeyAttestationsRequired()),
                ProofType.ATTESTATION.value to ProofTypeMetadata(setOf("ES256")),
            ),
        )
        val provider = buildOAuth2Provider(createTestConfig())
        for (proofs in listOf(Proofs(jwt = listOf(nested)), Proofs(jwt = listOf(nestedKid)), Proofs(attestation = listOf(attestation)))) {
            val request = DefaultCredentialRequest(
                client = DefaultClient("client", emptyList(), emptySet(), emptySet()),
                credentialIdentifier = null, credentialConfigurationId = "identity",
                proofs = proofs, credentialResponseEncryption = null,
            )
            for (crypto2 in listOf(false, true)) {
                val inputs = CredentialIssuanceInputProvider { count ->
                    assertEquals(1, count)
                    List(count) { CredentialIssuanceInput(buildJsonObject { put("name", "Alice") }) }
                }
                val response = if (crypto2) provider.createCredentialResponse(
                    request, configuration, Crypto2CredentialSigningKey.select(crypto2IssuerKey, configuration),
                    issuer, inputs, proofValidationContext = context,
                ) else provider.createCredentialResponse(
                    request, configuration, issuerKey, issuer, inputs, proofValidationContext = context,
                )
                val credential = assertIs<CredentialResponseResult.Success>(response).response.credentials!!.single()
                    .credential.jsonPrimitive.content
                val jwt = credential.substringBefore('~')
                assertTrue(issuerKey.verifyJws(jwt).isSuccess)
                CompactJws.verify(jwt, crypto2IssuerKey, JwsAlgorithm.ES256)
                val boundKey = JWKKey.importJWK(SDJwtVC.parse(credential).holderKeyJWK!!.toString()).getOrThrow()
                assertEquals(holder.getThumbprint(), boundKey.getThumbprint())
            }
        }
    }
}
