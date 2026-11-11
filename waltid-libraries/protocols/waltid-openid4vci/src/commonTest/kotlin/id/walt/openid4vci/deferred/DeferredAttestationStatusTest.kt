package id.walt.openid4vci.deferred

import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.jose.*
import id.walt.crypto2.keys.*
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.openid4vci.*
import id.walt.openid4vci.attestation.*
import id.walt.openid4vci.clientauth.attestation.VerifiedClientAttestation
import id.walt.openid4vci.clientauth.*
import id.walt.openid4vci.clientauth.attestation.*
import id.walt.openid4vci.clientauth.attestation.verifier.Crypto2KeyBasedClientAttestationVerifier
import id.walt.openid4vci.core.buildOAuth2Provider
import id.walt.openid4vci.metadata.issuer.*
import id.walt.openid4vci.proofs.*
import id.walt.openid4vci.proofs.attestation.*
import id.walt.openid4vci.requests.credential.*
import id.walt.openid4vci.requests.credential.encryption.CredentialResponseEncryptionParameters
import id.walt.openid4vci.responses.credential.*
import id.walt.openid4vci.responses.credential.encryption.CredentialResponseEncryptor
import id.walt.statuslist.codec.TokenStatusListCodec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class DeferredAttestationStatusTest {
    private val runtime = CryptoRuntime(defaultSoftwareKeyProviders())
    private val uri = "https://attester.example/status/1"
    private val time = Clock.System.now()
    private suspend fun key(id: String) = runtime.generateSoftwareKey(GenerateSoftwareKeyRequest(
        KeyId(id), KeySpec.Ec(EcCurve.P256), setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
    ))

    private suspend fun token(
        signer: Key, states: List<Int> = listOf(0, 0, 0, 0),
        changes: Map<String, JsonElement> = emptyMap(), typ: String = "statuslist+jwt", omit: Set<String> = emptySet(),
    ): String = CompactJws.sign(buildJsonObject {
        put("sub", uri); put("iat", time.epochSeconds); put("exp", time.epochSeconds + 3600); put("ttl", 60)
        put("status_list", buildJsonObject {
            put("bits", 8); put("lst", TokenStatusListCodec.encode(states.map(Int::toByte).toByteArray()))
        })
        changes.forEach { (name, value) -> put(name, value) }
    }.let { JsonObject(it.filterKeys { name -> name !in omit }) }.toString().encodeToByteArray(), signer, JwsAlgorithm.ES256, buildJsonObject { put("typ", typ) })

    private suspend fun evidence(signer: Key, index: Int = 0) = VerifiedAttestationStatus(
        version = 2, capturedAt = time.epochSeconds,
        policy = AttestationStatusPolicy(requireStatusReference = true, maximumListAgeSeconds = 300),
        reference = AttestationStatusReference(uri, index), attesterIssuer = "https://attester.example",
        attesterPublicJwk = signer.exportPublicJwkObject(),
    )

    @Test fun checksEveryIndexWhileDeduplicatingFetchesAndAuthorizationRemainsPerAttester() = runTest {
        val signer = key("signer")
        var jwt = token(signer)
        var fetches = 0
        val validator = AttestationStatusValidator(AttestationStatusResolver { fetches++; jwt }, now = { time })
        val entries = (0..3).map { evidence(signer, it) to false }
        assertEquals(AttestationStatusOutcome.VALID, validator.validate(entries).outcome)
        assertEquals(1, fetches)
        jwt = token(signer, listOf(0, 0, 0, 1))
        assertEquals(AttestationStatusOutcome.INVALID, validator.validate(entries).outcome)
        assertEquals(2, fetches, "A checkpoint must refresh even while the earlier token's TTL remains valid")
        assertEquals(AttestationStatusOutcome.UNVERIFIABLE, validator.validate(listOf(evidence(key("other")) to false)).outcome)
    }

    @Test fun normalizedStatusTokenTypesStillRequireTheAcceptedAttesterSigner() = runTest {
        val signer = key("signer")
        val attacker = key("attacker")
        val accepted = evidence(signer)
        for (type in listOf("STATUSLIST+JWT", "application/statuslist+jwt", "Application/StatusList+JWT")) {
            for (key in listOf(signer, attacker)) {
                val jwt = token(key, typ = type)
                val result = AttestationStatusValidator(AttestationStatusResolver { jwt }, now = { time })
                    .validate(listOf(accepted to false))
                assertEquals(if (key == signer) AttestationStatusOutcome.VALID else AttestationStatusOutcome.UNVERIFIABLE, result.outcome)
            }
        }
    }

    @Test fun rejectsMalformedUntrustedStaleAndOutOfBoundsStatusWithoutRecordingRevocation() = runTest {
        val signer = key("signer")
        val entry = evidence(signer)
        val invalid = listOf(
            token(key("attacker")), token(signer, typ = "oauth-client-attestation+jwt"),
            token(signer, changes = mapOf("sub" to JsonPrimitive("https://other.example/list"))),
            token(signer, changes = mapOf("iat" to JsonPrimitive(time.epochSeconds - 301))),
            token(signer, changes = mapOf("iat" to JsonPrimitive(time.epochSeconds + 1))),
            token(signer, changes = mapOf("iat" to JsonNull)), token(signer, omit = setOf("iat")),
            token(signer, changes = mapOf("exp" to JsonPrimitive(time.epochSeconds))),
            token(signer, changes = mapOf("nbf" to JsonPrimitive(time.epochSeconds + 1))),
            token(signer, changes = mapOf("ttl" to JsonPrimitive(0))),
            token(signer, changes = mapOf("status_list" to buildJsonObject { put("bits", 3); put("lst", "bad") })),
        )
        for (jwt in invalid) {
            val result = AttestationStatusValidator(AttestationStatusResolver { jwt }, now = { time }).validate(listOf(entry to false))
            assertEquals(AttestationStatusOutcome.UNVERIFIABLE, result.outcome)
            assertFalse(result.permanentlyDenied)
        }
        val validator = AttestationStatusValidator(AttestationStatusResolver { token(signer) }, now = { time })
        assertEquals(AttestationStatusOutcome.UNVERIFIABLE, validator.validate(listOf(entry.copy(reference = AttestationStatusReference(uri, 4)) to false)).outcome)
        val optionalClaimsOmitted = token(signer, omit = setOf("exp", "ttl"))
        assertEquals(AttestationStatusOutcome.VALID, AttestationStatusValidator(AttestationStatusResolver { optionalClaimsOmitted }, now = { time }).validate(listOf(entry to false)).outcome)
        val noOptionalClaims = token(signer, changes = mapOf("exp" to JsonNull))
        assertEquals(AttestationStatusOutcome.UNVERIFIABLE, AttestationStatusValidator(AttestationStatusResolver { noOptionalClaims }, now = { time }).validate(listOf(entry to false)).outcome)
    }

    @Test fun optionalAbsenceRequiredAbsenceAndLegacyEvidenceHaveDistinctHandling() = runTest {
        val empty = JsonObject(emptyMap())
        val optional = captureAttestationStatus(empty, null, AttestationStatusPolicy(), time)
        assertNull(optional.reference)
        val validator = AttestationStatusValidator(AttestationStatusResolver { error("No network request expected") })
        assertEquals(AttestationStatusOutcome.VALID, validator.validate(listOf(optional to false, null to false)).outcome)
        assertEquals(AttestationStatusOutcome.MISSING_EVIDENCE, validator.validate(listOf(null to true)).outcome)
        assertEquals(AttestationStatusOutcome.MISSING_EVIDENCE, validator.validate(listOf(optional.copy(version = 3) to false)).outcome)
        assertFailsWith<IllegalArgumentException> { captureAttestationStatus(empty, null, AttestationStatusPolicy(true), time) }
        for (status in listOf(JsonNull, JsonObject(emptyMap()), buildJsonObject {
            put("status_list", buildJsonObject { put("idx", -1); put("uri", uri) })
        })) assertFails { captureAttestationStatus(buildJsonObject { put("status", status) }, null, AttestationStatusPolicy(), time) }
    }

    @Test fun alreadyInvalidKeyAttestationIsRejectedAtInitialVerification() = runTest {
        val signer = key("attester")
        val holder = key("holder")
        val jwt = CompactJws.sign(buildJsonObject {
            put("iat", time.epochSeconds); put("exp", time.epochSeconds + 600)
            put("attested_keys", JsonArray(listOf(holder.exportPublicJwkObject())))
            put("status", buildJsonObject { put("status_list", buildJsonObject { put("uri", uri); put("idx", 0) }) })
        }.toString().encodeToByteArray(), signer, JwsAlgorithm.ES256, buildJsonObject { put("typ", "key-attestation+jwt") })
        val failure = assertFailsWith<DeferredProtocolException> {
            KeyAttestationVerifier(now = { time }).verify(jwt, null,
                CredentialProofValidationContext("https://issuer.example"), CredentialConfiguration(CredentialFormat.SD_JWT_VC, vct = "identity"),
                KeyAttestationVerificationOptions(KeyAttestationTrustResolver { _, _, _ -> listOf(signer) },
                    statusResolver = AttestationStatusResolver { token(signer, listOf(1, 0, 0, 0)) }),
            )
        }
        assertEquals("credential_request_denied", failure.error.error)
    }

    @Test fun initialClientAuthenticationRejectsRevocationAndKeepsSignerSeparateFromConfirmationKey() = runTest {
        val signer = key("attester")
        val holder = key("wallet-instance")
        val attestation = CompactJws.sign(buildJsonObject {
            put("sub", "wallet"); put("iat", time.epochSeconds); put("exp", time.epochSeconds + 600)
            put("cnf", buildJsonObject { put("jwk", holder.exportPublicJwkObject()) })
            put("status", buildJsonObject { put("status_list", buildJsonObject { put("uri", uri); put("idx", 0) }) })
        }.toString().encodeToByteArray(), signer, JwsAlgorithm.ES256, buildJsonObject { put("typ", "oauth-client-attestation+jwt") })
        val pop = CompactJws.sign(buildJsonObject {
            put("aud", "https://issuer.example"); put("iat", time.epochSeconds); put("jti", "pop")
        }.toString().encodeToByteArray(), holder, JwsAlgorithm.ES256, buildJsonObject { put("typ", "oauth-client-attestation-pop+jwt") })
        var status = token(signer, listOf(1, 0, 0, 0))
        val method = AttestationBasedClientAuthenticationMethod(
            Crypto2KeyBasedClientAttestationVerifier { _, _ -> listOf(signer) }, clock = { time },
            statusResolver = AttestationStatusResolver { status },
        )
        suspend fun authenticate() = method.authenticate(ClientAuthenticationEndpoint.TOKEN, emptyMap(), mapOf(
            ClientAttestationHeaders.CLIENT_ATTESTATION to listOf(attestation), ClientAttestationHeaders.CLIENT_ATTESTATION_POP to listOf(pop),
        ), ClientAuthenticationContext(authorizationServerIssuer = "https://issuer.example"))
        assertEquals("invalid_client", assertIs<ClientAuthenticationResult.Failure>(authenticate()).error.error)
        // A JWT signed by the wallet confirmation key cannot authorize its own attestation status.
        status = token(holder)
        assertEquals("temporarily_unavailable", assertIs<ClientAuthenticationResult.Failure>(authenticate()).error.error)
        status = token(signer)
        val accepted = assertNotNull(assertIs<ClientAuthenticationResult.Authenticated>(authenticate()).client.attestation)
        assertEquals(signer.exportPublicJwkObject(), accepted.status!!.attesterPublicJwk)
        assertEquals(holder.exportPublicJwkObject(), accepted.confirmationJwk)
    }

    @Test fun invalidWiaOrThirdKaPermanentlyDeniesWholeTransactionAndPreservesCompletedDataset() = runTest {
        for (invalidIndex in listOf(0, 3)) {
            val fixture = fixture()
            val completed = DeferredTransaction("completed", fixture.accepted.copy(credentialIdentifier = "completed"), consumed = true)
            fixture.state = fixture.state.copy(transactions = fixture.state.transactions + (completed.id to completed))
            fixture.states[invalidIndex] = 1
            val result = assertIs<CredentialResponseResult.Failure>(fixture.deliver())
            assertEquals("credential_request_denied", result.error.error)
            assertTrue(fixture.transaction().denied)
            assertFalse(fixture.transaction().consumed)
            assertEquals(completed, fixture.state.transactions.getValue("completed"))
            fixture.states[invalidIndex] = 0
            assertFailsWith<DeferredProtocolException> { fixture.claim() }
        }
    }

    @Test fun suspensionAndLookupFailuresAllowRetryWithoutPermanentDenial() = runTest {
        for (suspended in listOf(false, true)) {
            val fixture = fixture()
            fixture.unavailable = !suspended
            if (suspended) fixture.states[0] = 2
            val claim = fixture.claim()
            val result = assertIs<CredentialResponseResult.Success>(fixture.deliver(claim))
            val pending = fixture.provider.writeDeferredCredentialResponse(DeferredCredentialRequest("transaction"), result.response)
            assertEquals(202, pending.status)
            assertTrue(pending.retryable)
            assertEquals("transaction", pending.payload.getValue("transaction_id").jsonPrimitive.content)
            assertTrue(pending.payload.getValue("interval").jsonPrimitive.long > 0)
            assertFalse("credentials" in pending.payload)
            assertFalse("error" in pending.payload)
            assertFalse(fixture.transaction().denied)
            assertFalse(fixture.transaction().consumed)
            fixture.deferred.release("offer", claim)
            fixture.unavailable = false
            fixture.states[0] = 0
            val delivered = assertIs<CredentialResponseResult.Success>(fixture.deliver())
            val http = fixture.provider.writeDeferredCredentialResponse(DeferredCredentialRequest("transaction"), delivered.response)
            assertEquals(200, http.status)
            assertFalse(http.retryable)
            assertEquals(3, delivered.response.credentials!!.size)
        }
    }

    @Test fun cachedResponseCanReturnPendingAfterRestartAndLaterDeliver() = runTest {
        val fixture = fixture()
        val claim = fixture.claim()
        fixture.deferred.checkpoint("offer", claim, fixture.response)
        fixture.deferred.release("offer", claim)
        fixture.state = Json.decodeFromString(Json.encodeToString(fixture.state))
        fixture.unavailable = true
        val pendingClaim = fixture.claim()
        val pending = assertIs<CredentialResponseResult.Success>(fixture.deliver(pendingClaim)).response
        assertTrue(pending.isDeferred)
        assertEquals("transaction", pending.transactionId)
        assertEquals(fixture.response, fixture.transaction().response)
        assertFalse(fixture.transaction().denied)
        fixture.deferred.release("offer", pendingClaim)
        fixture.unavailable = false
        val delivered = assertIs<CredentialResponseResult.Success>(fixture.deliver()).response
        assertEquals(fixture.response.credentials, delivered.credentials)
        assertFalse(delivered.isDeferred)
    }

    @Test fun statusFailureDuringEncryptionReturnsOnlyAnEncryptedPendingResponse() = runTest {
        val fixture = fixture()
        fixture.unavailableDuringEncryption = true
        val encryption = fixture.encryption()
        val request = DeferredCredentialRequest("transaction", encryption)
        val claim = fixture.claim()
        val pending = assertIs<CredentialResponseResult.Success>(fixture.deliver(claim, encryption)).response
        val http = fixture.provider.writeDeferredCredentialResponse(request, pending)
        assertEquals(202, http.status)
        assertTrue(http.retryable)
        assertEquals("encrypted", http.encryptedJwt)
        assertTrue(http.payload.isEmpty())
        assertEquals(setOf("transaction_id", "interval"), fixture.encryptedPayloads.last().keys)
        assertEquals("transaction", fixture.encryptedPayloads.last().getValue("transaction_id").jsonPrimitive.content)
        assertNull(pending.credentials)
        assertFalse(fixture.transaction().denied)
        fixture.deferred.release("offer", claim)
        fixture.unavailableDuringEncryption = false
        fixture.unavailable = false
        val delivered = assertIs<CredentialResponseResult.Success>(fixture.deliver(encryption = encryption)).response
        assertEquals(200, fixture.provider.writeDeferredCredentialResponse(request, delivered).status)
        assertEquals(3, delivered.credentials!!.size)
    }

    @Test fun attestationExpiryWhilePreparingPendingStillPermanentlyDenies() = runTest {
        val fixture = fixture(lifetimeSeconds = 1)
        fixture.unavailable = true
        fixture.expireDuringEncryption = true
        val result = assertIs<CredentialResponseResult.Failure>(fixture.deliver(encryption = fixture.encryption()))
        assertEquals("credential_request_denied", result.error.error)
        assertTrue(fixture.transaction().denied)
    }

    @Test fun pendingEncryptionPropagatesCancellationAndChecksTheClaimFence() = runTest {
        for (cancel in listOf(false, true)) {
            val fixture = fixture()
            fixture.unavailable = true
            fixture.cancelEncryption = cancel
            fixture.loseClaimDuringEncryption = !cancel
            val encryption = fixture.encryption()
            if (cancel) assertFailsWith<CancellationException> { fixture.deliver(encryption = encryption) }
            else {
                assertFailsWith<IllegalStateException> { fixture.deliver(encryption = encryption) }
                assertEquals("successor", fixture.transaction().claimId)
            }
            assertFalse(fixture.transaction().denied)
        }
    }

    @Test fun cachedResponseRestartRechecksStatusAndResubmissionKeepsOriginalEvidence() = runTest {
        val fixture = fixture()
        val claim = fixture.claim()
        fixture.deferred.checkpoint("offer", claim, fixture.response)
        fixture.deferred.release("offer", claim)
        fixture.state = Json.decodeFromString(Json.encodeToString(fixture.state))
        val replacement = fixture.accepted.copy(clientAttestation = fixture.accepted.clientAttestation!!.copy(status = evidence(key("replacement"))))
        assertEquals(fixture.accepted, fixture.deferred.accept("offer", replacement).accepted)
        fixture.states[3] = 1
        assertIs<CredentialResponseResult.Failure>(fixture.deliver())
        assertTrue(fixture.transaction().denied)
        assertNotNull(fixture.transaction().response)
    }

    @Test fun changeDuringResponseEncryptionIsCheckedBeforeReturningSuccess() = runTest {
        val fixture = fixture()
        fixture.revokeDuringEncryption = true
        val claim = fixture.claim()
        val encryption = CredentialResponseEncryptionParameters(JsonObject(fixture.signer.exportPublicJwkObject().filterKeys { it in setOf("kty", "crv", "x", "y") } + ("alg" to JsonPrimitive("ECDH-ES"))), "A128GCM")
        assertIs<CredentialResponseResult.Failure>(fixture.deliver(claim, encryption))
        assertTrue(fixture.transaction().denied)
    }

    @Test fun cancellationPropagatesAndAClaimLostDuringLookupCannotDenyItsSuccessor() = runTest {
        val fixture = fixture()
        fixture.cancelLookup = true
        assertFailsWith<CancellationException> { fixture.deliver() }
        assertFalse(fixture.transaction().denied)
        val other = fixture()
        other.loseClaim = true
        other.states[0] = 1
        assertFailsWith<IllegalStateException> { other.deliver() }
        assertFalse(other.transaction().denied)
        assertEquals("successor", other.transaction().claimId)
    }

    @Test fun requiredLegacyStatusFailsClosedWhileCapturedOptionalAbsenceKeepsItsAcceptedPolicy() = runTest {
        for (mode in listOf("legacy-evidence", "uncaptured-proofs", "optional-absence")) {
            val legacy = mode != "optional-absence"
            val fixture = fixture()
            val optional = VerifiedAttestationStatus(capturedAt = time.epochSeconds, policy = AttestationStatusPolicy())
            val snapshot = fixture.accepted.copy(
                clientAttestation = fixture.accepted.clientAttestation!!.copy(status = if (legacy) null else optional),
                proofs = if (mode == "uncaptured-proofs") null else fixture.accepted.proofs!!.map { it.copy(keyAttestation = it.keyAttestation!!.copy(status = if (legacy) null else optional)) },
                configuration = if (mode == "uncaptured-proofs") fixture.accepted.configuration.copy(proofTypesSupported = null, cryptographicBindingMethodsSupported = null) else fixture.accepted.configuration,
            )
            fixture.state = fixture.state.copy(transactions = mapOf("transaction" to fixture.transaction().copy(accepted = snapshot)))
            val provider = buildOAuth2Provider(createTestConfig().copy(keyAttestationStatusPolicy = { AttestationStatusPolicy(true) }))
            val claim = fixture.claim()
            val result = provider.createDeferredCredentialResponse(DeferredCredentialRequest("transaction"), fixture.response,
                DeferredCredentialResponseContext("offer", claim, fixture.deferred))
            if (legacy) {
                assertEquals("credential_request_denied", assertIs<CredentialResponseResult.Failure>(result).error.error)
                assertTrue(fixture.transaction().denied)
            } else assertIs<CredentialResponseResult.Success>(result)
        }
    }

    @Test fun capturedClientStatusDoesNotRebuildCurrentClientAuthentication() = runTest {
        assertCapturedStatusSkipsLegacyPolicies(clientPolicy = true)
    }

    @Test fun capturedKeyStatusDoesNotResolveTheLegacyKeyPolicy() = runTest {
        assertCapturedStatusSkipsLegacyPolicies(clientPolicy = false)
    }

    private suspend fun assertCapturedStatusSkipsLegacyPolicies(clientPolicy: Boolean) {
        for (optionalOmission in listOf(false, true)) {
            val fixture = fixture()
            if (optionalOmission) {
                val absent = VerifiedAttestationStatus(capturedAt = time.epochSeconds, policy = AttestationStatusPolicy())
                val accepted = fixture.accepted.copy(
                    clientAttestation = fixture.accepted.clientAttestation!!.copy(status = absent),
                    proofs = fixture.accepted.proofs!!.map { it.copy(keyAttestation = it.keyAttestation!!.copy(status = absent)) },
                )
                fixture.state = fixture.state.copy(transactions = mapOf("transaction" to fixture.transaction().copy(accepted = accepted)))
            }
            val base = createTestConfig().copy(attestationStatusResolver = AttestationStatusResolver { token(fixture.signer) })
            val config = if (clientPolicy) base.copy(clientAuthenticationServiceResolver = ClientAuthenticationServiceResolver { _, _, _ ->
                error("Captured evidence must not rebuild current client-authentication trust")
            }) else base.copy(keyAttestationStatusPolicy = {
                error("Captured evidence must not resolve a legacy policy")
            })
            val claim = fixture.claim()
            val response = buildOAuth2Provider(config).createDeferredCredentialResponse(
                DeferredCredentialRequest("transaction"), fixture.response,
                DeferredCredentialResponseContext("offer", claim, fixture.deferred),
            )
            assertIs<CredentialResponseResult.Success>(response)
        }
    }

    private suspend fun fixture(lifetimeSeconds: Long = 3600): Fixture {
        val signer = key("attester")
        val holders = (0..2).map { key("holder-$it") }
        val lifetime = VerifiedAttestationLifetime(time.epochSeconds - 10, time.epochSeconds + lifetimeSeconds, clockSkewSeconds = 60)
        val accepted = AcceptedCredentialRequest(
            DefaultCredentialRequest(client = DefaultClient("wallet", emptyList(), emptySet(), emptySet()),
                credentialIdentifier = "credential", credentialConfigurationId = null, proofs = null,
                credentialResponseEncryption = null, accessTokenClientId = "wallet"),
            "credential", "config", CredentialConfiguration(CredentialFormat.SD_JWT_VC, vct = "identity",
                cryptographicBindingMethodsSupported = setOf(CryptographicBindingMethod.Jwk),
                proofTypesSupported = mapOf("jwt" to ProofTypeMetadata(setOf("ES256"), KeyAttestationsRequired()))),
            holders.mapIndexed { index, key -> AcceptedHolderBinding(key.exportPublicJwkObject(), proofIndexes = setOf(index)) },
            VerifiedClientAttestation("wallet", holders.first().exportPublicJwkObject(), lifetime, evidence(signer)), true,
            holders.mapIndexed { index, key -> AcceptedCredentialProof("jwt", AcceptedKeyAttestation(
                lifetime, setOf(Jwk.sha256Thumbprint(key.exportPublicJwk())), evidence(signer, index + 1),
            )) },
        )
        return Fixture(signer, accepted)
    }

    private inner class Fixture(val signer: Key, val accepted: AcceptedCredentialRequest) : DeferredIssuanceStore {
        val states = mutableListOf(0, 0, 0, 0)
        var unavailable = false
        var cancelLookup = false
        var loseClaim = false
        var revokeDuringEncryption = false
        var unavailableDuringEncryption = false
        var expireDuringEncryption = false
        var cancelEncryption = false
        var loseClaimDuringEncryption = false
        var currentTime = time
        val encryptedPayloads = mutableListOf<JsonObject>()
        var state = DeferredIssuanceState(status = DeferredStatus.READY, transactions = mapOf("transaction" to DeferredTransaction("transaction", accepted)))
        val deferred = DeferredIssuance(this, now = { currentTime })
        val response = CredentialResponse(credentials = List(3) { IssuedCredential(JsonPrimitive("signed-$it")) })
        fun transaction() = state.transactions.getValue("transaction")
        override suspend fun get(id: String) = DeferredSessionSnapshot(id, time + 60.minutes, state)
        override suspend fun compareAndSet(expected: DeferredSessionSnapshot, replacement: DeferredIssuanceState): Boolean {
            if (expected.state != state) return false
            state = replacement
            return true
        }
        val provider = buildOAuth2Provider(createTestConfig().copy(
            attestationStatusResolver = AttestationStatusResolver {
                if (cancelLookup) throw CancellationException("cancelled")
                if (unavailable) error("temporary lookup failure")
                if (loseClaim) state = state.copy(transactions = state.transactions + ("transaction" to transaction().copy(claimId = "successor")))
                token(signer, states)
            },
            credentialResponseEncryptor = CredentialResponseEncryptor { payload, _ ->
                encryptedPayloads += payload
                if (cancelEncryption) throw CancellationException("cancelled during pending encryption")
                if (loseClaimDuringEncryption) state = state.copy(transactions = state.transactions + ("transaction" to transaction().copy(claimId = "successor")))
                if (expireDuringEncryption) currentTime = time + 62.seconds
                if (revokeDuringEncryption) states[3] = 1
                if (unavailableDuringEncryption) unavailable = true
                "encrypted"
            },
        ))
        suspend fun encryption() = CredentialResponseEncryptionParameters(
            JsonObject(signer.exportPublicJwkObject().filterKeys { it in setOf("kty", "crv", "x", "y") } + ("alg" to JsonPrimitive("ECDH-ES"))),
            "A128GCM",
        )
        suspend fun claim() = assertIs<DeferredIssuance.Collection.Claimed>(deferred.collect("offer", "transaction", setOf("credential"), "wallet")).transaction
        suspend fun deliver(claim: DeferredTransaction? = null, encryption: CredentialResponseEncryptionParameters? = null): CredentialResponseResult {
            val current = claim ?: claim()
            return provider.createDeferredCredentialResponse(DeferredCredentialRequest("transaction", encryption), current.response ?: response,
                DeferredCredentialResponseContext("offer", current, deferred))
        }
    }
}
