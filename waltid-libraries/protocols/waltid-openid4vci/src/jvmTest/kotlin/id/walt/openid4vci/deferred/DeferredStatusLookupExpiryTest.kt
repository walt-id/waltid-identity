package id.walt.openid4vci.deferred

import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.jose.*
import id.walt.crypto2.keys.*
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.openid4vci.*
import id.walt.openid4vci.attestation.*
import id.walt.openid4vci.clientauth.attestation.VerifiedClientAttestation
import id.walt.openid4vci.core.buildOAuth2Provider
import id.walt.openid4vci.metadata.issuer.*
import id.walt.openid4vci.handlers.endpoints.credential.CredentialEndpointHandler
import id.walt.openid4vci.proofs.CredentialProofValidationContext
import id.walt.openid4vci.proofs.CredentialProofValidationException
import id.walt.openid4vci.requests.credential.*
import id.walt.openid4vci.responses.credential.*
import id.walt.statuslist.codec.TokenStatusListCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import kotlin.test.*
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

class DeferredStatusLookupExpiryTest {
    @Test fun initialAcceptanceRechecksLifetimeAfterStatusResolution() = runTest {
        val signer = CryptoRuntime(defaultSoftwareKeyProviders()).generateSoftwareKey(GenerateSoftwareKeyRequest(
            KeyId("attester"), KeySpec.Ec(EcCurve.P256), setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
        ))
        val publicJwk = signer.exportPublicJwkObject()
        val now = Clock.System.now()
        val uri = "https://attester.example/status"
        val attestation = VerifiedClientAttestation("wallet", publicJwk,
            VerifiedAttestationLifetime(now.epochSeconds, now.epochSeconds + 2, clockSkewSeconds = 0),
            VerifiedAttestationStatus(capturedAt = now.epochSeconds, policy = AttestationStatusPolicy(true),
                reference = AttestationStatusReference(uri, 0), attesterPublicJwk = publicJwk))
        var fetches = 0
        val config = createTestConfig().copy(attestationStatusResolver = AttestationStatusResolver {
            fetches++
            withContext(Dispatchers.Default) {
                // Lifetime checks accept the entire WIA expiration second, including clock tolerance.
                while (Clock.System.now().epochSeconds <= requireNotNull(attestation.lifetime.expiresAt)) delay(25)
            }
            CompactJws.sign(buildJsonObject {
                put("sub", uri); put("iat", now.epochSeconds); put("exp", now.epochSeconds + 3600)
                putJsonObject("status_list") { put("bits", 1); put("lst", TokenStatusListCodec.encode(byteArrayOf(0))) }
            }.toString().encodeToByteArray(), signer, JwsAlgorithm.ES256, buildJsonObject { put("typ", "statuslist+jwt") })
        })
        config.credentialEndpointHandlers.register(CredentialFormat.SD_JWT_VC,
            CredentialEndpointHandler { _, _, _, _, _, _, _, _, _, _, _, _, _, _, _ -> error("Acceptance must not sign") })
        val request = AuthenticatedCredentialRequest(DefaultCredentialRequest(
            client = DefaultClient("wallet", emptyList(), emptySet(), emptySet()), credentialIdentifier = "credential",
            credentialConfigurationId = null, proofs = null, credentialResponseEncryption = null, accessTokenClientId = "wallet"),
            CredentialRequestAuthentication("wallet", false, attestation, true))
        val failure = assertFailsWith<CredentialProofValidationException> {
            buildOAuth2Provider(config).acceptCredentialRequest(request,
                CredentialConfiguration(CredentialFormat.SD_JWT_VC, vct = "identity"), "credential", "config",
                CredentialProofValidationContext("https://issuer.example"))
        }
        assertEquals("credential_request_denied", failure.errorCode)
        assertEquals(1, fetches, "Lifetime must be checked again after the lookup, not only before it")
    }

    @Test fun cachedResponseRefusesExpiryDuringFinalLookupAndCanRetry() = runTest {
        val runtime = CryptoRuntime(defaultSoftwareKeyProviders())
        suspend fun key(id: String) = runtime.generateSoftwareKey(GenerateSoftwareKeyRequest(
            KeyId(id), KeySpec.Ec(EcCurve.P256), setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
        ))
        val signer = key("attester")
        val holder = key("holder")
        val signerJwk = signer.exportPublicJwkObject()
        val holderJwk = holder.exportPublicJwkObject()
        val now = Clock.System.now()
        val wiaUri = "https://attester.example/wia"
        val kaUri = "https://attester.example/ka"
        fun evidence(uri: String) = VerifiedAttestationStatus(capturedAt = now.epochSeconds,
            policy = AttestationStatusPolicy(true), reference = AttestationStatusReference(uri, 0), attesterPublicJwk = signerJwk)
        val lifetime = VerifiedAttestationLifetime(now.epochSeconds - 10, now.epochSeconds + 3600, clockSkewSeconds = 60)
        val accepted = AcceptedCredentialRequest(
            DefaultCredentialRequest(client = DefaultClient("wallet", emptyList(), emptySet(), emptySet()), credentialIdentifier = "credential",
                credentialConfigurationId = null, proofs = null, credentialResponseEncryption = null, accessTokenClientId = "wallet"),
            "credential", "config", CredentialConfiguration(CredentialFormat.SD_JWT_VC, vct = "identity",
                cryptographicBindingMethodsSupported = setOf(CryptographicBindingMethod.Jwk),
                proofTypesSupported = mapOf("jwt" to ProofTypeMetadata(setOf("ES256"), KeyAttestationsRequired()))),
            listOf(AcceptedHolderBinding(holderJwk, proofIndexes = setOf(0))),
            VerifiedClientAttestation("wallet", holderJwk, lifetime, evidence(wiaUri)), true,
            listOf(AcceptedCredentialProof("jwt", AcceptedKeyAttestation(lifetime,
                setOf(Jwk.sha256Thumbprint(holder.exportPublicJwk())), evidence(kaUri)))),
        )
        val response = CredentialResponse(credentials = listOf(IssuedCredential(JsonPrimitive("cached-credential"))))
        var state = DeferredIssuanceState(status = DeferredStatus.READY,
            transactions = mapOf("transaction" to DeferredTransaction("transaction", accepted, response = response)))
        val store = object : DeferredIssuanceStore {
            override suspend fun get(id: String) = DeferredSessionSnapshot(id, now + 60.minutes, state)
            override suspend fun compareAndSet(expected: DeferredSessionSnapshot, replacement: DeferredIssuanceState): Boolean {
                if (state != expected.state) return false
                state = replacement
                return true
            }
        }
        var wiaFetches = 0
        var expireDuringFinalLookup = true
        var lastWiaExpiry = 0L
        val provider = buildOAuth2Provider(createTestConfig().copy(attestationStatusResolver = AttestationStatusResolver { uri ->
            val minted = Clock.System.now().epochSeconds
            if (uri == wiaUri) wiaFetches++
            val expiring = expireDuringFinalLookup && wiaFetches == 2
            val expires = minted + if (uri == wiaUri && expiring) 1 else 3600
            if (uri == wiaUri) lastWiaExpiry = expires
            if (uri == kaUri && expiring) withContext(Dispatchers.Default) { delay(1600) }
            CompactJws.sign(buildJsonObject {
                put("sub", uri); put("iat", minted); put("exp", expires); put("ttl", 60)
                putJsonObject("status_list") { put("bits", 1); put("lst", TokenStatusListCodec.encode(byteArrayOf(0))) }
            }.toString().encodeToByteArray(), signer, JwsAlgorithm.ES256, buildJsonObject { put("typ", "statuslist+jwt") })
        }))
        val deferred = DeferredIssuance(store)
        suspend fun claim() = assertIs<DeferredIssuance.Collection.Claimed>(deferred.collect("offer", "transaction", setOf("credential"), "wallet")).transaction
        val firstClaim = claim()
        val request = DeferredCredentialRequest("transaction")
        val result = provider.createDeferredCredentialResponse(request, response, DeferredCredentialResponseContext("offer", firstClaim, deferred))
        val pending = assertIs<CredentialResponseResult.Success>(result).response
        assertEquals(2, wiaFetches, "The first checkpoint must pass, then the final checkpoint must catch expiry")
        assertTrue(Clock.System.now().epochSeconds >= lastWiaExpiry)
        val pendingHttp = provider.writeDeferredCredentialResponse(request, pending)
        assertEquals(202, pendingHttp.status)
        assertTrue(pendingHttp.retryable)
        assertEquals("transaction", pending.transactionId)
        assertTrue(pending.interval!! > 0)
        assertNull(pending.credentials)
        assertFalse(state.transactions.getValue("transaction").denied)
        assertFalse(state.transactions.getValue("transaction").consumed)
        assertEquals(response, state.transactions.getValue("transaction").response)
        deferred.release("offer", firstClaim)
        expireDuringFinalLookup = false
        val retryClaim = claim()
        val delivered = assertIs<CredentialResponseResult.Success>(provider.createDeferredCredentialResponse(
            request, response, DeferredCredentialResponseContext("offer", retryClaim, deferred))).response
        assertEquals(response.credentials, delivered.credentials)
        assertEquals(200, provider.writeDeferredCredentialResponse(request, delivered).status)
    }
}
