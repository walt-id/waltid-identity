package id.walt.openid4vci.proofs

import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.jose.*
import id.walt.crypto2.keys.*
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.openid4vci.CredentialFormat
import id.walt.openid4vci.metadata.issuer.CredentialConfiguration
import id.walt.openid4vci.proofs.attestation.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

class KeyAttestationValidationTest {
    private val now = kotlin.time.Instant.fromEpochSeconds(1_800_000_000)
    private val runtime = CryptoRuntime(defaultSoftwareKeyProviders())
    private suspend fun key(id: String) = runtime.generateSoftwareKey(GenerateSoftwareKeyRequest(
        KeyId(id), KeySpec.Ec(EcCurve.P256), setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
    ))
    private val context = CredentialProofValidationContext("https://issuer.example")
    private val configuration = CredentialConfiguration(CredentialFormat.SD_JWT_VC, vct = "identity")
    private suspend fun payload(key: Key, nbf: JsonElement? = null) = buildJsonObject {
        put("iat", now.epochSeconds)
        put("exp", now.epochSeconds + 7200)
        nbf?.let { put("nbf", it) }
        put("attested_keys", JsonArray(listOf(key.exportPublicJwkObject())))
    }.toString().encodeToByteArray()

    private suspend fun token(attester: Key, nbf: JsonElement? = null) = CompactJws.sign(
        payload(attester, nbf), attester, JwsAlgorithm.ES256,
        buildJsonObject { put("typ", "key-attestation+jwt") },
    )

    private suspend fun verify(jwt: String, attester: Key) = KeyAttestationVerifier(now = { now }).verify(
        jwt, null, context, configuration,
        KeyAttestationVerificationOptions(KeyAttestationTrustResolver { _, _, _ -> listOf(attester) }),
    )

    @Test
    fun `attested key limit defaults to twenty and supports positive overrides or explicit null`() = runTest {
        val method = KeyAttestationVerificationMethod.StaticJwk(key("attester").exportPublicJwkObject())
        val config = KeyAttestationConfig(method)
        val options = config.toVerificationOptions()
        assertEquals(20, options.maxAttestedKeys)
        assertEquals(20, KeyAttestationVerificationOptions(options.trustResolver).maxAttestedKeys)
        val encoded = Json.encodeToJsonElement(config).jsonObject
        assertFalse("maxAttestedKeys" in encoded)
        assertEquals(20, Json.decodeFromJsonElement<KeyAttestationConfig>(encoded).maxAttestedKeys)
        val unlimited = Json.decodeFromString<KeyAttestationConfig>(Json.encodeToString(config.copy(maxAttestedKeys = null)))
        assertNull(unlimited.toVerificationOptions().maxAttestedKeys)
        assertNull(options.copy(maxAttestedKeys = null).maxAttestedKeys)
        val configured = Json.decodeFromString<KeyAttestationConfig>(Json.encodeToString(config.copy(maxAttestedKeys = 2)))
        assertEquals(2, configured.toVerificationOptions().maxAttestedKeys)
        assertEquals(2, options.copy(maxAttestedKeys = 2).maxAttestedKeys)
        for (invalid in listOf(0, -1)) {
            assertFailsWith<IllegalArgumentException> { config.copy(maxAttestedKeys = invalid) }
            assertFailsWith<IllegalArgumentException> { options.copy(maxAttestedKeys = invalid) }
            val json = JsonObject(Json.encodeToJsonElement(config).jsonObject + ("maxAttestedKeys" to JsonPrimitive(invalid)))
            assertFailsWith<IllegalArgumentException> { Json.decodeFromJsonElement<KeyAttestationConfig>(json) }
        }
    }

    @Test
    fun `not before honors skew and rejects malformed claims`() = runTest {
        val attester = key("attester")
        for (nbf in listOf(null, JsonPrimitive(now.epochSeconds), JsonPrimitive(now.epochSeconds + 60), JsonPrimitive(now.epochSeconds + 59.5))) {
            verify(token(attester, nbf), attester)
        }
        for (nbf in listOf(JsonPrimitive(now.epochSeconds + 60.5), JsonPrimitive(now.epochSeconds + 3600),
            JsonPrimitive(now.epochSeconds.toString()), JsonNull, JsonPrimitive(true), buildJsonObject {}, JsonArray(emptyList()))) {
            assertFailsWith<CredentialProofValidationException>("nbf=$nbf") { verify(token(attester, nbf), attester) }
        }
    }

    @Test
    fun `attester public keys are verified locally without invoking remote verifier`() = runTest {
        val attester = key("attester")
        val remoteKey = object : Key by attester {
            override val capabilities = attester.capabilities.copy(
                verifier = Verifier { _, _, _ -> error("Remote verifier must not be invoked") },
            )
        }
        verify(token(attester), remoteKey)
        assertFailsWith<CredentialProofValidationException> { verify(token(key("attacker")), remoteKey) }
    }

    @Test
    fun `public key retrieval failures remain service errors`() = runTest {
        val attester = key("attester")
        val failure = IllegalStateException("KMS public-key retrieval unavailable")
        val unavailableKey = object : Key by attester {
            override val capabilities = attester.capabilities.copy(
                publicKeyExporter = PublicKeyExporter { throw failure },
            )
        }
        val error = assertFailsWith<KeyAttestationServiceException> { verify(token(attester), unavailableKey) }
        assertSame(failure, error.cause)
    }

    @Test
    fun `public key retrieval preserves cancellation`() = runTest {
        val attester = key("attester")
        val cancellation = kotlinx.coroutines.CancellationException("cancelled")
        val cancelledKey = object : Key by attester {
            override val capabilities = attester.capabilities.copy(
                publicKeyExporter = PublicKeyExporter { throw cancellation },
            )
        }
        assertSame(cancellation, assertFailsWith<kotlinx.coroutines.CancellationException> { verify(token(attester), cancelledKey) })
    }
}
