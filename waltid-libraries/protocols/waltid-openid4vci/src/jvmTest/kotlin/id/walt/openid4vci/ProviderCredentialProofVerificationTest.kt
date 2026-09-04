package id.walt.openid4vci

import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.openid4vci.core.buildOAuth2Provider
import id.walt.openid4vci.errors.CredentialErrorCodes
import id.walt.openid4vci.handlers.endpoints.credential.CredentialIssuanceInput
import id.walt.openid4vci.handlers.endpoints.credential.CredentialIssuanceInputProvider
import id.walt.openid4vci.handlers.endpoints.credential.CredentialEndpointHandler
import id.walt.openid4vci.metadata.issuer.BatchCredentialIssuance
import id.walt.openid4vci.metadata.issuer.CredentialConfiguration
import id.walt.openid4vci.metadata.issuer.ProofType
import id.walt.openid4vci.proofs.CredentialProofValidationContext
import id.walt.openid4vci.proofs.DefaultCredentialProofVerifier
import id.walt.openid4vci.requests.credential.CredentialRequestResult
import id.walt.openid4vci.responses.credential.CredentialResponseResult
import id.walt.openid4vci.responses.credential.CredentialResponse
import id.walt.openid4vci.responses.credential.IssuedCredential
import id.walt.openid4vci.tokens.jwt.JwtHeaderParams
import id.walt.openid4vci.tokens.jwt.JwtPayloadClaims
import id.walt.sdjwt.SDJwtVC
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class ProviderCredentialProofVerificationTest {

    @Test
    fun `invokes the format handler once with the complete ordered batch`() = runTest {
        var handlerInvocations = 0
        var handledInputCount = 0
        var handledProofCount = 0
        val config = createTestConfig(
            credentialProofVerifier = DefaultCredentialProofVerifier(now = { NOW }),
        )
        config.credentialEndpointHandlers.register(
            CredentialFormat.SD_JWT_VC,
            CredentialEndpointHandler { _, _, _, _, issuanceBatch, _, _, _, _, _, _, _, _, _ ->
                handlerInvocations += 1
                handledInputCount = issuanceBatch.inputs.size
                handledProofCount = issuanceBatch.verifiedProofs.size
                CredentialResponseResult.Success(
                    CredentialResponse(
                        credentials = issuanceBatch.instances.mapIndexed { index, _ ->
                            IssuedCredential(JsonPrimitive("credential-$index"))
                        }
                    )
                )
            },
        )
        val provider = buildOAuth2Provider(config)
        val request = createCredentialRequest(
            provider,
            List(3) { createProof(JWKKey.generate(KeyType.secp256r1)) },
        )

        val responseResult = provider.createCredentialResponse(
            request = request,
            configuration = credentialConfiguration(),
            issuerKey = JWKKey.generate(KeyType.secp256r1),
            issuerId = "did:example:issuer",
            issuanceInputData = issuanceInputs(
                buildJsonObject { put("given_name", "Alice") },
            ),
            proofValidationContext = proofContext(batchSize = 3),
        )

        assertTrue(responseResult is CredentialResponseResult.Success)
        assertEquals(1, handlerInvocations)
        assertEquals(3, handledInputCount)
        assertEquals(3, handledProofCount)
        assertEquals(3, responseResult.response.credentials?.size)
    }

    @Test
    fun `issues a configured batch larger than two`() = runTest {
        val provider = buildOAuth2Provider(
            createTestConfig(credentialProofVerifier = DefaultCredentialProofVerifier(now = { NOW })),
        )
        val proofs = List(3) { createProof(JWKKey.generate(KeyType.secp256r1)) }
        val request = createCredentialRequest(provider, proofs)
        var suppliedCredentialCount = 0

        val responseResult = provider.createCredentialResponse(
            request = request,
            configuration = credentialConfiguration(),
            issuerKey = JWKKey.generate(KeyType.secp256r1),
            issuerId = "did:example:issuer",
            issuanceInputData = CredentialIssuanceInputProvider { credentialCount ->
                suppliedCredentialCount = credentialCount
                List(credentialCount) {
                    CredentialIssuanceInput(buildJsonObject { put("given_name", "Alice") })
                }
            },
            proofValidationContext = proofContext(batchSize = 3),
        )

        assertTrue(responseResult is CredentialResponseResult.Success)
        assertEquals(3, responseResult.response.credentials?.size)
        assertEquals(3, suppliedCredentialCount)
    }

    @Test
    fun `binds credentials to the corresponding proof keys in request order`() = runTest {
        val provider = buildOAuth2Provider(
            createTestConfig(credentialProofVerifier = DefaultCredentialProofVerifier(now = { NOW })),
        )
        val holderKeys = List(3) { JWKKey.generate(KeyType.secp256r1) }
        val request = createCredentialRequest(provider, holderKeys.map { createProof(it) })

        val responseResult = provider.createCredentialResponse(
            request = request,
            configuration = credentialConfiguration(),
            issuerKey = JWKKey.generate(KeyType.secp256r1),
            issuerId = "did:example:issuer",
            issuanceInputData = CredentialIssuanceInputProvider { credentialCount ->
                List(credentialCount) {
                    CredentialIssuanceInput(buildJsonObject { put("given_name", "Alice") })
                }
            },
            proofValidationContext = proofContext(batchSize = 3),
        )

        assertTrue(responseResult is CredentialResponseResult.Success)
        val expectedHolderThumbprints = holderKeys.map { it.getPublicKey().getThumbprint() }
        val actualHolderThumbprints = requireNotNull(responseResult.response.credentials).map { issued ->
            val credential = issued.credential as JsonPrimitive
            val holderJwk = requireNotNull(SDJwtVC.parse(credential.content).holderKeyJWK)
            JWKKey.importJWK(holderJwk.toString()).getOrThrow().getThumbprint()
        }

        assertEquals(expectedHolderThumbprints, actualHolderThumbprints)
    }

    @Test
    fun `rejects multiple proofs when batch issuance is disabled`() = runTest {
        val provider = buildOAuth2Provider(
            createTestConfig(credentialProofVerifier = DefaultCredentialProofVerifier(now = { NOW })),
        )
        val request = createCredentialRequest(
            provider,
            listOf(
                createProof(JWKKey.generate(KeyType.secp256r1)),
                createProof(JWKKey.generate(KeyType.secp256r1)),
            ),
        )

        val responseResult = provider.createCredentialResponse(
            request = request,
            configuration = credentialConfiguration(),
            issuerKey = JWKKey.generate(KeyType.secp256r1),
            issuerId = "did:example:issuer",
            issuanceInputData = issuanceInputs(
                buildJsonObject { put("given_name", "Alice") },
            ),
            proofValidationContext = proofContext(),
        )

        assertTrue(responseResult is CredentialResponseResult.Failure)
        assertEquals(CredentialErrorCodes.INVALID_PROOF, responseResult.error.error)
    }

    @Test
    fun `rejects proof count above configured batch size before requesting issuance inputs`() = runTest {
        val provider = buildOAuth2Provider(
            createTestConfig(credentialProofVerifier = DefaultCredentialProofVerifier(now = { NOW })),
        )
        val request = createCredentialRequest(
            provider,
            List(3) { createProof(JWKKey.generate(KeyType.secp256r1)) },
        )
        var inputProviderInvoked = false

        val responseResult = provider.createCredentialResponse(
            request = request,
            configuration = credentialConfiguration(),
            issuerKey = JWKKey.generate(KeyType.secp256r1),
            issuerId = "did:example:issuer",
            issuanceInputData = CredentialIssuanceInputProvider {
                inputProviderInvoked = true
                emptyList()
            },
            proofValidationContext = proofContext(batchSize = 2),
        )

        assertTrue(responseResult is CredentialResponseResult.Failure)
        assertEquals(CredentialErrorCodes.INVALID_PROOF, responseResult.error.error)
        assertEquals(false, inputProviderInvoked)
    }

    @Test
    fun `accepts duplicate holder keys in a credential batch`() = runTest {
        val provider = buildOAuth2Provider(
            createTestConfig(credentialProofVerifier = DefaultCredentialProofVerifier(now = { NOW })),
        )
        val holderKey = JWKKey.generate(KeyType.secp256r1)
        val request = createCredentialRequest(provider, listOf(createProof(holderKey), createProof(holderKey)))
        var suppliedCredentialCount = 0

        val responseResult = provider.createCredentialResponse(
            request = request,
            configuration = credentialConfiguration(),
            issuerKey = JWKKey.generate(KeyType.secp256r1),
            issuerId = "did:example:issuer",
            issuanceInputData = CredentialIssuanceInputProvider { credentialCount ->
                suppliedCredentialCount = credentialCount
                List(credentialCount) {
                    CredentialIssuanceInput(buildJsonObject { put("given_name", "Alice") })
                }
            },
            proofValidationContext = proofContext(batchSize = 2),
        )

        assertTrue(responseResult is CredentialResponseResult.Success)
        assertEquals(2, suppliedCredentialCount)
        assertEquals(2, responseResult.response.credentials?.size)
        val expectedHolderThumbprint = holderKey.getPublicKey().getThumbprint()
        val actualHolderThumbprints = requireNotNull(responseResult.response.credentials).map { issued ->
            val credential = issued.credential as JsonPrimitive
            val holderJwk = requireNotNull(SDJwtVC.parse(credential.content).holderKeyJWK)
            JWKKey.importJWK(holderJwk.toString()).getOrThrow().getThumbprint()
        }
        assertEquals(listOf(expectedHolderThumbprint, expectedHolderThumbprint), actualHolderThumbprints)
    }

    @Test
    fun `rejects complete batch when one proof is invalid before requesting issuance inputs`() = runTest {
        val provider = buildOAuth2Provider(
            createTestConfig(credentialProofVerifier = DefaultCredentialProofVerifier(now = { NOW })),
        )
        val request = createCredentialRequest(
            provider,
            listOf(
                createProof(JWKKey.generate(KeyType.secp256r1)),
                tamperSignature(createProof(JWKKey.generate(KeyType.secp256r1))),
            ),
        )
        var inputProviderInvoked = false

        val responseResult = provider.createCredentialResponse(
            request = request,
            configuration = credentialConfiguration(),
            issuerKey = JWKKey.generate(KeyType.secp256r1),
            issuerId = "did:example:issuer",
            issuanceInputData = CredentialIssuanceInputProvider {
                inputProviderInvoked = true
                emptyList()
            },
            proofValidationContext = proofContext(batchSize = 2),
        )

        assertTrue(responseResult is CredentialResponseResult.Failure)
        assertEquals(CredentialErrorCodes.INVALID_PROOF, responseResult.error.error)
        assertEquals(false, inputProviderInvoked)
    }

    @Test
    fun `createCredentialResponse rejects invalid proof signature before issuing credential`() = runTest {
        val provider = buildOAuth2Provider(
            createTestConfig(
                credentialProofVerifier = DefaultCredentialProofVerifier(now = { NOW }),
            ),
        )
        val holderKey = JWKKey.generate(KeyType.secp256r1)
        val proofJwt = tamperSignature(createProof(holderKey))
        val proofParam = buildJsonObject {
            put("jwt", JsonArray(listOf(JsonPrimitive(proofJwt))))
        }.toString()

        val requestResult = provider.createCredentialRequest(
            parameters = mapOf(
                "credential_configuration_id" to listOf(CREDENTIAL_CONFIGURATION_ID),
                "proofs" to listOf(proofParam),
            ),
        )
        assertTrue(requestResult is CredentialRequestResult.Success)

        val responseResult = provider.createCredentialResponse(
            request = requestResult.request,
            configuration = credentialConfiguration(),
            issuerKey = JWKKey.generate(KeyType.secp256r1),
            issuerId = "did:example:issuer",
            issuanceInputData = issuanceInputs(
                buildJsonObject { put("given_name", "Alice") },
            ),
            proofValidationContext = CredentialProofValidationContext(
                credentialIssuer = CREDENTIAL_ISSUER,
                clientId = "client",
            ),
        )

        assertTrue(responseResult is CredentialResponseResult.Failure)
        assertEquals(CredentialErrorCodes.INVALID_PROOF, responseResult.error.error)
    }

    private suspend fun createProof(key: JWKKey): String = key.signJws(
        plaintext = buildJsonObject {
            put(JwtPayloadClaims.AUDIENCE, CREDENTIAL_ISSUER)
            put(JwtPayloadClaims.ISSUED_AT, NOW.epochSeconds)
        }.toString().encodeToByteArray(),
        headers = mapOf(
            JwtHeaderParams.TYPE to JsonPrimitive("openid4vci-proof+jwt"),
            JwtHeaderParams.JSON_WEB_KEY to key.getPublicKey().exportJWKObject(),
        ),
    )

    private suspend fun createCredentialRequest(
        provider: id.walt.openid4vci.core.OAuth2Provider,
        proofs: List<String>,
    ) = provider.createCredentialRequest(
        parameters = mapOf(
            "credential_configuration_id" to listOf(CREDENTIAL_CONFIGURATION_ID),
            "proofs" to listOf(
                buildJsonObject {
                    put("jwt", JsonArray(proofs.map { JsonPrimitive(it) }))
                }.toString()
            ),
        ),
    ).let { result ->
        assertTrue(result is CredentialRequestResult.Success)
        result.request
    }

    private fun proofContext(batchSize: Int? = null) = CredentialProofValidationContext(
        credentialIssuer = CREDENTIAL_ISSUER,
        clientId = "client",
        batchCredentialIssuance = batchSize?.let(::BatchCredentialIssuance),
    )

    private fun issuanceInputs(credentialData: JsonObject) =
        CredentialIssuanceInputProvider { credentialCount ->
            List(credentialCount) { CredentialIssuanceInput(credentialData) }
        }

    private fun credentialConfiguration() = CredentialConfiguration(
        format = CredentialFormat.SD_JWT_VC,
        vct = CREDENTIAL_CONFIGURATION_ID,
        cryptographicBindingMethodsSupported = setOf(CryptographicBindingMethod.Jwk),
        proofTypesSupported = mapOf(
            "jwt" to ProofType(proofSigningAlgValuesSupported = setOf("ES256")),
        ),
    )

    private fun tamperSignature(jwt: String): String {
        val parts = jwt.split(".")
        check(parts.size == 3)
        val replacement = if (parts[2].first() == 'A') 'B' else 'A'
        return "${parts[0]}.${parts[1]}.$replacement${parts[2].drop(1)}"
    }

    private companion object {
        const val CREDENTIAL_ISSUER = "https://issuer.example"
        const val CREDENTIAL_CONFIGURATION_ID = "identity_credential"
        val NOW = Instant.fromEpochSeconds(1_800_000_000)
    }
}
