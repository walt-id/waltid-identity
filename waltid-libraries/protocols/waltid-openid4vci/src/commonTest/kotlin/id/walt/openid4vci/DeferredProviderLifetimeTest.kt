package id.walt.openid4vci

import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.jose.Jwk
import id.walt.crypto2.jose.exportPublicJwk
import id.walt.crypto2.jose.exportPublicJwkObject
import id.walt.crypto2.keys.*
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.openid4vci.attestation.VerifiedAttestationLifetime
import id.walt.openid4vci.clientauth.attestation.VerifiedClientAttestation
import id.walt.openid4vci.clientauth.ClientAuthenticationEndpoint
import id.walt.openid4vci.clientauth.ClientAuthenticationMethods
import id.walt.openid4vci.clientauth.ClientAuthenticationServiceConfig
import id.walt.openid4vci.core.OAuth2Provider
import id.walt.openid4vci.core.buildOAuth2Provider
import id.walt.openid4vci.deferred.*
import id.walt.openid4vci.handlers.endpoints.credential.*
import id.walt.openid4vci.metadata.issuer.*
import id.walt.openid4vci.proofs.CredentialProofVerifier
import id.walt.openid4vci.requests.credential.*
import id.walt.openid4vci.requests.credential.encryption.CredentialResponseEncryptionParameters
import id.walt.openid4vci.responses.credential.*
import id.walt.openid4vci.responses.credential.encryption.CredentialResponseEncryptor
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

class DeferredProviderLifetimeTest {
    @Test fun restoredExpiredEvidenceCannotBypassResponseCreation() = runTest {
        for (expired in listOf("wia", "ka")) {
            val fixture = fixture()
            val evidence = fixture.accepted
            val lifetime = VerifiedAttestationLifetime(fixture.time.epochSeconds - 600, fixture.time.epochSeconds - 120, clockSkewSeconds = 60)
            val invalid = if (expired == "wia") evidence.copy(clientAttestation = evidence.clientAttestation!!.copy(lifetime = lifetime))
            else evidence.copy(proofs = evidence.proofs!!.map { it.copy(keyAttestation = it.keyAttestation!!.copy(lifetime = lifetime)) })
            val restored = invalid.restore()
            for (crypto2 in listOf(false, true)) assertDenied { fixture.issue(restored, crypto2) }
            val deferredFixture = Fixture(fixture.key, invalid)
            assertDenied { deferredFixture.createResponse(deferredFixture.claimRequest()) }
            assertTrue(deferredFixture.transaction().denied)
            assertEquals(0, fixture.allocations)
            assertEquals(0, fixture.signatures)
        }
    }

    @Test fun resumedRequestRevalidatesBeforeAllocatingInputsForBothSigningApis() = runTest {
        for (crypto2 in listOf(false, true)) {
            val fixture = fixture()
            val request = fixture.claimRequest().withSession(DefaultSession(subject = "offer")).withIssuer("https://issuer.example")
            fixture.time += 4.minutes
            assertDenied { fixture.issue(request, crypto2) }
            assertEquals(0, fixture.allocations)
            assertEquals(0, fixture.signatures)
            assertTrue(fixture.transaction().denied)
        }
    }

    @Test fun expiryDuringAllocationPreventsSigningForBothApis() = runTest {
        for (crypto2 in listOf(false, true)) {
            val fixture = fixture()
            val request = fixture.claimRequest()
            fixture.expireDuringAllocation = true
            assertDenied { fixture.issue(request, crypto2) }
            assertEquals(1, fixture.allocations)
            assertEquals(0, fixture.signatures)
            assertTrue(fixture.transaction().denied)
        }
    }

    @Test fun expiryDuringSigningDeniesBeforeReturningCredentialsForBothApis() = runTest {
        for (crypto2 in listOf(false, true)) {
            val fixture = fixture()
            val request = fixture.claimRequest()
            fixture.expireDuringSigning = true
            assertDenied { fixture.issue(request, crypto2) }
            assertEquals(1, fixture.signatures)
            assertTrue(fixture.transaction().denied)
            assertNull(fixture.transaction().response)
        }
    }

    @Test fun checkpointResponseIsRevalidatedByTheProviderAtDelivery() = runTest {
        val fixture = fixture()
        val request = fixture.claimRequest()
        fixture.deferred.checkpoint("offer", fixture.transaction(), fixture.response)
        fixture.time += 4.minutes
        assertDenied { fixture.createResponse(request) }
        assertTrue(fixture.transaction().denied)
        assertNotNull(fixture.transaction().response)
        assertFalse(fixture.transaction().consumed)
    }

    @Test fun expiryDuringAsynchronousEncryptionDeniesDelivery() = runTest {
        val fixture = fixture()
        val encryptionJwk = JsonObject(fixture.accepted.bindings.single().publicJwk.filterKeys {
            it in setOf("kty", "crv", "x", "y")
        } + mapOf("alg" to JsonPrimitive("ECDH-ES"), "use" to JsonPrimitive("enc")))
        val request = fixture.claimRequest(CredentialResponseEncryptionParameters(encryptionJwk, "A128GCM"))
        fixture.expireDuringEncryption = true
        assertDenied { fixture.createResponse(request) }
        assertEquals(1, fixture.encryptions)
        assertTrue(fixture.transaction().denied)
    }

    @Test fun writerOnlyFormatsTheCreatedResponseWithoutStorageOrRepeatedEncryption() = runTest {
        val fixture = fixture()
        val encryptionJwk = JsonObject(fixture.accepted.bindings.single().publicJwk.filterKeys {
            it in setOf("kty", "crv", "x", "y")
        } + mapOf("alg" to JsonPrimitive("ECDH-ES"), "use" to JsonPrimitive("enc")))
        val request = fixture.claimRequest(CredentialResponseEncryptionParameters(encryptionJwk, "A128GCM"))
        val result = assertIs<CredentialResponseResult.Success>(fixture.createResponse(request))
        val reads = fixture.storeReads
        val http = fixture.provider.writeCredentialResponse(request, result.response)
        assertEquals(200, http.status)
        assertEquals("encrypted-response", http.encryptedJwt)
        assertEquals(reads, fixture.storeReads)
        assertEquals(1, fixture.encryptions)
        val checkpoint = Json.decodeFromString<CredentialResponse>(Json.encodeToString(result.response))
        assertEquals(fixture.response, checkpoint, "Prepared ciphertext must not enter persisted checkpoints")
    }

    @Test fun pendingResponseUsesTheSameTypedCreationAndWritingStages() = runTest {
        val fixture = fixture()
        val request = DeferredCredentialRequest("transaction")
        val reads = fixture.storeReads
        val result = assertIs<CredentialResponseResult.Success>(fixture.provider.createDeferredCredentialResponse(
            request, CredentialResponse(transactionId = "transaction", interval = 30),
        ))
        val http = fixture.provider.writeDeferredCredentialResponse(request, result.response)
        assertEquals(202, http.status)
        assertEquals("transaction", http.payload.getValue("transaction_id").jsonPrimitive.content)
        assertEquals(reads, fixture.storeReads)
    }

    @Test fun credentialsWithoutAPersistedClaimContextFailClosed() = runTest {
        val fixture = fixture()
        val result = assertIs<CredentialResponseResult.Failure>(fixture.provider.createDeferredCredentialResponse(
            DeferredCredentialRequest("transaction"), fixture.response,
        ))
        assertEquals("invalid_transaction_id", result.error.error)
        assertFalse(fixture.transaction().denied)
    }

    @Test fun legacyAcceptanceWithoutWiaEvidenceIsDeniedUsingProviderConfiguration() = runTest {
        val original = fixture()
        val legacy = original.accepted.copy(clientAttestation = null, clientAttestationRequired = null)
        val fixture = Fixture(original.key, legacy, requireWia = true)
        val request = fixture.claimRequest()
        assertDenied { fixture.issue(request, crypto2 = true) }
        assertDenied { fixture.createResponse(request) }
        assertTrue(fixture.transaction().denied)
        assertEquals(0, fixture.allocations)
        assertEquals(0, fixture.signatures)
    }

    @Test fun acceptedOptionalAuthenticationPolicySurvivesConfigurationChanges() = runTest {
        val original = fixture()
        val optional = original.accepted.copy(clientAttestation = null, clientAttestationRequired = false)
        val fixture = Fixture(original.key, optional, requireWia = true)
        val request = fixture.claimRequest()
        assertIs<CredentialResponseResult.Success>(fixture.issue(request, crypto2 = true))
        assertIs<CredentialResponseResult.Success>(fixture.createResponse(request))
        assertFalse(fixture.transaction().denied)
    }

    private suspend fun assertDenied(action: suspend () -> CredentialResponseResult) {
        assertEquals("credential_request_denied", assertIs<CredentialResponseResult.Failure>(action()).error.error)
    }

    private suspend fun fixture(): Fixture {
        val key = CryptoRuntime(defaultSoftwareKeyProviders()).generateSoftwareKey(GenerateSoftwareKeyRequest(
            KeyId("holder"), KeySpec.Ec(EcCurve.P256), setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
        ))
        val now = Clock.System.now()
        val lifetime = VerifiedAttestationLifetime(now.epochSeconds - 300, now.epochSeconds + 120, clockSkewSeconds = 60)
        val accepted = AcceptedCredentialRequest(
            DefaultCredentialRequest(
                client = DefaultClient("wallet", emptyList(), emptySet(), emptySet()), credentialIdentifier = "credential",
                credentialConfigurationId = null, proofs = null, credentialResponseEncryption = null, accessTokenClientId = "wallet",
            ), "credential", "config", CredentialConfiguration(
                CredentialFormat.SD_JWT_VC, vct = "identity", cryptographicBindingMethodsSupported = setOf(CryptographicBindingMethod.Jwk),
                proofTypesSupported = mapOf("jwt" to ProofTypeMetadata(setOf("ES256"), KeyAttestationsRequired())),
            ), listOf(AcceptedHolderBinding(key.exportPublicJwkObject(), proofIndexes = setOf(0))),
            VerifiedClientAttestation("wallet", key.exportPublicJwkObject(), lifetime), true,
            listOf(AcceptedCredentialProof("jwt", AcceptedKeyAttestation(lifetime, setOf(Jwk.sha256Thumbprint(key.exportPublicJwk()))))),
        )
        return Fixture(key, accepted)
    }

    private class Fixture(val key: Key, val accepted: AcceptedCredentialRequest, requireWia: Boolean = false) : DeferredIssuanceStore {
        var time = Clock.System.now()
        private val deadline = time + 60.minutes
        private var state = DeferredIssuanceState(status = DeferredStatus.READY, transactions = mapOf("transaction" to DeferredTransaction("transaction", accepted)))
        val deferred = DeferredIssuance(this, now = { time })
        val response = CredentialResponse(credentials = listOf(IssuedCredential(JsonPrimitive("signed"))))
        var allocations = 0
        var signatures = 0
        var encryptions = 0
        var expireDuringAllocation = false
        var expireDuringSigning = false
        var expireDuringEncryption = false
        fun transaction() = state.transactions.getValue("transaction")
        var storeReads = 0
        override suspend fun get(id: String): DeferredSessionSnapshot {
            storeReads++
            return DeferredSessionSnapshot(id, deadline, state)
        }
        override suspend fun compareAndSet(expected: DeferredSessionSnapshot, replacement: DeferredIssuanceState): Boolean {
            if (expected.state != state) return false
            state = replacement
            return true
        }
        val provider: OAuth2Provider = buildOAuth2Provider(createTestConfig(
            credentialProofVerifier = CredentialProofVerifier { _, _, _ -> error("Accepted proofs must not be replayed") },
        ).copy(clientAuthenticationServiceConfig = ClientAuthenticationServiceConfig(
            methodsByEndpoint = if (requireWia) mapOf(ClientAuthenticationEndpoint.TOKEN to
                setOf(ClientAuthenticationMethods.ATTEST_JWT_CLIENT_AUTH)) else emptyMap(),
        ), credentialResponseEncryptor = CredentialResponseEncryptor { _, _ ->
            encryptions++
            if (expireDuringEncryption) time += 4.minutes
            "encrypted-response"
        }).also { config ->
            fun sign(): CredentialResponseResult {
                signatures++
                if (expireDuringSigning) time += 4.minutes
                return CredentialResponseResult.Success(response)
            }
            val legacy = CredentialEndpointHandler { _, _, _, _, _, _, _, _, _, _, _, _, _, _, _ -> sign() }
            val crypto2 = Crypto2CredentialEndpointHandler { _, _, _, _, _, _, _, _, _, _, _, _, _, _, _ -> sign() }
            config.credentialEndpointHandlers.register(accepted.configuration.format,
                object : CredentialEndpointHandler by legacy, Crypto2CredentialEndpointHandler by crypto2 {})
        })
        suspend fun claimRequest(encryption: CredentialResponseEncryptionParameters? = null): CredentialRequest {
            val claim = assertIs<DeferredIssuance.Collection.Claimed>(deferred.collect("offer", "transaction", setOf("credential"), "wallet")).transaction
            return claim.accepted.restore(encryption, DeferredCredentialResponseContext("offer", claim, deferred))
        }
        suspend fun createResponse(request: CredentialRequest): CredentialResponseResult =
            provider.createDeferredCredentialResponse(
                DeferredCredentialRequest("transaction", request.credentialResponseEncryption), response,
                DeferredCredentialResponseContext("offer", transaction(), deferred),
            )

        suspend fun issue(request: CredentialRequest, crypto2: Boolean): CredentialResponseResult {
            val inputs = CredentialIssuanceInputProvider { count ->
                allocations++
                if (expireDuringAllocation) time += 4.minutes
                List(count) { CredentialIssuanceInput(JsonObject(emptyMap())) }
            }
            return if (crypto2) provider.createCredentialResponse(
                request, accepted.configuration, Crypto2CredentialSigningKey.select(key, accepted.configuration), "https://issuer.example", inputs,
            ) else provider.createCredentialResponse(
                request, accepted.configuration, LegacyP256TestKey(key), "https://issuer.example", inputs,
            )
        }
    }
}
