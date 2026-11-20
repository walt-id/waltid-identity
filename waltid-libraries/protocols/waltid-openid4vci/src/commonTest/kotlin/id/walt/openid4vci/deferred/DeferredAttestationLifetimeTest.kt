package id.walt.openid4vci.deferred

import id.walt.openid4vci.createTestConfig
import id.walt.openid4vci.core.buildOAuth2Provider
import id.walt.openid4vci.responses.credential.CredentialResponseResult
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.jose.Jwk
import id.walt.crypto2.jose.exportPublicJwk
import id.walt.crypto2.jose.exportPublicJwkObject
import id.walt.crypto2.keys.*
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.openid4vci.CryptographicBindingMethod
import id.walt.openid4vci.CredentialFormat
import id.walt.openid4vci.DefaultClient
import id.walt.openid4vci.attestation.VerifiedAttestationLifetime
import id.walt.openid4vci.clientauth.attestation.VerifiedClientAttestation
import id.walt.openid4vci.metadata.issuer.*
import id.walt.openid4vci.requests.credential.*
import id.walt.openid4vci.responses.credential.CredentialResponse
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class DeferredAttestationLifetimeTest {
    private var time = Instant.fromEpochSeconds(1_800_000_000)
    private val runtime = CryptoRuntime(defaultSoftwareKeyProviders())
    private val validator = DeferredAttestationLifetimeValidator()
    private fun lifetime(exp: Long? = time.epochSeconds + 120, skew: Long = 60) =
        VerifiedAttestationLifetime(time.epochSeconds - 300, exp, clockSkewSeconds = skew)

    private suspend fun accepted(id: String = "credential"): AcceptedCredentialRequest {
        val holders = List(3) { runtime.generateSoftwareKey(GenerateSoftwareKeyRequest(
            id = KeyId("holder-$it"), spec = KeySpec.Ec(EcCurve.P256), usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
        )) }
        return AcceptedCredentialRequest(
            DefaultCredentialRequest(
                client = DefaultClient("wallet", emptyList(), emptySet(), emptySet()), credentialIdentifier = id,
                credentialConfigurationId = null, proofs = null, credentialResponseEncryption = null,
                accessTokenClientId = "wallet",
            ), id, "config", CredentialConfiguration(
                CredentialFormat.SD_JWT_VC, vct = "identity",
                cryptographicBindingMethodsSupported = setOf(CryptographicBindingMethod.Jwk),
                proofTypesSupported = mapOf("jwt" to ProofTypeMetadata(setOf("ES256"), KeyAttestationsRequired())),
            ),
            holders.mapIndexed { index, key -> AcceptedHolderBinding(key.exportPublicJwkObject(), proofIndexes = setOf(index)) },
            clientAttestation = VerifiedClientAttestation("wallet", holders.first().exportPublicJwkObject(), lifetime()),
            clientAttestationRequired = true,
            proofs = holders.map { key -> AcceptedCredentialProof("jwt", AcceptedKeyAttestation(
                lifetime(), setOf(Jwk.sha256Thumbprint(key.exportPublicJwk())),
            )) },
        )
    }

    @Test fun validEvidenceSurvivesSerializationAndProofFreshnessWindow() = runTest {
        val accepted = accepted()
        // Original proof age and nonce validity are deliberately absent from the snapshot.
        val restored = Json.decodeFromString<AcceptedCredentialRequest>(Json.encodeToString(accepted))
        assertTrue(validator.isValid(restored, time))
        time += 2.minutes
        assertTrue(validator.isValid(restored, time))
    }

    @Test fun clientAndKeyExpirationRetainTheirOriginalSkewBoundaries() {
        val expiry = time.epochSeconds
        val evidence = lifetime(expiry)
        assertTrue(evidence.clientAttestationValidAt(time + 60.seconds))
        assertFalse(evidence.clientAttestationValidAt(time + 61.seconds))
        assertTrue(evidence.keyAttestationValidAt(time + 59.seconds))
        assertFalse(evidence.keyAttestationValidAt(time + 60.seconds))
        assertFalse(evidence.copy(issuedAt = expiry + 61).keyAttestationValidAt(time))
        assertFalse(evidence.copy(issuedAt = expiry + 61).clientAttestationValidAt(time))
        assertTrue(evidence.copy(notBefore = expiry + 60.5).keyAttestationValidAt(time + 0.5.seconds))
        assertFalse(evidence.copy(notBefore = expiry + 60.5).keyAttestationValidAt(time))
        assertFalse(evidence.copy(notBefore = Double.NaN).keyAttestationValidAt(time))
    }

    @Test fun oneExpiredKeyAttestationDeniesTheWholeThreeKeyTransaction() = runTest {
        val original = accepted()
        val expired = original.copy(proofs = original.proofs!!.mapIndexed { index, proof ->
            if (index != 1) proof else proof.copy(keyAttestation = proof.keyAttestation!!.copy(lifetime = lifetime(time.epochSeconds - 60)))
        })
        assertFalse(validator.isValid(expired, time))
        val store = Store(time, expired)
        repeat(2) { assertResponseDenied { store.createResponse() } }
        assertTrue(store.state.transactions.getValue("transaction").denied)
        assertNull(store.state.transactions.getValue("transaction").response)
    }

    @Test fun expiredClientAttestationDeniesEvenWithValidKeyAttestations() = runTest {
        val original = accepted()
        val expired = original.copy(clientAttestation = original.clientAttestation!!.copy(lifetime = lifetime(time.epochSeconds - 61)))
        assertFalse(validator.isValid(expired, time))
        assertResponseDenied { Store(time, expired).createResponse() }
    }

    @Test fun missingRequiredEvidenceAndMismatchedAssociationsFailClosed() = runTest {
        val original = accepted()
        val proof = original.proofs!!.first()
        for (invalid in listOf(
            original.copy(clientAttestation = null),
            original.copy(clientAttestation = original.clientAttestation!!.copy(subject = "other")),
            original.copy(proofs = null),
            original.copy(proofs = emptyList()),
            original.copy(proofs = listOf(proof.copy(keyAttestation = null)) + original.proofs.drop(1)),
            original.copy(proofs = listOf(proof.copy(keyAttestation = proof.keyAttestation!!.copy(attestedKeyThumbprints = emptySet()))) + original.proofs.drop(1)),
            original.copy(bindings = original.bindings.map { it.copy(proofIndexes = setOf(5)) }),
        )) assertFalse(validator.isValid(invalid, time))
        val legacy = original.copy(clientAttestation = null, clientAttestationRequired = null, configuration = original.configuration.copy(proofTypesSupported = null, cryptographicBindingMethodsSupported = null), proofs = null)
        assertFalse(validator.isValid(legacy, time, clientAttestationRequired = true))
        assertTrue(validator.isValid(legacy.copy(request = legacy.request.copy(anonymousPreAuthorizedAccess = true)), time, clientAttestationRequired = true))
    }

    @Test fun standaloneWithoutExpirationKeepsItsAcceptedLifetimeRules() = runTest {
        val original = accepted()
        val standalone = original.copy(
            configuration = original.configuration.copy(proofTypesSupported = mapOf("attestation" to ProofTypeMetadata(setOf("ES256")))),
            proofs = original.proofs!!.map { it.copy(proofType = "attestation", keyAttestation = it.keyAttestation!!.copy(lifetime = lifetime(null))) },
        )
        assertTrue(validator.isValid(standalone, time))
        assertFalse(validator.isValid(standalone.copy(proofs = null), time))
        assertFalse(validator.isValid(original.copy(proofs = standalone.proofs!!.map { it.copy(proofType = "jwt") }), time))
    }

    @Test fun checkpointRecoveryCannotDeliverExpiredEvidenceOrReplaceItOnRetry() = runTest {
        val original = accepted()
        val store = Store(time, original)
        val service = store.service()
        val claim = assertIs<DeferredIssuance.Collection.Claimed>(service.collect("offer", "transaction", setOf("credential"), "wallet")).transaction
        service.checkpoint("offer", claim, CredentialResponse())
        service.release("offer", claim)
        val saved = store.state.transactions.getValue("transaction")
        store.state = Json.decodeFromString(Json.encodeToString(store.state))
        store.time += 4.minutes
        val replacement = original.copy(clientAttestation = original.clientAttestation!!.copy(lifetime = lifetime(store.time.epochSeconds + 300)))
        assertEquals(original, service.accept("offer", replacement).accepted)
        assertResponseDenied { store.createResponse() }
        assertEquals(saved.response, store.state.transactions.getValue("transaction").response)
        assertDenied { store.service().accept("offer", replacement) }
        assertFalse(store.state.transactions.getValue("transaction").consumed)
    }

    @Test fun expirationDuringSigningIsDeniedBeforeCheckpoint() = runTest {
        val store = Store(time, accepted())
        val service = store.service()
        val claim = assertIs<DeferredIssuance.Collection.Claimed>(service.collect("offer", "transaction", setOf("credential"), "wallet")).transaction
        store.time += 4.minutes
        assertResponseDenied { store.createResponse(claim) }
        assertDenied { service.checkpoint("offer", claim, CredentialResponse()) }
        assertTrue(store.state.transactions.getValue("transaction").denied)
        assertNull(store.state.transactions.getValue("transaction").response)
        assertDenied { service.consume("offer", claim) }
    }

    @Test fun failedCompareAndSetRechecksExpiryDuringResponseCreation() = runTest {
        val store = Store(time, accepted())
        var conflict = true
        val racingStore = object : DeferredIssuanceStore by store {
            override suspend fun compareAndSet(expected: DeferredSessionSnapshot, replacement: DeferredIssuanceState): Boolean {
                if (conflict) {
                    conflict = false
                    store.time += 4.minutes
                    return false
                }
                return store.compareAndSet(expected, replacement)
            }
        }
        val service = DeferredIssuance(racingStore, now = { store.time })
        val claim = assertIs<DeferredIssuance.Collection.Claimed>(service.collect("offer", "transaction", setOf("credential"), "wallet")).transaction
        assertResponseDenied {
            buildOAuth2Provider(createTestConfig()).createDeferredCredentialResponse(
                DeferredCredentialRequest("transaction"), CredentialResponse(), DeferredCredentialResponseContext("offer", claim, service),
            )
        }
        assertTrue(store.state.transactions.getValue("transaction").denied)
        assertFalse(store.state.transactions.getValue("transaction").consumed)
    }

    @Test fun transactionDenialPreservesOtherCompletedDatasets() = runTest {
        val original = accepted()
        val store = Store(time, original)
        val completed = DeferredTransaction("completed", original.copy(credentialIdentifier = "completed"), consumed = true, response = CredentialResponse())
        store.state = store.state.copy(transactions = store.state.transactions + (completed.id to completed))
        store.time += 4.minutes
        assertResponseDenied { store.createResponse() }
        assertEquals(completed, store.state.transactions.getValue(completed.id))
        assertEquals(DeferredStatus.READY, store.state.status)
    }

    private suspend fun assertResponseDenied(action: suspend () -> CredentialResponseResult) {
        assertEquals("credential_request_denied", assertIs<CredentialResponseResult.Failure>(action()).error.error)
    }

    private suspend fun assertDenied(action: suspend () -> Any?) {
        assertEquals("credential_request_denied", assertFailsWith<DeferredProtocolException> { action() }.error.error)
    }

    private class Store(var time: Instant, accepted: AcceptedCredentialRequest) : DeferredIssuanceStore {
        var state = DeferredIssuanceState(status = DeferredStatus.READY, transactions = mapOf("transaction" to DeferredTransaction("transaction", accepted)))
        private val deadline = time + 60.minutes
        override suspend fun get(id: String) = DeferredSessionSnapshot(id, deadline, state)
        override suspend fun compareAndSet(expected: DeferredSessionSnapshot, replacement: DeferredIssuanceState): Boolean {
            if (expected.state != state) return false
            state = replacement
            return true
        }
        suspend fun createResponse(claim: DeferredTransaction? = null): CredentialResponseResult = try {
            val service = service()
            val transaction = claim ?: assertIs<DeferredIssuance.Collection.Claimed>(
                service.collect("offer", "transaction", setOf("credential"), "wallet"),
            ).transaction
            buildOAuth2Provider(createTestConfig()).createDeferredCredentialResponse(
                DeferredCredentialRequest("transaction"), transaction.response ?: CredentialResponse(),
                DeferredCredentialResponseContext("offer", transaction, service),
            )
        } catch (e: DeferredProtocolException) {
            CredentialResponseResult.Failure(e.error)
        }
        fun service() = DeferredIssuance(this, now = { time })
    }
}
