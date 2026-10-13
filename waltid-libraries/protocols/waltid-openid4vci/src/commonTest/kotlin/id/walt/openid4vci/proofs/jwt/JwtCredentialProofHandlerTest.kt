package id.walt.openid4vci.proofs.jwt

import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.jose.exportPublicJwkObject
import id.walt.crypto2.keys.*
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.openid4vci.CredentialFormat
import id.walt.openid4vci.CryptographicBindingMethod
import id.walt.openid4vci.errors.CredentialErrorCodes
import id.walt.openid4vci.metadata.issuer.CredentialConfiguration
import id.walt.openid4vci.metadata.issuer.ProofTypeMetadata
import id.walt.openid4vci.proofs.CredentialProofValidationContext
import id.walt.openid4vci.proofs.CredentialProofValidationException
import id.walt.openid4vci.proofs.ProofType
import id.walt.openid4vci.proofs.VerifiedJwtProof
import id.walt.openid4vci.proofs.attestation.KeyAttestationConfig
import id.walt.openid4vci.proofs.attestation.KeyAttestationVerificationMethod
import id.walt.openid4vci.proofs.attestation.toVerificationOptions
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.time.Instant

class JwtCredentialProofHandlerTest {
    @Test
    fun `plain JWT proof requires the issuer identifier as a string audience`() = runTest {
        checkAudienceValidation(withAttestation = false)
    }

    @Test
    fun `JWT proof with nested attestation requires the issuer identifier as a string audience`() = runTest {
        checkAudienceValidation(withAttestation = true)
    }

    private suspend fun checkAudienceValidation(withAttestation: Boolean) {
        val now = Instant.fromEpochSeconds(1_800_000_000)
        val issuer = "https://issuer.example"
        val runtime = CryptoRuntime(defaultSoftwareKeyProviders())
        suspend fun key(id: String) = runtime.generateSoftwareKey(GenerateSoftwareKeyRequest(
            KeyId(id), KeySpec.Ec(EcCurve.P256), setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
        ))
        val holder = key("holder")
        val attester = if (withAttestation) key("attester") else null
        val attestation = attester?.let {
            CompactJws.sign(buildJsonObject {
                put("iat", now.epochSeconds)
                put("exp", now.epochSeconds + 300)
                put("attested_keys", JsonArray(listOf(holder.exportPublicJwkObject())))
            }.toString().encodeToByteArray(), it, JwsAlgorithm.ES256,
                buildJsonObject { put("typ", "key-attestation+jwt") })
        }
        val context = CredentialProofValidationContext(
            credentialIssuer = issuer,
            keyAttestation = attester?.let {
                KeyAttestationConfig(KeyAttestationVerificationMethod.StaticJwk(it.exportPublicJwkObject())).toVerificationOptions()
            },
        )
        val metadata = ProofTypeMetadata(setOf("ES256"))
        val configuration = CredentialConfiguration(
            format = CredentialFormat.SD_JWT_VC,
            vct = "test",
            cryptographicBindingMethodsSupported = setOf(CryptographicBindingMethod.Jwk),
            proofTypesSupported = mapOf(ProofType.JWT.value to metadata),
        )
        val handler = JwtCredentialProofHandler(now = { now })
        suspend fun verify(audience: JsonElement?) = handler.verify(
            proof = JsonPrimitive(CompactJws.sign(buildJsonObject {
                audience?.let { put("aud", it) }
                put("iat", now.epochSeconds)
            }.toString().encodeToByteArray(), holder, JwsAlgorithm.ES256, buildJsonObject {
                put("typ", "openid4vci-proof+jwt")
                put("jwk", holder.exportPublicJwkObject())
                attestation?.let { put("key_attestation", it) }
            })),
            proofMetadata = metadata,
            configuration = configuration,
            context = context,
        )

        val evidence = assertIs<VerifiedJwtProof>(verify(JsonPrimitive(issuer)).evidence)
        assertEquals(JsonPrimitive(issuer), evidence.payload["aud"])
        assertEquals(withAttestation, evidence.keyAttestation != null)
        for (audience in listOf(
            null, JsonNull, JsonPrimitive(""), JsonPrimitive(" "), JsonPrimitive(123), JsonPrimitive(true),
            JsonPrimitive("https://other.example"), JsonPrimitive("$issuer/"), JsonPrimitive("https://ISSUER.example"),
            JsonObject(emptyMap()), JsonArray(emptyList()),
            JsonArray(listOf(JsonPrimitive(issuer))),
            JsonArray(listOf(JsonPrimitive("https://other.example"), JsonPrimitive(issuer))),
            JsonArray(listOf(JsonPrimitive(issuer), JsonPrimitive(123))),
            JsonArray(listOf(JsonPrimitive(issuer), JsonPrimitive(true))),
            JsonArray(listOf(JsonPrimitive(issuer), JsonNull)),
        )) {
            val failure = assertFailsWith<CredentialProofValidationException>("aud=$audience") { verify(audience) }
            assertEquals(CredentialErrorCodes.INVALID_PROOF, failure.errorCode)
        }
    }
}
