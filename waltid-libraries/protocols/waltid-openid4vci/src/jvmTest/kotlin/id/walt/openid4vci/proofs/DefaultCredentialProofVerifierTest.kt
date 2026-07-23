package id.walt.openid4vci.proofs

import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.openid4vci.CryptographicBindingMethod
import id.walt.openid4vci.CredentialFormat
import id.walt.openid4vci.DefaultClient
import id.walt.openid4vci.errors.CredentialErrorCodes
import id.walt.openid4vci.metadata.issuer.CredentialConfiguration
import id.walt.openid4vci.metadata.issuer.ProofType
import id.walt.openid4vci.prooftypes.Proofs
import id.walt.openid4vci.requests.credential.DefaultCredentialRequest
import id.walt.openid4vci.tokens.jwt.JwtHeaderParams
import id.walt.openid4vci.tokens.jwt.JwtPayloadClaims
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Instant

class DefaultCredentialProofVerifierTest {

    @Test
    fun `verifies jwt proof signature and returns holder key`() = runTest {
        val holderKey = JWKKey.generate(KeyType.secp256r1)
        val proof = createProof(holderKey)

        val verified = verifier.verify(
            credentialRequest = credentialRequest(proof),
            credentialConfiguration = credentialConfiguration(),
            context = context(),
        )

        assertEquals(1, verified.size)
        assertEquals(holderKey.getPublicKey().getThumbprint(), verified.single().holderKey.getThumbprint())
    }

    @Test
    fun `rejects tampered jwt proof signature as invalid proof`() = runTest {
        val holderKey = JWKKey.generate(KeyType.secp256r1)
        val proof = tamperSignature(createProof(holderKey))

        val error = assertFailsWith<CredentialProofValidationException> {
            verifier.verify(
                credentialRequest = credentialRequest(proof),
                credentialConfiguration = credentialConfiguration(),
                context = context(),
            )
        }

        assertEquals(CredentialErrorCodes.INVALID_PROOF, error.errorCode)
    }

    @Test
    fun `rejects proof audience that is not credential issuer identifier`() = runTest {
        val holderKey = JWKKey.generate(KeyType.secp256r1)
        val proof = createProof(holderKey, audience = "did:example:issuer")

        val error = assertFailsWith<CredentialProofValidationException> {
            verifier.verify(
                credentialRequest = credentialRequest(proof),
                credentialConfiguration = credentialConfiguration(),
                context = context(),
            )
        }

        assertEquals(CredentialErrorCodes.INVALID_PROOF, error.errorCode)
    }

    @Test
    fun `requires nonce when nonce endpoint is enabled`() = runTest {
        val holderKey = JWKKey.generate(KeyType.secp256r1)
        val proof = createProof(holderKey, nonce = null)

        val error = assertFailsWith<CredentialProofValidationException> {
            verifier.verify(
                credentialRequest = credentialRequest(proof),
                credentialConfiguration = credentialConfiguration(),
                context = context(
                    nonceService = AlwaysValidNonceService,
                ),
            )
        }

        assertEquals(CredentialErrorCodes.INVALID_NONCE, error.errorCode)
    }

    @Test
    fun `allows a valid nonce to be reused until its expiry`() = runTest {
        val holderKey = JWKKey.generate(KeyType.secp256r1)
        val proof = createProof(holderKey, nonce = "nonce-1")
        val context = context(nonceService = AlwaysValidNonceService)

        verifier.verify(
            credentialRequest = credentialRequest(proof),
            credentialConfiguration = credentialConfiguration(),
            context = context,
        )
        verifier.verify(
            credentialRequest = credentialRequest(proof),
            credentialConfiguration = credentialConfiguration(),
            context = context,
        )
    }

    private suspend fun createProof(
        key: JWKKey,
        audience: String = CREDENTIAL_ISSUER,
        nonce: String? = "nonce-1",
    ): String = key.signJws(
        plaintext = buildJsonObject {
            put(JwtPayloadClaims.AUDIENCE, audience)
            put(JwtPayloadClaims.ISSUED_AT, NOW.epochSeconds)
            nonce?.let { put("nonce", it) }
        }.toString().encodeToByteArray(),
        headers = mapOf(
            JwtHeaderParams.TYPE to JsonPrimitive("openid4vci-proof+jwt"),
            JwtHeaderParams.JSON_WEB_KEY to key.getPublicKey().exportJWKObject(),
        ),
    )

    private fun credentialRequest(proof: String) = DefaultCredentialRequest(
        client = DefaultClient(
            id = "client",
            redirectUris = emptyList(),
            grantTypes = emptySet(),
            responseTypes = emptySet(),
            scopes = setOf("credential"),
        ),
        credentialIdentifier = null,
        credentialConfigurationId = CREDENTIAL_CONFIGURATION_ID,
        proofs = Proofs(jwt = listOf(proof)),
        credentialResponseEncryption = null,
    )

    private fun credentialConfiguration() = CredentialConfiguration(
        format = CredentialFormat.SD_JWT_VC,
        vct = CREDENTIAL_CONFIGURATION_ID,
        cryptographicBindingMethodsSupported = setOf(CryptographicBindingMethod.Jwk),
        proofTypesSupported = mapOf(
            "jwt" to ProofType(proofSigningAlgValuesSupported = setOf("ES256")),
        ),
    )

    private fun context(
        nonceService: CredentialNonceService? = null,
    ) = CredentialProofValidationContext(
        credentialIssuer = CREDENTIAL_ISSUER,
        clientId = "client",
        nonceValidation = nonceService?.let {
            CredentialNonceValidationContext(
                service = it,
                binding = CredentialNonceBinding(
                    credentialIssuer = CREDENTIAL_ISSUER,
                    credentialEndpoint = CREDENTIAL_ENDPOINT,
                    nonceEndpoint = NONCE_ENDPOINT,
                ),
            )
        },
    )

    private fun tamperSignature(jwt: String): String {
        val parts = jwt.split(".")
        check(parts.size == 3)
        val replacement = if (parts[2].first() == 'A') 'B' else 'A'
        return "${parts[0]}.${parts[1]}.$replacement${parts[2].drop(1)}"
    }

    private companion object {
        const val CREDENTIAL_ISSUER = "https://issuer.example"
        const val CREDENTIAL_ENDPOINT = "$CREDENTIAL_ISSUER/credential"
        const val NONCE_ENDPOINT = "$CREDENTIAL_ISSUER/nonce"
        const val CREDENTIAL_CONFIGURATION_ID = "identity_credential"
        val NOW = Instant.fromEpochSeconds(1_800_000_000)
        val verifier = DefaultCredentialProofVerifier(now = { NOW })

        val AlwaysValidNonceService = object : CredentialNonceService {
            override suspend fun issue(binding: CredentialNonceBinding): IssuedCredentialNonce =
                error("Nonce issuance is not used in this test")

            override suspend fun validate(
                nonce: String,
                binding: CredentialNonceBinding,
            ): CredentialNonceValidationResult = CredentialNonceValidationResult.VALID
        }
    }
}
