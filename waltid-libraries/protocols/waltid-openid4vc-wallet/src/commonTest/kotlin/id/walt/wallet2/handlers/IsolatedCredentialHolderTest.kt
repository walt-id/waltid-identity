package id.walt.wallet2.handlers

import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.keys.*
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.wallet2.data.StoredCredential
import id.walt.wallet2.data.Wallet
import id.walt.wallet2.stores.inmemory.InMemoryCredentialStore
import io.ktor.client.HttpClient
import io.ktor.http.Url
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

class IsolatedCredentialHolderTest {
    @Test
    fun prooflessResponseArraysPreserveEveryCredentialWithoutInventingHolderBindings() = runTest {
        val fixture = batchTestFixture(true)
        for (w3c in listOf(false, true)) {
            val raw = credential(w3c = w3c)
            for (poll in listOf(false, true)) for (count in listOf(1, 2)) for (withDefault in listOf(false, true)) {
                val store = InMemoryCredentialStore()
                val wallet = if (withDefault) fixture.wallet.copy(credentialStores = listOf(store))
                    else Wallet("keyless", credentialStores = listOf(store))
                var requests = 0
                val callbacks = mutableListOf<String>()
                val batches = mutableListOf<Int>()
                val response = response(*Array(count) { raw })
                batchTestClient(credential = { requests++; response }, deferred = { requests++; response }).use { http ->
                    receive(poll, wallet, http, onStored = { callbacks += it.id }, beforeStored = { batches += it })
                }
                val stored = store.listCredentials().toList()
                assertEquals(count, stored.size)
                stored.forEach { assertNull(it.holderKeyBinding) }
                assertEquals(stored.map { it.id }.toSet(), callbacks.toSet())
                assertEquals(listOf(count), batches)
                assertEquals(1, requests)
                assertTrue(stored.all { it.credential.format == if (w3c) "jwt_vc_json" else "dc+sd-jwt" })
            }
        }
    }

    @Test
    fun bothGrantsStoreProoflessArraysImmediatelyOrAfterRetainedDeferredIssuance() = runTest {
        val metadata = Json.parseToJsonElement(batchTestMetadata()).jsonObject
        val configured = metadata.getValue("credential_configurations_supported").jsonObject.getValue("identity").jsonObject
        val prooflessMetadata = JsonObject(metadata + ("credential_configurations_supported" to
            buildJsonObject { put("identity", JsonObject(configured - "proof_types_supported" - "cryptographic_binding_methods_supported")) })).toString()
        val response = response(credential(), credential())
        for (authorized in listOf(false, true)) for (deferred in listOf(false, true)) {
            val fixture = batchTestFixture(true)
            val records = id.walt.wallet2.stores.inmemory.InMemoryIssuanceSessionStore()
            fixture.wallet.attachIssuanceSessionState(WalletIssuanceSessionState(fixture.wallet.id, records))
            var requests = 0
            var polls = 0
            val quotas = mutableListOf<Int>()
            val reported = mutableListOf<String>()
            batchTestClient(metadata = prooflessMetadata,
                credentialStatus = if (deferred) io.ktor.http.HttpStatusCode.Accepted else io.ktor.http.HttpStatusCode.OK,
                credential = {
                    requests++
                    assertFalse("proofs" in it)
                    if (deferred) """{"transaction_id":"transaction","interval":1}""" else response
                }, deferred = { polls++; response }).use { http ->
                val result = if (authorized) WalletIssuanceHandler.receiveCredentialsAuthCode(fixture.wallet,
                    ReceiveAuthorizedCredentialsRequest(listOf(fixture.selection(1)), "code",
                        credentialIssuer = BATCH_TEST_ISSUER, credentialEndpoint = Url("$BATCH_TEST_ISSUER/credential")),
                    httpClient = http, beforeCredentialsStored = { quotas += it }, onCredentialStored = { reported += it.id })
                else WalletIssuanceHandler.receiveCredentials(fixture.wallet, ReceiveCredentialRequest(offerJson = batchTestOffer()),
                    httpClient = http, beforeCredentialsStored = { quotas += it }, onCredentialStored = { reported += it.id })
                assertNull(result.failure)
                if (deferred) {
                    assertTrue(result.credentialIds.isEmpty())
                    val handle = assertNotNull(result.deferredCredentials.single().deferredCredentialId)
                    val restored = WalletIssuanceSessionService(fixture.wallet.copy(), httpClient = http, sessionStore = records,
                        now = { kotlin.time.Clock.System.now() + 2.seconds })
                    assertEquals(2, assertIs<WalletIssuanceOutcome.Stored>(restored.resumeDeferred(handle,
                        beforeCredentialsStored = { quotas += it }, onCredentialStored = { reported += it.id })).credentialIds.size)
                } else assertEquals(2, result.credentialIds.size)
            }
            assertEquals(1, requests)
            assertEquals(if (deferred) 1 else 0, polls)
            assertEquals(listOf(2), quotas)
            assertEquals(2, reported.toSet().size)
            assertTrue(fixture.store.listCredentials().toList().all { it.holderKeyBinding == null })
        }
    }

    @Test
    fun proofBoundResponsesStillRejectMultipleCredentialsForOneProofBeforeAnyWrite() = runTest {
        val fixture = batchTestFixture(true)
        val raw = batchTestCredential(fixture.keys.first())
        for (poll in listOf(false, true)) {
            val store = InMemoryCredentialStore()
            val wallet = fixture.wallet.copy(credentialStores = listOf(store))
            var requests = 0
            batchTestClient(credential = { requests++; response(raw, raw) },
                deferred = { requests++; response(raw, raw) }).use { http ->
                assertFailsWith<IllegalArgumentException> {
                    receive(poll, wallet, http, proof = true,
                        beforeStored = { error("Must validate cardinality before accounting") },
                        onStored = { error("Must validate cardinality before saving") })
                }
            }
            assertEquals(1, requests)
            assertTrue(store.listCredentials().toList().isEmpty())
        }
    }

    @Test
    fun missingOrUnresolvedHolderSelectionsFailBeforeTheRemoteResponseIsConsumed() = runTest {
        data class Selection(val keyId: String? = null,
            val holders: List<CredentialHolderBinding> = listOf(CredentialHolderBinding()),
            val proof: Boolean = false)
        val cases = listOf(
            Selection(keyId = "missing"),
            Selection(holders = listOf(CredentialHolderBinding("missing"))),
            Selection(holders = listOf(CredentialHolderBinding(did = "did:example:missing"))),
            Selection(proof = true),
            Selection(holders = List(2) { CredentialHolderBinding() }),
            Selection(holders = emptyList()),
        )
        for (poll in listOf(false, true)) for (selection in cases) {
            val store = InMemoryCredentialStore()
            var requests = 0
            batchTestClient(credential = { requests++; error("Unexpected fetch") },
                deferred = { requests++; error("Unexpected poll") }).use { http ->
                assertFailsWith<IllegalArgumentException> {
                    receive(poll, Wallet("keyless", credentialStores = listOf(store)), http,
                        selection.keyId, selection.holders, selection.proof)
                }
            }
            assertEquals(0, requests, "$poll / $selection")
            assertTrue(store.listCredentials().toList().isEmpty())
        }
    }

    @Test
    fun unownedAndMalformedConfirmationsCannotBeStoredAsBearerCredentials() = runTest {
        val fixture = batchTestFixture(true)
        val foreign = assertNotNull(fixture.keys.last().crypto2Key)
        val jwk = Json.parseToJsonElement(assertNotNull(foreign.capabilities.publicKeyExporter)
            .exportPublicKey().toPublicJwk(foreign.spec).data.toByteArray().decodeToString()).jsonObject
        val confirmations = listOf<JsonElement>(
            buildJsonObject { put("jwk", jwk) },
            buildJsonObject { put("jwk", "malformed") },
            buildJsonObject { put("jkt", "unsupported-thumbprint") },
            JsonPrimitive("malformed"),
            JsonNull,
        )
        for (confirmation in confirmations) {
            val response = response(credential(), credential(confirmation))
            for (poll in listOf(false, true)) for (withDefault in listOf(false, true)) {
                val store = InMemoryCredentialStore()
                val wallet = if (withDefault) fixture.wallet.copy(credentialStores = listOf(store))
                    else Wallet("keyless", credentialStores = listOf(store))
                var requests = 0
                var writes = 0
                batchTestClient(credential = { requests++; response }, deferred = { requests++; response }).use { http ->
                    assertFailsWith<Exception> {
                        receive(poll, wallet, http, onStored = { writes++ })
                    }
                }
                assertEquals(1, requests)
                assertEquals(0, writes)
                assertTrue(store.listCredentials().toList().isEmpty())
            }
        }
    }

    private suspend fun receive(
        poll: Boolean,
        wallet: Wallet,
        http: HttpClient,
        keyId: String? = null,
        holders: List<CredentialHolderBinding> = listOf(CredentialHolderBinding()),
        proof: Boolean = false,
        onStored: suspend (StoredCredential) -> Unit = {},
        beforeStored: suspend (Int) -> Unit = {},
    ) {
        if (poll) WalletIssuanceHandler.pollDeferredFlow(wallet,
            PollDeferredRequest(Url("$BATCH_TEST_ISSUER/deferred"), "transaction", "access", keyId = keyId,
                holderBindings = holders, proofRequired = proof), httpClient = http,
            beforeCredentialsStored = beforeStored, onCredentialStored = onStored).toList()
        else WalletIssuanceHandler.fetchCredential(wallet,
            FetchCredentialRequest(Url("$BATCH_TEST_ISSUER/credential"), "access", "identity",
                proofJwt = if (proof) "proof" else null, storeInWallet = true, keyId = keyId,
                holderBindings = holders), httpClient = http, beforeCredentialsStored = beforeStored, onCredentialStored = onStored)
    }

    private fun response(vararg raw: String) = buildJsonObject {
        putJsonArray("credentials") { raw.forEach { value -> add(buildJsonObject { put("credential", value) }) } }
    }.toString()

    private suspend fun credential(confirmation: JsonElement? = null, w3c: Boolean = false): String {
        val key = CryptoRuntime(defaultSoftwareKeyProviders()).generateSoftwareKey(
            GenerateSoftwareKeyRequest(KeyId("issuer"), KeySpec.Ec(EcCurve.P256), setOf(KeyUsage.SIGN)))
        val signed = CompactJws.sign(key = key, algorithm = JwsAlgorithm.ES256, payload = buildJsonObject {
            put("iss", BATCH_TEST_ISSUER)
            put("iat", 1700000000)
            if (w3c) {
                // A subject identifier alone does not turn proofless W3C issuance into holder-bound issuance.
                put("sub", "did:example:unrelated-subject")
                putJsonObject("vc") {
                    putJsonArray("@context") { add("https://www.w3.org/2018/credentials/v1") }
                    putJsonArray("type") { add("VerifiableCredential") }
                    putJsonObject("credentialSubject") { put("id", "did:example:unrelated-subject"); put("given_name", "Ada") }
                }
            } else {
                put("vct", "identity")
                put("given_name", "Ada")
                confirmation?.let { put("cnf", it) }
            }
        }.toString().encodeToByteArray(), protectedHeader = buildJsonObject { put("typ", if (w3c) "JWT" else "dc+sd-jwt") })
        return if (w3c) signed else "$signed~"
    }
}
