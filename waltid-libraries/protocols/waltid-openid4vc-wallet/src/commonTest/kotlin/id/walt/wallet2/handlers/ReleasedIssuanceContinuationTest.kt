package id.walt.wallet2.handlers

import id.walt.wallet2.data.StoredCredential
import id.walt.wallet2.data.WalletCredentialStore
import id.walt.wallet2.stores.inmemory.InMemoryIssuanceSessionStore
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlinx.serialization.encodeToString
import kotlin.test.*
import kotlin.time.Instant

class ReleasedIssuanceContinuationTest {
    @Test
    fun configuredReferenceRetainsReleasedSourceAndJsonContracts() {
        val original = WalletDeferredCredential("handle", "identity", 2L)
        val (_, configuration: String, interval) = original
        assertEquals("identity", configuration)
        assertEquals(2L, interval)
        val strict = Json { encodeDefaults = true }
        val wire = """{"id":"handle","credentialConfigurationId":"identity","intervalSeconds":2}"""
        assertEquals(wire, strict.encodeToString(original))
        assertEquals(original, strict.decodeFromString<WalletDeferredCredential>(wire))
        val dataset = WalletDeferredCredential("handle", "identity", 2L, "dataset")
        assertEquals("dataset", dataset.copy(intervalSeconds = 4L).credentialIdentifier)
        assertEquals(dataset, WalletDeferredCredential(WalletIssuanceContinuation(dataset)))
        val failed = WalletIssuanceOutcome.Failed("session",
            WalletIssuanceError(WalletIssuanceErrorCode.STORAGE, "Retry local save"),
            deferredCredentials = listOf(WalletIssuanceContinuation(dataset)),
            failure = CredentialIssuanceFailure(
                id.waltid.openid4vci.wallet.credential.CredentialIssuanceTarget("identity", "dataset"),
                CredentialIssuanceStage.STORAGE))
        val copied = failed.copy(storedCredentialIds = listOf("saved"))
        assertEquals(failed.deferredCredentials, copied.deferredCredentials)
        assertEquals(failed.failure, copied.failure)
        assertFailsWith<IllegalArgumentException> {
            strict.decodeFromString<WalletDeferredCredential>("""{"id":"handle","credentialConfigurationId":null,"intervalSeconds":2}""")
        }
    }

    @Test
    fun releasedArraySurvivesPendingRestartAndPartialStorageWithStoredOrStaticKey() = runTest {
        for (staticKey in listOf(false, true)) {
            val fixture = batchTestFixture(false)
            val records = InMemoryIssuanceSessionStore()
            val record = releasedRecord(fixture, staticKey)
            records.put(record)
            var current = Instant.fromEpochMilliseconds(1000)
            var writes = 0
            val flaky = object : WalletCredentialStore by fixture.store {
                override suspend fun addCredential(entry: StoredCredential) {
                    if (++writes == 2) error("Second storage write failed")
                    fixture.store.addCredential(entry)
                }
            }
            val wallet = fixture.wallet.copy(credentialStores = listOf(flaky),
                keyStores = if (staticKey) emptyList() else fixture.wallet.keyStores,
                staticKey = if (staticKey) fixture.keys.first().legacyKey else null)
            val credential = batchTestCredential(fixture.keys.first())
            var polls = 0
            val http = batchTestClient(credential = { error("Must not restart issuance") },
                deferredStatus = { if (polls == 0) HttpStatusCode.Accepted else HttpStatusCode.OK },
                deferred = {
                    assertEquals("released-transaction", it.getValue("transaction_id").jsonPrimitive.content)
                    val claimed = Json.parseToJsonElement(records.list().single().payload).jsonObject
                    assertTrue(claimed.getValue("claimed").jsonPrimitive.boolean)
                    assertEquals("released-single-key", claimed.getValue("content").jsonObject
                        .getValue("request").jsonObject.getValue("validation").jsonObject.getValue("type").jsonPrimitive.content)
                    if (++polls == 1) """{"transaction_id":"released-transaction","interval":1}"""
                    else response(credential, credential)
                })
            fun service() = WalletIssuanceSessionService(wallet.copy(), httpClient = http, sessionStore = records, now = { current })
            val original = service()
            val handle = original.listDeferredCredentials().single()
            assertEquals("released-handle", handle.id)
            assertEquals(record, records.list().single()) // Listing must not rewrite or claim a released record.
            assertEquals(2L, assertIs<WalletIssuanceOutcome.Deferred>(original.resumeDeferred(handle.id)).credentials.single().intervalSeconds)
            current = Instant.fromEpochMilliseconds(2999)
            assertEquals(1L, assertIs<WalletIssuanceOutcome.Deferred>(service().resumeDeferred(handle.id)).credentials.single().intervalSeconds)
            assertEquals(0, polls)
            current = Instant.fromEpochMilliseconds(3000)
            assertIs<WalletIssuanceOutcome.Deferred>(service().resumeDeferred(handle.id))
            val retained = records.list().single()
            val retainedPayload = Json.parseToJsonElement(retained.payload).jsonObject
            val invalidPublic = JsonObject(retainedPayload.getValue("public").jsonObject +
                    ("credentialConfigurationId" to JsonNull))
            records.put(retained.copy(payload = JsonObject(retainedPayload + ("public" to invalidPublic)).toString()))
            assertFailsWith<IllegalArgumentException> { service().listIssuanceContinuations() }
            assertEquals(1, polls)
            records.put(retained)
            current = Instant.fromEpochMilliseconds(4000)
            val partial = assertIs<WalletIssuanceOutcome.Failed>(service().resumeDeferred(handle.id))
            assertEquals(WalletIssuanceErrorCode.STORAGE, partial.error.code)
            assertEquals(1, partial.storedCredentialIds.size)
            val callbacks = mutableListOf<String>()
            val stored = assertIs<WalletIssuanceOutcome.Stored>(service().resumeDeferred(handle.id,
                beforeCredentialsStored = { assertEquals(1, it) }, onCredentialStored = { callbacks += it.id }))
            assertEquals(2, stored.credentialIds.size)
            assertEquals(1, callbacks.size)
            assertTrue(partial.storedCredentialIds.single() in stored.credentialIds)
            assertEquals(2, polls)
            assertTrue(records.list().isEmpty())
            val saved = fixture.store.listCredentials().toList()
            assertEquals(2, saved.size)
            saved.forEach {
                assertEquals("Released credential", it.label)
                assertEquals("retained", it.metadata?.get("custom")?.jsonPrimitive?.content)
                assertNotNull(it.holderKeyBinding)
            }
        }
    }

    @Test
    fun invalidReleasedBindingsFailBeforePollingWithoutConsumingTheRecord() = runTest {
        for (invalid in listOf("wrong-key", "missing-key", "missing-field", "session-mismatch")) {
            val fixture = batchTestFixture(false)
            val original = releasedRecord(fixture)
            val payload = Json.parseToJsonElement(original.payload).jsonObject.toMutableMap()
            when (invalid) {
                "wrong-key" -> payload["selectedPublicJwk"] = JsonPrimitive(fixture.keys[1].legacyKey!!.getPublicKey().exportJWKObject().toString())
                "missing-key" -> payload["keyId"] = JsonPrimitive("missing")
                "missing-field" -> payload.remove("endpoint")
                "session-mismatch" -> payload["sessionId"] = JsonPrimitive("different")
            }
            val invalidRecord = original.copy(payload = JsonObject(payload).toString())
            val records = InMemoryIssuanceSessionStore().also { it.put(invalidRecord) }
            var polls = 0
            val http = batchTestClient(credential = { error("Must not restart issuance") }, deferred = { polls++; error("Must not poll") })
            val service = WalletIssuanceSessionService(fixture.wallet, httpClient = http, sessionStore = records,
                now = { Instant.fromEpochMilliseconds(3000) })
            assertEquals(WalletIssuanceErrorCode.STORAGE,
                assertIs<WalletIssuanceOutcome.Failed>(service.resumeDeferred("released-handle")).error.code)
            assertEquals(0, polls)
            assertEquals(invalidRecord, records.list().single())
            assertTrue(fixture.store.listCredentials().toList().isEmpty())
        }
    }

    @Test
    fun releasedBearerCredentialsRemainUnboundAndForeignHolderBindingsAreRejected() = runTest {
        for (foreignHolder in listOf(false, true)) {
            val fixture = batchTestFixture(false)
            val records = InMemoryIssuanceSessionStore().also { it.put(releasedRecord(fixture)) }
            val credential = if (foreignHolder) batchTestCredential(fixture.keys[1])
            else fixture.keys.first().legacyKey!!.signJws(
                """{"iss":"https://issuer.example","vct":"identity","given_name":"Ada"}""".encodeToByteArray(),
                mapOf("typ" to JsonPrimitive("dc+sd-jwt")),
            ) + "~"
            val http = batchTestClient(credential = { error("Must not restart issuance") }, deferred = { response(credential) })
            val service = WalletIssuanceSessionService(fixture.wallet, httpClient = http, sessionStore = records,
                now = { Instant.fromEpochMilliseconds(3000) })
            val result = service.resumeDeferred("released-handle")
            if (foreignHolder) {
                assertEquals(WalletIssuanceErrorCode.PROTOCOL, assertIs<WalletIssuanceOutcome.Failed>(result).error.code)
                assertTrue(fixture.store.listCredentials().toList().isEmpty())
            } else {
                assertEquals(1, assertIs<WalletIssuanceOutcome.Stored>(result).credentialIds.size)
                assertNull(fixture.store.listCredentials().toList().single().holderKeyBinding)
                assertTrue(records.list().isEmpty())
            }
        }
    }

    @Test
    fun aLostReleasedPollIsClaimedDurablyAndNeverReplayedAfterRestart() = runTest {
        val fixture = batchTestFixture(false)
        val records = InMemoryIssuanceSessionStore().also { it.put(releasedRecord(fixture)) }
        var polls = 0
        val http = batchTestClient(credential = { error("Must not restart issuance") }, deferred = { polls++; error("Response lost") })
        repeat(2) {
            val service = WalletIssuanceSessionService(fixture.wallet.copy(), httpClient = http, sessionStore = records,
                now = { Instant.fromEpochMilliseconds(3000) })
            assertEquals(WalletIssuanceErrorCode.REMOTE_OUTCOME_UNCERTAIN,
                assertIs<WalletIssuanceOutcome.Failed>(service.resumeDeferred("released-handle")).error.code)
        }
        assertEquals(1, polls)
        assertTrue(Json.parseToJsonElement(records.list().single().payload).jsonObject.getValue("claimed").jsonPrimitive.boolean)
        assertTrue(fixture.store.listCredentials().toList().isEmpty())
    }

    /** Field names and nulls copied from the flat 1.1.0 shape at 29a5a58; keys are generated test material. */
    private suspend fun releasedRecord(fixture: BatchTestFixture, staticKey: Boolean = false): WalletIssuanceSessionRecord {
        val key = fixture.keys.first()
        val keyId = if (staticKey) JsonNull else JsonPrimitive(key.keyId)
        val publicJwk = JsonPrimitive(key.legacyKey!!.getPublicKey().exportJWKObject().toString())
        return WalletIssuanceSessionRecord("deferred:released-handle", "released-session",
            WalletIssuanceSessionRecordKind.DEFERRED_CREDENTIAL, """{
                "public":{"id":"released-handle","credentialConfigurationId":"identity","intervalSeconds":2},
                "sessionId":"released-session","endpoint":"$BATCH_TEST_ISSUER/deferred",
                "transactionId":"released-transaction","accessToken":"released-token","tokenType":"Bearer",
                "dpop":null,"dpopNonce":null,"keyId":$keyId,"selectedPublicJwk":$publicJwk,
                "label":"Released credential","metadata":{"custom":"retained"}
            }""".trimIndent(), 1000)
    }

    private fun response(vararg credentials: String) = buildJsonObject {
        put("credentials", JsonArray(credentials.map { buildJsonObject { put("credential", it) } }))
    }.toString()
}
