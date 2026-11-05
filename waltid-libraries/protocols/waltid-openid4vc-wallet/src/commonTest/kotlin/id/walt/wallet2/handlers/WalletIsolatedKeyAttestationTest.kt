package id.walt.wallet2.handlers

import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.jose.exportPublicJwkObject
import id.walt.crypto2.keys.KeyUsage
import id.walt.wallet2.data.StoredCredential
import id.walt.wallet2.data.WalletCredentialStore
import id.walt.wallet2.data.resolveHolderKey
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*
import kotlin.time.Clock

class WalletIsolatedKeyAttestationTest {
    @Test fun oneProofCanFetchAllFewerOrReorderedAttestedKeysWithOrWithoutStorage() = runTest {
        for (store in listOf(false, true)) for (released in listOf(false, true))
            for (indices in listOf(listOf(0, 1, 2), listOf(0, 2), listOf(2, 1, 0))) {
                val fixture = batchTestFixture(true)
                val proof = fixture.attestedProof(listOf(0, 1, 2))
                val credentials = indices.map { batchTestCredential(fixture.keys[it]) }
                var requests = 0
                var quota = 0
                var saved = 0
                batchTestClient(metadata = singleProofMetadata(), credential = {
                    requests++
                    assertEquals(listOf(proof), it.batchProofs())
                    response(credentials)
                }).use { http ->
                    val result = WalletIssuanceHandler.fetchCredentials(if (store) fixture.wallet else id.walt.wallet2.data.Wallet("stateless"),
                        request(fixture, listOf(proof), store, released), http,
                        beforeCredentialsStored = { quota++; assertEquals(indices.size, it) },
                        onCredentialStored = { saved++ })
                    assertEquals(credentials, result.rawCredentials)
                    assertEquals(if (store) 1 else 0, quota)
                    assertEquals(if (store) indices.size else 0, saved)
                }
                assertEquals(1, requests)
                val stored = fixture.store.listCredentials().toList()
                assertEquals(if (store) indices.size else 0, stored.size)
                if (store) assertEquals(indices.map { fixture.keys[it].keyId }.toSet(), stored.map {
                    fixture.wallet.resolveHolderKey(it, setOf(KeyUsage.SIGN)).keyMaterial.keyId
                }.toSet())
            }
    }

    @Test fun expectedAttestedKeysDoNotSelectOrRequireEveryWalletBinding() = runTest {
        for (selected in listOf(listOf(0, 2), listOf(0, 1))) {
            val fixture = batchTestFixture(true)
            val proof = fixture.attestedProof(listOf(0, 1, 2))
            val raw = listOf(0, 2).map { batchTestCredential(fixture.keys[it]) }
            var callbacks = 0
            batchTestClient(credential = { response(raw) }).use { http ->
                val req = request(fixture, listOf(proof), true).copy(
                    holderBindings = selected.map { CredentialHolderBinding(fixture.keys[it].keyId) })
                if (selected == listOf(0, 2)) {
                    val result = WalletIssuanceHandler.fetchCredentials(fixture.wallet, req, http)
                    assertEquals(2, assertIs<WalletIssuanceOutcome.Stored>(result.storageOutcome).credentialIds.size)
                } else {
                    assertFailsWith<IllegalStateException> {
                        WalletIssuanceHandler.fetchCredentials(fixture.wallet, req, http,
                            beforeCredentialsStored = { callbacks++ }, onCredentialStored = { callbacks++ })
                    }
                    assertEquals(0, callbacks)
                    assertTrue(fixture.store.listCredentials().toList().isEmpty())
                }
            }
        }
    }

    @Test fun sharedAndOverlappingAttestationsDeduplicatePublicKeysRatherThanProofs() = runTest {
        for (shared in listOf(false, true)) {
            val fixture = batchTestFixture(true)
            val first = fixture.attestedProof(if (shared) listOf(0, 1, 2) else listOf(0, 1))
            val second = if (shared) CompactJws.sign(
                CompactJws.decodeUnverified(first).payload,
                fixture.keys[1].crypto2Key!!, JwsAlgorithm.ES256,
                buildJsonObject {
                    put("typ", "openid4vci-proof+jwt")
                    put("jwk", fixture.keys[1].crypto2Key!!.exportPublicJwkObject())
                    put("key_attestation", CompactJws.decodeUnverified(first).protectedHeader.getValue("key_attestation"))
                },
            ) else fixture.attestedProof(listOf(1, 2), signer = 1)
            batchTestClient(metadata = batchTestMetadata(2), credential = {
                assertEquals(2, it.batchProofs().size)
                response(listOf(2, 0, 1).map { index -> batchTestCredential(fixture.keys[index]) })
            }).use { http ->
                val result = WalletIssuanceHandler.fetchCredentials(fixture.wallet,
                    request(fixture, listOf(first, second), true), http)
                assertEquals(3, result.rawCredentials.size)
                assertEquals(3, fixture.store.listCredentials().toList().size)
            }
        }
    }

    @Test fun batchLimitStillCountsEverySubmittedProofBeforeContactingTheIssuer() = runTest {
        val fixture = batchTestFixture(true)
        val proof = fixture.attestedProof(listOf(0, 1, 2))
        var requests = 0
        batchTestClient(metadata = batchTestMetadata(2), credential = { requests++; error("Must reject oversized proofs") }).use { http ->
            assertFailsWith<IllegalArgumentException> {
                WalletIssuanceHandler.fetchCredentials(fixture.wallet, request(fixture, List(3) { proof }, true), http)
            }
        }
        assertEquals(0, requests)
    }

    @Test fun aLateInvalidCredentialPreventsAllWritesAndStorageCallbacks() = runTest {
        for (store in listOf(false, true)) for (invalid in listOf("foreign", "duplicate", "unbound", "malformed")) {
            val fixture = batchTestFixture(true)
            val proof = fixture.attestedProof(listOf(0, 1, 2))
            var writes = 0
            var callbacks = 0
            var requests = 0
            val wallet = fixture.wallet.copy(credentialStores = listOf(object : WalletCredentialStore by fixture.store {
                override suspend fun addCredential(entry: StoredCredential) { writes++; fixture.store.addCredential(entry) }
            }))
            val bad = when (invalid) {
                "foreign" -> batchTestCredential(fixture.keys[3])
                "duplicate" -> batchTestCredential(fixture.keys[0])
                "unbound" -> batchTestCredential(publicJwk = null)
                else -> "not-a-credential"
            }
            val raw = listOf(batchTestCredential(fixture.keys[0]), batchTestCredential(fixture.keys[1]), bad)
            batchTestClient(credential = { requests++; response(raw) }).use { http ->
                val failure = assertFailsWith<Exception> {
                    WalletIssuanceHandler.fetchCredentials(wallet, request(fixture, listOf(proof), store), http,
                        beforeCredentialsStored = { callbacks++ }, onCredentialStored = { callbacks++ })
                }
                if (invalid == "foreign" || invalid == "duplicate") {
                    assertEquals("Issued credential is bound to an unexpected or repeated holder key", failure.message)
                }
            }
            assertEquals(1, requests, "$store / $invalid")
            assertEquals(0, writes, "$store / $invalid")
            assertEquals(0, callbacks, "$store / $invalid")
            assertTrue(fixture.store.listCredentials().toList().isEmpty())
        }
    }

    @Test fun selectedWalletKeysMustBeAvailableOwnedAndDistinctBeforeTheCredentialRequest() = runTest {
        val fixture = batchTestFixture(true)
        val proof = fixture.attestedProof(listOf(0, 1, 2))
        val unavailable = listOf(CredentialHolderBinding("unavailable"))
        val duplicate = List(2) { CredentialHolderBinding(fixture.keys.first().keyId) }
        for (bindings in listOf(unavailable, duplicate)) {
            var requests = 0
            batchTestClient(credential = { requests++; error("Must resolve selected keys first") }).use { http ->
                assertFailsWith<IllegalArgumentException> {
                    WalletIssuanceHandler.fetchCredentials(fixture.wallet,
                        request(fixture, listOf(proof), true).copy(holderBindings = bindings), http)
                }
            }
            assertEquals(0, requests)
        }
    }

    @Test fun issuerProofAndNonceErrorsRemainStructuredAndAreNotPreemptedByWalletParsing() = runTest {
        val fixture = batchTestFixture(true)
        for (code in listOf("invalid_proof", "invalid_nonce")) {
            var requests = 0
            val http = HttpClient(MockEngine) {
                engine { addHandler {
                    if (it.url.encodedPath.endsWith("openid-credential-issuer")) respond(singleProofMetadata(),
                        headers = headersOf(HttpHeaders.ContentType, "application/json"))
                    else {
                        requests++
                        respond("""{"error":"$code","error_description":"issuer rejected evidence"}""",
                            HttpStatusCode.BadRequest, headersOf(HttpHeaders.ContentType, "application/json"))
                    }
                } }
                install(ContentNegotiation) { json() }
            }
            http.use {
                val error = assertFailsWith<CredentialEndpointException> {
                    // Parsing this evidence would fail. The issuer owns its rejection and error details.
                    WalletIssuanceHandler.fetchCredentials(fixture.wallet, request(fixture, listOf("invalid-proof"), true), it)
                }
                assertEquals(code, error.credentialError?.error)
                assertEquals("issuer rejected evidence", error.credentialError?.description)
            }
            assertEquals(1, requests)
            assertTrue(fixture.store.listCredentials().toList().isEmpty())
        }
    }

    @Test fun deferredResponsePreservesAllSelectedBindingsWithoutNewClaimTimeBehavior() = runTest {
        val fixture = batchTestFixture(true)
        val proof = fixture.attestedProof(listOf(0, 1, 2))
        batchTestClient(credentialStatus = HttpStatusCode.Accepted,
            credential = { """{"transaction_id":"tx","interval":2}""" }).use { http ->
            val result = WalletIssuanceHandler.fetchCredentials(fixture.wallet, request(fixture, listOf(proof), true), http,
                beforeCredentialsStored = { error("Deferred response must not reserve storage") },
                onCredentialStored = { error("Deferred response must not store credentials") })
            val deferred = assertNotNull(result.deferredCredential)
            assertEquals(fixture.selection(3).holderBindings, deferred.holderBindings)
            assertTrue(deferred.proofRequired)
            assertTrue(fixture.store.listCredentials().toList().isEmpty())
        }
    }

    private fun request(fixture: BatchTestFixture, proofs: List<String>, store: Boolean, released: Boolean = false) =
        FetchCredentialRequest(Url("$BATCH_TEST_ISSUER/credential"), "access", "identity",
            proofJwt = if (released) proofs.single() else null,
            proofs = if (released) null else id.walt.openid4vci.proofs.Proofs(jwt = proofs),
            storeInWallet = store, credentialIssuerBaseUrl = BATCH_TEST_ISSUER,
            holderBindings = fixture.selection(3).holderBindings)

    private suspend fun BatchTestFixture.attestedProof(indices: List<Int>, signer: Int = 0): String {
        val now = Clock.System.now().epochSeconds
        val attester = keys.last().crypto2Key!!
        val attestation = CompactJws.sign(buildJsonObject {
            put("iat", now); put("exp", now + 300); put("nonce", "nonce")
            put("attested_keys", JsonArray(indices.map { keys[it].crypto2Key!!.exportPublicJwkObject() }))
        }.toString().encodeToByteArray(), attester, JwsAlgorithm.ES256,
            buildJsonObject { put("typ", "key-attestation+jwt") })
        return CompactJws.sign(buildJsonObject {
            put("aud", BATCH_TEST_ISSUER); put("iat", now); put("nonce", "nonce")
        }.toString().encodeToByteArray(), keys[signer].crypto2Key!!, JwsAlgorithm.ES256,
            buildJsonObject {
                put("typ", "openid4vci-proof+jwt"); put("jwk", keys[signer].crypto2Key!!.exportPublicJwkObject())
                put("key_attestation", attestation)
            })
    }

    private fun singleProofMetadata() =
        JsonObject(Json.parseToJsonElement(batchTestMetadata()).jsonObject - "batch_credential_issuance").toString()

    private fun response(credentials: List<String>) = buildJsonObject {
        put("credentials", JsonArray(credentials.map { buildJsonObject { put("credential", it) } }))
    }.toString()
}
