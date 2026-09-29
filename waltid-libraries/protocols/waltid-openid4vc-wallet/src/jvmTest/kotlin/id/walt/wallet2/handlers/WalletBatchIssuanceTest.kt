package id.walt.wallet2.handlers

import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.keys.*
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.wallet2.data.*
import id.walt.wallet2.stores.inmemory.InMemoryCredentialStore
import id.walt.wallet2.stores.inmemory.InMemoryKeyStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.*
import io.ktor.http.content.OutgoingContent
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicReference
import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import id.walt.verifier.openid.transactiondata.TransactionDataTypeRegistry
import id.waltid.openid4vp.wallet.presentation.ScaAuthenticationMethods
import id.waltid.openid4vp.wallet.presentation.ScaPresentation
import kotlin.test.*
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
class WalletBatchIssuanceTest {
    private var current = Clock.System.now()
    private val clockedServices = mutableSetOf<WalletIssuanceSessionService>()

    private fun newSessionService(
        wallet: Wallet,
        httpClient: HttpClient,
        sessionStore: WalletIssuanceSessionStore? = null,
        onEvent: suspend (WalletSessionEvent) -> Unit = {},
    ) = WalletIssuanceSessionService(wallet, httpClient = httpClient, sessionStore = sessionStore,
        onEvent = onEvent, now = { current }).also { clockedServices += it }

    /** Reach polling in tests of later stages; timing boundaries use resumeDeferred directly below. */
    private suspend fun WalletIssuanceSessionService.resumeWhenDue(
        id: String,
        beforeCredentialsStored: suspend (Int) -> Unit = {},
        onCredentialStored: suspend (StoredCredential) -> Unit = {},
    ): WalletIssuanceOutcome {
        listDeferredCredentials().firstOrNull { it.id == id }?.intervalSeconds?.let { interval ->
            if (this in clockedServices) current = maxOf(current, Clock.System.now()) + interval.seconds
            else withContext(Dispatchers.Default) { delay(interval.seconds) }
        }
        return resumeDeferred(id, beforeCredentialsStored, onCredentialStored)
    }

    @Test fun contradictoryOrBlankHolderSelectorsCannotEnterTheIssuanceFlow() = runTest {
        val inline = id.walt.crypto.keys.DirectSerializedKey(JWKKey.generate(KeyType.secp256r1))
        assertFailsWith<IllegalArgumentException> { CredentialHolderBinding(keyId = "stored", key = inline) }
        val encoded = Json.encodeToJsonElement(CredentialHolderBinding(key = inline)).jsonObject
        assertFailsWith<IllegalArgumentException> {
            Json.decodeFromJsonElement<CredentialHolderBinding>(JsonObject(encoded + ("keyId" to JsonPrimitive("stored"))))
        }
        assertFailsWith<IllegalArgumentException> { CredentialHolderBinding(keyId = " ") }
        assertFailsWith<IllegalArgumentException> { CredentialHolderBinding(did = " ") }
    }

    @Test fun deferredIntervalsSurviveRestartAndEarlyCallsDoNotMoveTheDeadline() = runTest {
        val fixture = fixture(true)
        val records = MemorySessionStore()
        var proofs = emptyList<String>()
        var polls = 0
        val http = client(credentialStatus = HttpStatusCode.Accepted, credential = {
            proofs = it.proofs()
            """{"transaction_id":"timed","interval":2}"""
        }, deferredStatus = { if (polls == 0) HttpStatusCode.Accepted else HttpStatusCode.OK },
            deferred = {
                if (++polls == 1) """{"transaction_id":"timed","interval":3}""" else batchTestResponse(proofs)
            })
        current = Instant.fromEpochMilliseconds(1000)
        val service = newSessionService(fixture.wallet, http, records)
        val review = service.start(WalletIssuanceSessionRequest(offerJson = offer()))
        val pending = assertIs<WalletIssuanceOutcome.Deferred>(service.continuePreAuthorized(review.id)).credentials.single()
        val checkpoint = records.list().single()
        val restored = newSessionService(fixture.wallet, http, records)
        assertEquals(2L, assertIs<WalletIssuanceOutcome.Deferred>(restored.resumeDeferred(pending.id,
            beforeCredentialsStored = { error("Early polling must not reserve usage") })).credentials.single().intervalSeconds)
        current += 1999.milliseconds
        assertEquals(1L, assertIs<WalletIssuanceOutcome.Deferred>(restored.resumeDeferred(pending.id)).credentials.single().intervalSeconds)
        assertEquals(0, polls)
        assertEquals(checkpoint, records.list().single())
        current += 1.milliseconds
        assertEquals(3L, assertIs<WalletIssuanceOutcome.Deferred>(restored.resumeDeferred(pending.id)).credentials.single().intervalSeconds)
        assertEquals(1, polls)
        val updated = records.list().single()
        val restarted = newSessionService(fixture.wallet, http, records)
        current += 2999.milliseconds
        assertEquals(1L, assertIs<WalletIssuanceOutcome.Deferred>(restarted.resumeDeferred(pending.id)).credentials.single().intervalSeconds)
        assertEquals(updated, records.list().single())
        current += 1.milliseconds
        assertEquals(1, assertIs<WalletIssuanceOutcome.Stored>(restarted.resumeDeferred(pending.id)).credentialIds.size)
        assertEquals(2, polls)
    }

    @Test fun anExtremeIssuerIntervalCannotOverflowIntoAnImmediatelyDuePoll() = runTest {
        val fixture = fixture(true)
        val http = client(credentialStatus = HttpStatusCode.Accepted,
            credential = { """{"transaction_id":"long-wait","interval":${Long.MAX_VALUE}}""" },
            deferred = { error("An overflowing interval must not reach the issuer") })
        val service = newSessionService(fixture.wallet, http)
        val review = service.start(WalletIssuanceSessionRequest(offerJson = offer()))
        val pending = assertIs<WalletIssuanceOutcome.Deferred>(service.continuePreAuthorized(review.id)).credentials.single()
        current += 1.seconds
        assertTrue(requireNotNull(assertIs<WalletIssuanceOutcome.Deferred>(service.resumeDeferred(pending.id))
            .credentials.single().intervalSeconds) > 0)
    }

    @Test fun preAuthorizedReceiveNegotiatesFromMetadataAndHonorsIdentifiersEvenAfterScopeAuthorization() = runTest {
        for (detailsSupported in listOf(false, true)) for (returnsIdentifiers in listOf(false, true)) {
            if (detailsSupported && !returnsIdentifiers) continue
            val fixture = fixture(true)
            var tokenCalls = 0
            val targets = mutableListOf<String>()
            val result = WalletIssuanceHandler.receiveCredential(fixture.wallet,
                ReceiveCredentialRequest(offerJson = offer(), credentials = listOf(fixture.selection(2))),
                httpClient = client(authorizationDetailsSupported = detailsSupported, token = { parameters ->
                    tokenCalls++
                    assertAutomaticParameters(parameters, detailsSupported)
                    negotiatedToken(returnsIdentifiers)
                }, credential = { body ->
                    if (returnsIdentifiers) {
                        assertNull(body["credential_configuration_id"])
                        targets += body.getValue("credential_identifier").jsonPrimitive.content
                    } else {
                        assertNull(body["credential_identifier"])
                        targets += body.getValue("credential_configuration_id").jsonPrimitive.content
                    }
                    batchTestResponse(body.proofs())
                }))
            assertEquals(1, tokenCalls)
            assertEquals(if (returnsIdentifiers) listOf("dataset-a", "dataset-b") else listOf("identity"), targets)
            assertEquals(targets.size * 2, result.credentialIds.size)
        }
    }

    @Test fun isolatedTokenAndAuthorizationUrlAutomaticallyNegotiateWithoutCallerSwitches() = runTest {
        for (detailsSupported in listOf(false, true)) {
            val fixture = fixture(true)
            var tokenCalls = 0
            val http = client(authorizationDetailsSupported = detailsSupported, token = { parameters ->
                tokenCalls++
                assertAutomaticParameters(parameters, detailsSupported)
                negotiatedToken(detailsSupported)
            }, credential = { error("Isolated authorization must not issue credentials") })
            val result = WalletIssuanceHandler.requestToken(fixture.wallet,
                RequestTokenRequest(tokenEndpoint = Url("$ISSUER/token"), preAuthorizedCode = "pre-code",
                    credentialIssuer = ISSUER, credentialConfigurationIds = listOf("identity")), httpClient = http)
            assertEquals("access", result.accessToken)
            assertEquals(1, tokenCalls)
            for (withOffer in listOf(false, true)) {
                val authorization = WalletIssuanceHandler.generateAuthorizationUrl(fixture.wallet,
                    GenerateAuthorizationUrlRequest(
                        credentialIssuer = ISSUER.takeUnless { withOffer },
                        offerJson = offer().takeIf { withOffer },
                        credentialConfigurationIds = listOf("identity")), httpClient = http)
                assertAutomaticParameters(Url(authorization.authorizationUrl.toString()).parameters, detailsSupported)
            }
        }
    }

    @Test fun pushedAuthorizationNegotiatesForBothStatelessAndRetainedFlows() = runTest {
        for (detailsSupported in listOf(false, true)) {
            val fixture = fixture(true)
            var pushedRequests = 0
            val http = client(authorizationDetailsSupported = detailsSupported,
                par = { parameters ->
                    pushedRequests++
                    assertAutomaticParameters(parameters, detailsSupported)
                }, credential = { error("Authorization must not issue credentials") })
            WalletIssuanceHandler.generateAuthorizationUrl(fixture.wallet,
                GenerateAuthorizationUrlRequest(credentialIssuer = ISSUER, credentialConfigurationIds = listOf("identity")), httpClient = http)
            val service = newSessionService(fixture.wallet, httpClient = http)
            val preview = service.start(WalletIssuanceSessionRequest(credentialIssuer = ISSUER, credentialConfigurationIds = listOf("identity")))
            val authorization = service.beginAuthorization(preview.id, listOf(fixture.selection(2)))
            assertTrue(authorization.pushedAuthorizationRequestUsed)
            assertEquals(2, pushedRequests)
        }
    }

    @Test fun missingAuthorizationCapabilitiesFailBeforeRedeemingPreAuthorizedCode() = runTest {
        val fixture = fixture(true)
        var tokenCalls = 0
        assertFailsWith<IllegalArgumentException> {
            WalletIssuanceHandler.receiveCredential(fixture.wallet, ReceiveCredentialRequest(offerJson = offer()),
                httpClient = client(metadata = metadata().replace("\"scope\":\"identity\",", ""),
                    authorizationDetailsSupported = false,
                    token = { tokenCalls++; TOKEN }, credential = { error("Credential request must not be sent") }))
        }
        assertEquals(0, tokenCalls)
    }

    @Test fun retainedSessionsNegotiateBothGrantsAndPreserveBatchSelectionsAcrossRestart() = runTest {
        for (detailsSupported in listOf(false, true)) for (authorized in listOf(false, true)) {
            val fixture = fixture(true)
            val records = MemorySessionStore()
            val http = client(authorizationDetailsSupported = detailsSupported, token = { parameters ->
                if (!authorized) assertAutomaticParameters(parameters, detailsSupported)
                negotiatedToken(detailsSupported)
            }, credential = { batchTestResponse(it.proofs()) })
            val service = newSessionService(fixture.wallet, httpClient = http, sessionStore = records)
            val preview = service.start(if (authorized) WalletIssuanceSessionRequest(
                credentialIssuer = ISSUER, credentialConfigurationIds = listOf("identity"), redirectUri = Url("openid://callback"))
                else WalletIssuanceSessionRequest(offerJson = offer()))
            val result = if (authorized) {
                val authorization = service.beginAuthorization(preview.id, listOf(fixture.selection(2)))
                assertAutomaticParameters(Url(authorization.url).parameters, detailsSupported)
                val restored = newSessionService(fixture.wallet, httpClient = http, sessionStore = records)
                restored.continueAuthorization(WalletIssuanceAuthorizationCallback(preview.id,
                    "openid://callback?code=code&state=${authorization.state}"))
            } else {
                service.continuePreAuthorized(preview.id, credentials = listOf(fixture.selection(2)))
            }
            assertEquals(if (detailsSupported) 4 else 2, assertIs<WalletIssuanceOutcome.Stored>(result).credentialIds.size)
        }
    }

    private fun assertAutomaticParameters(parameters: Parameters, detailsSupported: Boolean) {
        if (detailsSupported) {
            assertNull(parameters["scope"])
            val details = Json.parseToJsonElement(assertNotNull(parameters["authorization_details"])).jsonArray
            assertEquals("identity", details.single().jsonObject.getValue("credential_configuration_id").jsonPrimitive.content)
        } else {
            assertEquals("identity", parameters["scope"])
            assertNull(parameters["authorization_details"])
        }
    }

    private fun negotiatedToken(identifiers: Boolean): String = if (identifiers)
        """{"access_token":"access","token_type":"Bearer","authorization_details":[
            {"type":"openid_credential","credential_configuration_id":"identity","credential_identifiers":["dataset-a","dataset-b"]}]}"""
        else """{"access_token":"access","token_type":"Bearer","scope":"identity"}"""

    @Test fun singleIsDefaultAndExplicitBatchesMatchReorderedCrypto1AndCrypto2Keys() = runTest {
        for (crypto2 in listOf(false, true)) for (count in listOf(1, 2, 5)) {
            val fixture = fixture(crypto2)
            val received = mutableListOf<JsonObject>()
            val events = mutableListOf<String>()
            val client = client(credential = { body ->
                received += body
                batchTestResponse(body.proofs().reversed())
            })
            val result = WalletIssuanceHandler.receiveCredential(fixture.wallet,
                ReceiveCredentialRequest(offerJson = offer(),
                    credentials = if (count == 1) null else listOf(fixture.selection(count))),
                httpClient = client,
                beforeCredentialsStored = { events += "before:$it" },
                onCredentialStored = { events += "stored" })
            assertEquals(count, result.credentialIds.size)
            assertEquals(count, received.single().proofs().size)
            assertEquals(listOf("before:$count") + List(count) { "stored" }, events)
            val stored = fixture.store.listCredentials().toList()
            val selected = fixture.keys.take(count).reversed()
            for ((credential, key) in stored.zip(selected)) {
                assertEquals(key.keyId, fixture.wallet.resolveHolderKey(credential, setOf(KeyUsage.SIGN)).keyMaterial.keyId)
            }
        }
    }

    @Test fun acceptsFewerCredentialsWithoutAssigningTheFirstProofsKey() = runTest {
        val fixture = fixture(true)
        val result = WalletIssuanceHandler.receiveCredential(fixture.wallet,
            ReceiveCredentialRequest(offerJson = offer(), credentials = listOf(fixture.selection(5))),
            httpClient = client(credential = { batchTestResponse(listOf(it.proofs().last())) }))
        assertEquals(1, result.credentialIds.size)
        val stored = fixture.store.listCredentials().toList().single()
        assertEquals(fixture.keys.last().keyId, fixture.wallet.resolveHolderKey(stored, setOf(KeyUsage.SIGN)).keyMaterial.keyId)
    }

    @Test fun deferredResultsRetainProofBindingValidationForSingleAndBatchIssuance() = runTest {
        for (count in listOf(1, 2)) {
            val fixture = fixture(true)
            val result = WalletIssuanceHandler.receiveCredential(fixture.wallet,
                ReceiveCredentialRequest(offerJson = offer(), credentials = listOf(fixture.selection(count))),
                httpClient = client(credentialStatus = HttpStatusCode.Accepted,
                    credential = { """{"transaction_id":"deferred-target","interval":5}""" }))
            assertTrue(result.credentialIds.isEmpty())
            val deferred = result.deferredCredentials.single()
            assertTrue(deferred.proofRequired)
            assertEquals(5, deferred.intervalSeconds)
            assertEquals(fixture.keys.take(count).map { it.keyId }, deferred.holderBindings.map { it.keyId })
            assertTrue(deferred.holderBindings.all { it.key == null })
        }
    }

    @Test fun repeatedHolderKeysRemainPermitted() = runTest {
        val fixture = fixture(false)
        val result = WalletIssuanceHandler.receiveCredential(fixture.wallet,
            ReceiveCredentialRequest(offerJson = offer(), credentials = listOf(WalletCredentialSelection("identity",
                holderBindings = List(2) { CredentialHolderBinding(fixture.keys.first().keyId) }))),
            httpClient = client(credential = { batchTestResponse(it.proofs()) }))
        assertEquals(2, result.credentialIds.size)
    }

    @Test fun unknownHolderKeyInLastResponseEntryPreventsAllWritesAndCallbacks() = runTest {
        val fixture = fixture(true)
        val foreign = batchTestCredential(JWKKey.generate(KeyType.secp256r1))
        var before = 0
        var stored = 0
        val result = WalletIssuanceHandler.receiveCredential(fixture.wallet,
            ReceiveCredentialRequest(offerJson = offer(), credentials = listOf(fixture.selection(2))),
            httpClient = client(credential = { request ->
                val first = Json.parseToJsonElement(batchTestResponse(request.proofs().take(1))).jsonObject["credentials"]!!.jsonArray.single()
                buildJsonObject { put("credentials", JsonArray(listOf(first, buildJsonObject { put("credential", foreign) }))) }.toString()
            }), beforeCredentialsStored = { before++ }, onCredentialStored = { stored++ })
        assertEquals(CredentialIssuanceStage.RESPONSE, assertNotNull(result.failure).stage)
        assertTrue(result.credentialIds.isEmpty())
        assertEquals(0, before)
        assertEquals(0, stored)
        assertTrue(fixture.store.listCredentials().toList().isEmpty())
    }

    @Test fun batchesAboveMetadataLimitFailBeforeRedeemingToken() = runTest {
        val fixture = fixture(true)
        var tokenCalls = 0
        assertFailsWith<IllegalArgumentException> {
            WalletIssuanceHandler.receiveCredential(fixture.wallet,
                ReceiveCredentialRequest(offerJson = offer(), credentials = listOf(fixture.selection(5))),
                httpClient = client(metadata = metadata(2), token = { tokenCalls++; TOKEN },
                    credential = { error("Credential request must not be sent") }))
        }
        assertEquals(0, tokenCalls)
    }

    @Test fun tokenDatasetsProduceSeparateRequestsUsingTheSameToken() = runTest {
        val fixture = fixture(true)
        var tokenCalls = 0
        val targets = mutableListOf<String>()
        val result = WalletIssuanceHandler.receiveCredential(fixture.wallet,
            ReceiveCredentialRequest(offerJson = offer(), credentials = listOf(fixture.selection(2))),
            httpClient = client(token = {
                tokenCalls++
                """{"access_token":"access","token_type":"Bearer","authorization_details":[
                    {"type":"openid_credential","credential_configuration_id":"identity","credential_identifiers":["dataset-a","dataset-b"]}]}"""
            }, credential = { body ->
                assertNull(body["credential_configuration_id"])
                targets += body["credential_identifier"]!!.jsonPrimitive.content
                batchTestResponse(body.proofs())
            }))
        assertEquals(1, tokenCalls)
        assertEquals(listOf("dataset-a", "dataset-b"), targets)
        assertEquals(4, result.credentialIds.size)
    }

    @Test fun laterTargetFailureReportsPriorProgressAndUnattemptedTargetsForBothGrants() = runTest {
        for (authorized in listOf(false, true)) for (deferred in listOf(false, true)) {
            val fixture = fixture(true)
            val requested = mutableListOf<String>()
            var tokenCalls = 0
            val http = client(token = {
                tokenCalls++
                """{"access_token":"access","token_type":"Bearer","authorization_details":[
                    {"type":"openid_credential","credential_configuration_id":"identity","credential_identifiers":["a","b","c"]}]}"""
            }, credentialStatus = if (deferred) HttpStatusCode.Accepted else HttpStatusCode.OK,
                credential = { body ->
                    val target = body.getValue("credential_identifier").jsonPrimitive.content
                    requested += target
                    check(target == "a") { "Issuer unavailable" }
                    if (deferred) """{"transaction_id":"accepted-a","interval":5}"""
                    else batchTestResponse(body.proofs())
                })
            val result = if (authorized) WalletIssuanceHandler.receiveCredentialAuthCode(fixture.wallet,
                ReceiveAuthorizedCredentialRequest(code = "code", credentialIssuer = ISSUER,
                    credentialEndpoint = Url("$ISSUER/credential"), credentials = listOf(fixture.selection(2))),
                httpClient = http)
            else WalletIssuanceHandler.receiveCredential(fixture.wallet,
                ReceiveCredentialRequest(offerJson = offer(), credentials = listOf(fixture.selection(2))), httpClient = http)

            assertEquals(1, tokenCalls)
            assertEquals(listOf("a", "b"), requested)
            val failure = assertNotNull(result.failure)
            assertEquals("b", failure.target.credentialIdentifier)
            assertEquals(CredentialIssuanceStage.REQUEST, failure.stage)
            assertEquals(listOf("c"), failure.notAttempted.map { it.credentialIdentifier })
            assertEquals(if (deferred) 0 else 2, result.credentialIds.size)
            assertEquals(result.credentialIds.toSet(), fixture.store.listCredentials().toList().map { it.id }.toSet())
            if (deferred) {
                val pending = result.deferredCredentials.single()
                assertEquals("a", pending.credentialIdentifier)
                assertEquals(fixture.keys.take(2).map { it.keyId }, pending.holderBindings.map { it.keyId })
            } else assertTrue(result.deferredCredentials.isEmpty())
        }
    }

    @Test fun fullFlowsResumeReceivedBatchesWithoutRedeemingEitherGrantAgain() = runTest {
        for (retained in listOf(false, true)) for (authorized in listOf(false, true)) {
            val fixture = fixture(true)
            val records = id.walt.wallet2.stores.inmemory.InMemoryIssuanceSessionStore()
            var writes = 0
            val failingStore = object : WalletCredentialStore by fixture.store {
                override suspend fun addCredential(entry: StoredCredential) {
                    check(++writes != 4) { "Second target's second write failed" }
                    fixture.store.addCredential(entry)
                }
            }
            val wallet = fixture.wallet.copy(credentialStores = listOf(failingStore))
                .attachIssuanceSessionState(WalletIssuanceSessionState(fixture.wallet.id, records))
            var tokens = 0
            val requests = mutableListOf<String>()
            val events = mutableListOf<WalletSessionEvent>()
            val http = client(token = {
                tokens++
                """{"access_token":"access","token_type":"Bearer","authorization_details":[
                    {"type":"openid_credential","credential_configuration_id":"identity","credential_identifiers":["a","b","c"]}]}"""
            }, credential = {
                requests += it.getValue("credential_identifier").jsonPrimitive.content
                batchTestResponse(it.proofs().reversed())
            })
            val storedIds: List<String>
            val handle: WalletDeferredCredential
            val failure: CredentialIssuanceFailure
            var retainedSessionId: String? = null
            if (retained) {
                val service = newSessionService(wallet, sessionStore = records, httpClient = http, onEvent = { events += it })
                val preview = service.start(if (authorized) WalletIssuanceSessionRequest(
                    credentialIssuer = ISSUER, credentialConfigurationIds = listOf("identity"), redirectUri = Url("openid://callback"))
                    else WalletIssuanceSessionRequest(offerJson = offer()))
                retainedSessionId = preview.id
                val outcome = if (authorized) {
                    val authorization = service.beginAuthorization(preview.id, listOf(fixture.selection(2)))
                    service.continueAuthorization(WalletIssuanceAuthorizationCallback(preview.id,
                        "openid://callback?code=code&state=${authorization.state}"))
                } else service.continuePreAuthorized(preview.id, credentials = listOf(fixture.selection(2)))
                val failed = assertIs<WalletIssuanceOutcome.Failed>(outcome)
                assertEquals(preview.id, failed.sessionId)
                assertEquals(WalletIssuanceErrorCode.STORAGE, failed.error.code)
                storedIds = failed.storedCredentialIds
                handle = failed.deferredCredentials.single()
                failure = assertNotNull(failed.failure)
            } else {
                val outcome = if (authorized) WalletIssuanceHandler.receiveCredentialAuthCode(wallet,
                    ReceiveAuthorizedCredentialRequest(code = "code", credentialIssuer = ISSUER,
                        credentialEndpoint = Url("$ISSUER/credential"), credentials = listOf(fixture.selection(2))),
                    httpClient = http, onEvent = { events += it })
                else WalletIssuanceHandler.receiveCredential(wallet,
                    ReceiveCredentialRequest(offerJson = offer(), credentials = listOf(fixture.selection(2))),
                    httpClient = http, onEvent = { events += it })
                storedIds = outcome.credentialIds
                failure = assertNotNull(outcome.failure)
                assertTrue(outcome.deferredCredentials.isEmpty())
                val storage = assertNotNull(outcome.storageOutcome)
                assertEquals(listOf(storedIds.last()), storage.storedCredentialIds)
                handle = storage.deferredCredentials.single()
            }
            assertEquals(3, storedIds.size)
            assertEquals(3, events.count { it == WalletSessionEvent.issuance_credential_stored })
            assertFalse(WalletSessionEvent.issuance_completed in events)
            assertEquals(CredentialIssuanceStage.STORAGE, failure.stage)
            assertEquals("b", failure.target.credentialIdentifier)
            assertEquals(listOf("c"), failure.notAttempted.map { it.credentialIdentifier })
            assertEquals("b", handle.credentialIdentifier)
            assertNull(handle.intervalSeconds)
            val storedRecord = records.list().single()
            retainedSessionId?.let { assertEquals(it, storedRecord.sessionId) }
            val payload = Json.parseToJsonElement(storedRecord.payload).jsonObject
            assertFalse("request" in payload)
            val restored = newSessionService(fixture.wallet.copy(), sessionStore = records,
                httpClient = http, onEvent = { events += it })
            assertEquals(listOf(handle), restored.listDeferredCredentials())
            val newlyStored = mutableListOf<String>()
            val resumed = assertIs<WalletIssuanceOutcome.Stored>(restored.resumeWhenDue(handle.id,
                beforeCredentialsStored = { assertEquals(1, it) }, onCredentialStored = { newlyStored += it.id }))
            retainedSessionId?.let { assertEquals(it, resumed.sessionId) }
            assertEquals(2, resumed.credentialIds.size)
            assertEquals(1, newlyStored.size)
            assertTrue(storedIds.last() in resumed.credentialIds)
            assertEquals(4, (storedIds + resumed.credentialIds).toSet().size)
            assertEquals(4, fixture.store.listCredentials().toList().size)
            assertEquals(1, events.count { it == WalletSessionEvent.issuance_completed })
            assertTrue(restored.listDeferredCredentials().isEmpty())
            assertEquals(1, tokens)
            assertEquals(listOf("a", "b"), requests)
        }
    }

    @Test fun cancellationAfterAnImmediateSaveRetainsTheBatchAfterActiveSessionCleanup() = runTest {
        val fixture = fixture(true)
        val records = id.walt.wallet2.stores.inmemory.InMemoryIssuanceSessionStore()
        var requests = 0
        var tokens = 0
        val http = client(token = { tokens++; TOKEN }, credential = { requests++; batchTestResponse(it.proofs()) })
        val stored = CompletableDeferred<Unit>()
        val waitForCancellation = CompletableDeferred<Unit>()
        val service = newSessionService(fixture.wallet, sessionStore = records, httpClient = http,
            onEvent = {
                if (it == WalletSessionEvent.issuance_credential_stored) {
                    stored.complete(Unit)
                    waitForCancellation.await()
                }
            })
        val preview = service.start(WalletIssuanceSessionRequest(offerJson = offer()))
        val receiving = async { service.continuePreAuthorized(preview.id, credentials = listOf(fixture.selection(2))) }
        stored.await()
        receiving.cancel()
        receiving.join()
        assertEquals(1, fixture.store.listCredentials().toList().size)
        assertEquals(WalletIssuanceSessionRecordKind.DEFERRED_CREDENTIAL, records.list().single().kind)
        val restored = newSessionService(fixture.wallet.copy(), sessionStore = records, httpClient = http)
        val handle = restored.listDeferredCredentials().single()
        val callbacks = mutableListOf<String>()
        val resumed = assertIs<WalletIssuanceOutcome.Stored>(restored.resumeWhenDue(handle.id,
            beforeCredentialsStored = { assertEquals(1, it) }, onCredentialStored = { callbacks += it.id }))
        assertEquals(preview.id, resumed.sessionId)
        assertEquals(2, resumed.credentialIds.size)
        assertEquals(1, callbacks.size)
        assertTrue(records.list().isEmpty())
        assertEquals(1, requests)
        assertEquals(1, tokens)
    }

    @Test fun observerFailurePreservesCommittedIdAndConsumesReviewedPreview() = runTest {
        val fixture = fixture(true)
        var tokenCalls = 0
        val http = client(token = { tokenCalls++; TOKEN }, credential = { batchTestResponse(it.proofs()) })
        val preview = WalletIssuanceHandler.previewOffer(fixture.wallet, ResolveOfferRequest(offerJson = offer()), httpClient = http)
        val request = ReceiveCredentialFromPreviewRequest(preview.previewHandle, credentials = listOf(fixture.selection(2)))
        val result = WalletIssuanceHandler.receiveCredential(fixture.wallet, request, httpClient = http,
            onCredentialStored = { error("Observer unavailable") })
        assertEquals(CredentialIssuanceStage.OBSERVER, assertNotNull(result.failure).stage)
        assertEquals(1, result.credentialIds.size)
        assertEquals(result.credentialIds.single(), fixture.store.listCredentials().toList().single().id)
        assertFailsWith<PreviewSessionException> {
            WalletIssuanceHandler.receiveCredential(fixture.wallet, request, httpClient = http)
        }
        val handle = assertNotNull(result.storageOutcome).deferredCredentials.single()
        val resumedIds = mutableListOf<String>()
        val resumed = assertIs<WalletIssuanceOutcome.Stored>(fixture.wallet.issuanceSessions(http)
            .resumeWhenDue(handle.id, onCredentialStored = { resumedIds += it.id }))
        assertEquals(2, resumed.credentialIds.size)
        assertEquals(1, resumedIds.size)
        assertEquals(1, tokenCalls)
    }

    @Test fun authorizedReceiveUsesTokenIdentifiersAndBatchProofs() = runTest {
        val fixture = fixture(true)
        val targets = mutableListOf<String>()
        val result = WalletIssuanceHandler.receiveCredentialAuthCode(fixture.wallet,
            ReceiveAuthorizedCredentialRequest(
                code = "code", credentialIssuer = ISSUER, credentialEndpoint = Url("$ISSUER/credential"),
                credentials = listOf(fixture.selection(2))),
            httpClient = client(token = {
                """{"access_token":"access","token_type":"Bearer","authorization_details":[
                    {"type":"openid_credential","credential_configuration_id":"identity","credential_identifiers":["a","b"]}]}"""
            }, credential = { body ->
                targets += body["credential_identifier"]!!.jsonPrimitive.content
                batchTestResponse(body.proofs())
            }))
        assertEquals(listOf("a", "b"), targets)
        assertEquals(4, result.credentialIds.size)
    }

    @Test fun mobileDeferredBatchSurvivesServiceRecreationWithEveryHolderKey() = runTest {
        val fixture = fixture(true)
        val records = MemorySessionStore()
        var proofs = emptyList<String>()
        val http = client(credentialStatus = HttpStatusCode.Accepted, credential = {
            proofs = it.proofs()
            """{"transaction_id":"deferred-batch","interval":1}"""
        }, deferred = { batchTestResponse(proofs.reversed()) })
        val service = newSessionService(fixture.wallet, httpClient = http, sessionStore = records)
        val preview = service.start(WalletIssuanceSessionRequest(offerJson = offer()))
        assertEquals(5, preview.offer.batchSize)
        assertTrue(proofs.isEmpty())
        val pending = assertIs<WalletIssuanceOutcome.Deferred>(
            service.continuePreAuthorized(preview.id, credentials = listOf(fixture.selection(5))))
        assertEquals(5, proofs.size)
        val restored = newSessionService(fixture.wallet, httpClient = http, sessionStore = records)
        val result = assertIs<WalletIssuanceOutcome.Stored>(restored.resumeWhenDue(pending.credentials.single().id))
        assertEquals(5, result.credentialIds.size)
        val stored = fixture.store.listCredentials().toList()
        assertEquals(fixture.keys.reversed().map { it.keyId },
            stored.map { fixture.wallet.resolveHolderKey(it, setOf(KeyUsage.SIGN)).keyMaterial.keyId })
        assertTrue(records.records.isEmpty())
    }

    @Test fun issuedCredentialsMustMatchTheRequestedFormatAndTypeBeforeAnyWrite() = runTest {
        for (wrongFormat in listOf(false, true)) {
            val fixture = fixture(true)
            var beforeWrites = 0
            val advertised = if (wrongFormat) metadata().replace(
                "\"format\":\"dc+sd-jwt\",\"vct\":\"identity\"",
                "\"format\":\"jwt_vc_json\",\"credential_definition\":{\"type\":[\"VerifiableCredential\"]}",
            ) else metadata()
            val result = WalletIssuanceHandler.receiveCredential(fixture.wallet,
                ReceiveCredentialRequest(offerJson = offer(), credentials = listOf(fixture.selection(2))),
                httpClient = client(metadata = advertised, credential = { request ->
                    buildJsonObject {
                        put("credentials", JsonArray(request.proofs().mapIndexed { index, proof ->
                            val jwk = CompactJws.decodeUnverified(proof).protectedHeader.getValue("jwk").jsonObject
                            val credential = batchTestCredential(jwk, if (index == 0) "identity" else "unrequested-type")
                            buildJsonObject { put("credential", credential) }
                        }))
                    }.toString()
                }), beforeCredentialsStored = { beforeWrites++ })
            assertEquals(CredentialIssuanceStage.RESPONSE, assertNotNull(result.failure).stage)
            assertTrue(result.credentialIds.isEmpty())
            assertEquals(0, beforeWrites)
            assertTrue(fixture.store.listCredentials().toList().isEmpty())
        }
    }

    @Test fun deferredResponseUsesTheOriginallySelectedConfigurationAfterRestart() = runTest {
        val fixture = fixture(true)
        val records = MemorySessionStore()
        var proofs = emptyList<String>()
        val http = client(credentialStatus = HttpStatusCode.Accepted,
            credential = {
                proofs = it.proofs()
                """{"transaction_id":"wrong-kind","interval":1}"""
            }, deferred = {
                val jwk = CompactJws.decodeUnverified(proofs.single()).protectedHeader.getValue("jwk").jsonObject
                buildJsonObject { put("credentials", buildJsonArray {
                    add(buildJsonObject { put("credential", batchTestCredential(jwk, "another-type")) })
                }) }.toString()
            })
        val service = newSessionService(fixture.wallet, httpClient = http, sessionStore = records)
        val preview = service.start(WalletIssuanceSessionRequest(offerJson = offer()))
        val pending = assertIs<WalletIssuanceOutcome.Deferred>(service.continuePreAuthorized(preview.id)).credentials.single()
        val restored = newSessionService(fixture.wallet, httpClient = http, sessionStore = records)
        val failed = assertIs<WalletIssuanceOutcome.Failed>(restored.resumeWhenDue(pending.id))
        assertEquals(WalletIssuanceErrorCode.PROTOCOL, failed.error.code)
        assertTrue(fixture.store.listCredentials().toList().isEmpty())
    }

    @Test fun deferredTargetSurvivesFailureOfLaterTargetAndServiceRecreation() = runTest {
        val fixture = fixture(true)
        val records = MemorySessionStore()
        var calls = 0
        var firstProofs = emptyList<String>()
        val http = client(token = { negotiatedToken(true) }, credentialStatus = HttpStatusCode.Accepted,
            credential = { request ->
                if (++calls == 1) {
                    firstProofs = request.proofs()
                    """{"transaction_id":"accepted-first","interval":1}"""
                } else {
                    assertEquals(1, records.records.values.count { it.kind == WalletIssuanceSessionRecordKind.DEFERRED_CREDENTIAL })
                    error("Later target failed")
                }
            }, deferred = { batchTestResponse(firstProofs) })
        val service = newSessionService(fixture.wallet, httpClient = http, sessionStore = records)
        val preview = service.start(WalletIssuanceSessionRequest(offerJson = offer()))
        val failed = assertIs<WalletIssuanceOutcome.Failed>(
            service.continuePreAuthorized(preview.id, credentials = listOf(fixture.selection(2))))
        assertEquals(2, calls)
        assertEquals("dataset-b", assertNotNull(failed.failure).target.credentialIdentifier)
        assertEquals(CredentialIssuanceStage.REQUEST, failed.failure.stage)
        assertTrue(failed.failure.notAttempted.isEmpty())
        val pending = failed.deferredCredentials.single()
        assertEquals("dataset-a", pending.credentialIdentifier)
        val restored = newSessionService(fixture.wallet, httpClient = http, sessionStore = records)
        assertEquals(listOf(pending), restored.listDeferredCredentials())
        assertEquals(2, assertIs<WalletIssuanceOutcome.Stored>(restored.resumeWhenDue(pending.id)).credentialIds.size)
        assertTrue(restored.listDeferredCredentials().isEmpty())
        assertTrue(records.records.isEmpty())
    }

    @Test fun isolatedDeferredPollingReturnsIntervalAndUsesTheTokenKeyForDpopNonceRetry() = runTest {
        val fixture = fixture(true)
        val tokenKey = requireNotNull(fixture.keys[1].crypto2Key)
        val credential = batchTestCredential(fixture.keys[0])
        val proofs = mutableListOf<JsonObject>()
        val http = HttpClient(MockEngine) {
            engine { addHandler { request ->
                when (request.url.encodedPath) {
                    "/.well-known/openid-credential-issuer" -> respond(metadata(), HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
                    "/.well-known/oauth-authorization-server" -> respond(
                        """{"issuer":"$ISSUER","authorization_endpoint":"$ISSUER/authorize","token_endpoint":"$ISSUER/token",
                            "response_types_supported":["code"],"dpop_signing_alg_values_supported":["ES256"]}""",
                        HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
                    "/deferred" -> {
                        assertEquals("DPoP access", request.headers[HttpHeaders.Authorization])
                        val verified = CompactJws.verify(assertNotNull(request.headers["DPoP"]), tokenKey, JwsAlgorithm.ES256)
                        val claims = Json.parseToJsonElement(verified.payload.decodeToString()).jsonObject
                        proofs += claims
                        assertEquals("POST", claims["htm"]?.jsonPrimitive?.content)
                        assertEquals("$ISSUER/deferred", claims["htu"]?.jsonPrimitive?.content)
                        val hash = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                            java.security.MessageDigest.getInstance("SHA-256").digest("access".encodeToByteArray()))
                        assertEquals(hash, claims["ath"]?.jsonPrimitive?.content)
                        when (proofs.size) {
                            1 -> respond("""{"error":"use_dpop_nonce"}""", HttpStatusCode.Unauthorized,
                                headersOf("DPoP-Nonce" to listOf("required-nonce"),
                                    HttpHeaders.ContentType to listOf(ContentType.Application.Json.toString())))
                            2 -> {
                                assertEquals("required-nonce", claims["nonce"]?.jsonPrimitive?.content)
                                respond("""{"transaction_id":"pending","interval":7}""", HttpStatusCode.Accepted,
                                    headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
                            }
                            else -> respond(buildJsonObject { put("credentials", buildJsonArray {
                                add(buildJsonObject { put("credential", credential) })
                            }) }.toString(), HttpStatusCode.OK,
                                headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
                        }
                    }
                    else -> error("Unexpected endpoint ${request.url}")
                }
            } }
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val request = PollDeferredRequest(deferredCredentialEndpoint = Url("$ISSUER/deferred"),
            transactionId = "pending", accessToken = "access", tokenType = "DPoP", dpopKeyId = fixture.keys[1].keyId,
            holderBindings = listOf(CredentialHolderBinding(fixture.keys[0].keyId)), proofRequired = true,
            credentialIssuerBaseUrl = ISSUER, credentialConfigurationId = "identity")
        val pending = WalletIssuanceHandler.pollDeferred(fixture.wallet, request, httpClient = http)
        assertEquals(DeferredCredentialPending("pending", 7), pending.pending)
        assertTrue(pending.credentialIds.isEmpty())
        assertTrue(fixture.store.listCredentials().toList().isEmpty())
        val stored = WalletIssuanceHandler.pollDeferred(fixture.wallet, request, httpClient = http)
        assertNull(stored.pending)
        assertEquals(1, stored.credentialIds.size)
        assertEquals(3, proofs.map { it["jti"] }.distinct().size)
        assertEquals(fixture.keys[0].keyId,
            fixture.wallet.resolveHolderKey(fixture.store.listCredentials().toList().single(), setOf(KeyUsage.SIGN)).keyMaterial.keyId)
    }

    @Test fun isolatedDeferredPollingRejectsMissingKeysAndUnknownTokenTypesBeforeHttp() = runTest {
        val fixture = fixture(true)
        var calls = 0
        val http = HttpClient(MockEngine) { engine { addHandler { calls++; error("No request expected") } } }
        val request = PollDeferredRequest(deferredCredentialEndpoint = Url("$ISSUER/deferred"),
            transactionId = "pending", accessToken = "access")
        for (invalid in listOf(
            request.copy(holderBindings = listOf(CredentialHolderBinding("missing"))),
            request.copy(tokenType = "DPoP"),
            request.copy(tokenType = "unsupported"),
        )) assertFailsWith<IllegalArgumentException> {
            WalletIssuanceHandler.pollDeferred(fixture.wallet, invalid, httpClient = http)
        }
        assertEquals(0, calls)
    }

    @Test fun lifecycleObserverFailuresDoNotHideStoredResults() = runTest {
        val fixture = fixture(true)
        val result = WalletIssuanceHandler.receiveCredential(fixture.wallet, ReceiveCredentialRequest(offerJson = offer()),
            httpClient = client(credential = { batchTestResponse(it.proofs()) }),
            onEvent = { error("Event observer failed") })
        assertNull(result.failure)
        assertEquals(1, result.credentialIds.size)
    }

    @Test fun deferredStorageRetryAfterRestartUsesReceivedResponseAndSkipsCommittedWrites() = runTest {
        val fixture = fixture(true)
        val records = MemorySessionStore()
        var writes = 0
        val writeStore = object : WalletCredentialStore by fixture.store {
            override suspend fun addCredential(entry: StoredCredential) {
                if (++writes == 2) error("Storage temporarily unavailable")
                fixture.store.addCredential(entry)
            }
        }
        val wallet = fixture.wallet.copy(credentialStores = listOf(writeStore))
        var proofs = emptyList<String>()
        var polls = 0
        val http = client(credentialStatus = HttpStatusCode.Accepted, credential = {
            proofs = it.proofs()
            """{"transaction_id":"storage-retry","interval":1}"""
        }, deferred = { polls++; batchTestResponse(proofs) })
        val storedEvents = mutableListOf<WalletSessionEvent>()
        val quotaCounts = mutableListOf<Int>()
        val meteredIds = mutableListOf<String>()
        val service = newSessionService(wallet, httpClient = http, sessionStore = records,
            onEvent = { if (it == WalletSessionEvent.issuance_credential_stored) storedEvents += it })
        val preview = service.start(WalletIssuanceSessionRequest(offerJson = offer()))
        val pending = assertIs<WalletIssuanceOutcome.Deferred>(
            service.continuePreAuthorized(preview.id, credentials = listOf(fixture.selection(2)))).credentials.single()
        val failed = assertIs<WalletIssuanceOutcome.Failed>(service.resumeWhenDue(pending.id, { quotaCounts += it }, { meteredIds += it.id }))
        assertEquals(WalletIssuanceErrorCode.STORAGE, failed.error.code)
        assertEquals(1, failed.storedCredentialIds.size)
        assertEquals(listOf(pending.copy(intervalSeconds = null)), failed.deferredCredentials)
        val restored = newSessionService(wallet, httpClient = http, sessionStore = records,
            onEvent = { if (it == WalletSessionEvent.issuance_credential_stored) storedEvents += it })
        val completed = assertIs<WalletIssuanceOutcome.Stored>(restored.resumeWhenDue(pending.id, { quotaCounts += it }, { meteredIds += it.id }))
        assertEquals(2, completed.credentialIds.size)
        assertTrue(completed.credentialIds.containsAll(failed.storedCredentialIds))
        assertEquals(2, fixture.store.listCredentials().toList().size)
        assertEquals(3, writes)
        assertEquals(2, storedEvents.size)
        assertEquals(listOf(2, 1), quotaCounts)
        assertEquals(completed.credentialIds.toSet(), meteredIds.toSet())
        assertEquals(2, meteredIds.size)
        assertEquals(1, polls)
        assertTrue(records.records.isEmpty())
    }

    @Test fun fullGrantDeferredHandlesRecoverPartialStorageAcrossWalletRecreation() = runTest {
        for (authorized in listOf(false, true)) {
            val fixture = fixture(true)
            val records = MemorySessionStore()
            var writes = 0
            val writeStore = object : WalletCredentialStore by fixture.store {
                override suspend fun addCredential(entry: StoredCredential) {
                    if (++writes == 2) error("Storage temporarily unavailable")
                    fixture.store.addCredential(entry)
                }
            }
            val wallet = fixture.wallet.copy(credentialStores = listOf(writeStore)).attachIssuanceSessionState(WalletIssuanceSessionState(fixture.wallet.id, records))
            var proofs = emptyList<String>()
            var polls = 0
            val http = client(credentialStatus = HttpStatusCode.Accepted, credential = {
                proofs = it.proofs()
                """{"transaction_id":"full-flow-retry","interval":1}"""
            }, deferred = { polls++; batchTestResponse(proofs.reversed()) })
            val result = if (authorized) WalletIssuanceHandler.receiveCredentialAuthCode(wallet,
                ReceiveAuthorizedCredentialRequest(code = "code", credentialIssuer = ISSUER,
                    credentialEndpoint = Url("$ISSUER/credential"), credentials = listOf(fixture.selection(2))),
                httpClient = http)
            else WalletIssuanceHandler.receiveCredential(wallet,
                ReceiveCredentialRequest(offerJson = offer(), credentials = listOf(fixture.selection(2))), httpClient = http)
            assertNull(result.failure)
            val handle = assertNotNull(result.deferredCredentials.single().deferredCredentialId)
            assertNotEquals("full-flow-retry", handle)
            val restored = wallet.copy().attachIssuanceSessionState(WalletIssuanceSessionState(fixture.wallet.id, records)).issuanceSessions(http)
            val public = restored.listDeferredCredentials().single()
            assertEquals(handle, public.id)
            val failed = assertIs<WalletIssuanceOutcome.Failed>(restored.resumeWhenDue(handle))
            assertEquals(WalletIssuanceErrorCode.STORAGE, failed.error.code)
            assertEquals(1, failed.storedCredentialIds.size)
            assertEquals(listOf(public.copy(intervalSeconds = null)), failed.deferredCredentials)
            val restarted = wallet.copy().attachIssuanceSessionState(WalletIssuanceSessionState(fixture.wallet.id, records)).issuanceSessions(http)
            val stored = assertIs<WalletIssuanceOutcome.Stored>(restarted.resumeWhenDue(handle))
            assertEquals(2, stored.credentialIds.size)
            assertTrue(stored.credentialIds.containsAll(failed.storedCredentialIds))
            assertEquals(2, fixture.store.listCredentials().toList().size)
            assertEquals(3, writes)
            assertEquals(1, polls)
            assertTrue(restarted.listDeferredCredentials().isEmpty())
            assertTrue(records.records.isEmpty())
            assertIs<WalletIssuanceOutcome.Failed>(restarted.resumeWhenDue(handle))
            assertEquals(1, polls)
        }
    }

    @Test fun deferredPendingUpdatesIntervalAndRetainsTransactionAcrossRestart() = runTest {
        val fixture = fixture(true)
        val records = MemorySessionStore()
        var proofs = emptyList<String>()
        var pendingResponse = true
        val http = client(credentialStatus = HttpStatusCode.Accepted, credential = {
            proofs = it.proofs()
            """{"transaction_id":"same-transaction","interval":1}"""
        }, deferredStatus = { if (pendingResponse) HttpStatusCode.Accepted else HttpStatusCode.OK }, deferred = {
            assertEquals("same-transaction", it["transaction_id"]?.jsonPrimitive?.content)
            if (pendingResponse) """{"transaction_id":"same-transaction","interval":7}""" else batchTestResponse(proofs)
        })
        val service = newSessionService(fixture.wallet, httpClient = http, sessionStore = records)
        val preview = service.start(WalletIssuanceSessionRequest(offerJson = offer()))
        val pending = assertIs<WalletIssuanceOutcome.Deferred>(service.continuePreAuthorized(preview.id)).credentials.single()
        val next = assertIs<WalletIssuanceOutcome.Deferred>(service.resumeWhenDue(pending.id)).credentials.single()
        assertEquals(pending.id, next.id)
        assertEquals(7, next.intervalSeconds)
        pendingResponse = false
        val restored = newSessionService(fixture.wallet, httpClient = http, sessionStore = records)
        assertEquals(1, assertIs<WalletIssuanceOutcome.Stored>(restored.resumeWhenDue(next.id)).credentialIds.size)
    }

    @Test fun deferredFailureRetainsTransientContinuationButConsumesExplicitDenial() = runTest {
        for (error in listOf("temporarily_unavailable", "invalid_transaction_id", "credential_request_denied")) {
            val fixture = fixture(true)
            val records = MemorySessionStore()
            val terminal = error != "temporarily_unavailable"
            val http = client(credentialStatus = HttpStatusCode.Accepted,
                credential = { """{"transaction_id":"failure","interval":1}""" },
                deferredStatus = { if (terminal) HttpStatusCode.BadRequest else HttpStatusCode.ServiceUnavailable },
                deferred = { """{"error":"$error"}""" })
            val service = newSessionService(fixture.wallet, httpClient = http, sessionStore = records)
            val preview = service.start(WalletIssuanceSessionRequest(offerJson = offer()))
            val pending = assertIs<WalletIssuanceOutcome.Deferred>(service.continuePreAuthorized(preview.id)).credentials.single()
            val failed = assertIs<WalletIssuanceOutcome.Failed>(service.resumeWhenDue(pending.id))
            assertEquals(WalletIssuanceErrorCode.ISSUER_RESPONSE, failed.error.code)
            assertEquals(if (terminal) emptyList() else listOf(pending), failed.deferredCredentials)
            assertEquals(if (terminal) 0 else 1, records.records.size)
        }
    }

    @Test fun concurrentDeferredResumeDoesNotPollOrStoreTheSameTargetTwice() = runTest {
        val fixture = fixture(true)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var proofs = emptyList<String>()
        var polls = 0
        val http = client(credentialStatus = HttpStatusCode.Accepted, credential = {
            proofs = it.proofs()
            """{"transaction_id":"concurrent","interval":1}"""
        }, deferred = {
            polls++
            entered.complete(Unit)
            release.await()
            batchTestResponse(proofs)
        })
        val service = newSessionService(fixture.wallet, httpClient = http)
        val preview = service.start(WalletIssuanceSessionRequest(offerJson = offer()))
        val pending = assertIs<WalletIssuanceOutcome.Deferred>(service.continuePreAuthorized(preview.id)).credentials.single()
        val first = async { service.resumeWhenDue(pending.id) }
        entered.await()
        assertIs<WalletIssuanceOutcome.Failed>(service.resumeWhenDue(pending.id))
        release.complete(Unit)
        assertEquals(1, assertIs<WalletIssuanceOutcome.Stored>(first.await()).credentialIds.size)
        assertEquals(1, polls)
        assertEquals(1, fixture.store.listCredentials().toList().size)
    }

    @Test fun cancelAndClearCannotDiscardAnInFlightDeferredResponse() = runTest {
        val fixture = fixture(true)
        val records = MemorySessionStore()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val http = client(credentialStatus = HttpStatusCode.Accepted,
            credential = { """{"transaction_id":"pending","interval":1}""" },
            deferredStatus = { HttpStatusCode.Accepted }, deferred = {
                entered.complete(Unit)
                release.await()
                """{"transaction_id":"pending","interval":7}"""
            })
        val original = newSessionService(fixture.wallet, httpClient = http, sessionStore = records)
        val preview = original.start(WalletIssuanceSessionRequest(offerJson = offer()))
        val pending = assertIs<WalletIssuanceOutcome.Deferred>(original.continuePreAuthorized(preview.id)).credentials.single()
        // Resume using only persisted state, without a cached record in the new engine.
        val restored = newSessionService(fixture.wallet, httpClient = http, sessionStore = records)
        val poll = async { restored.resumeWhenDue(pending.id) }
        entered.await()
        assertIs<WalletIssuanceOutcome.Failed>(restored.cancel(preview.id))
        assertFailsWith<IllegalStateException> { restored.clearSessions() }
        assertEquals(1, records.records.size)
        release.complete(Unit)
        val updated = assertIs<WalletIssuanceOutcome.Deferred>(poll.await()).credentials.single()
        assertEquals(7, updated.intervalSeconds)
        assertEquals(listOf(updated), restored.listDeferredCredentials())
        assertIs<WalletIssuanceOutcome.Cancelled>(restored.cancel(preview.id))
        assertTrue(restored.listDeferredCredentials().isEmpty())
        restored.clearSessions()
        assertTrue(records.records.isEmpty())
    }

    @Test fun reconstructedEnginesSharePollAdmissionAndCannotClearEachOthersWork() = runTest {
        val fixture = fixture(true)
        val state = WalletIssuanceSessionState(fixture.wallet.id, MemorySessionStore())
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var proofs = emptyList<String>()
        var polls = 0
        val http = client(credentialStatus = HttpStatusCode.Accepted, credential = {
            proofs = it.proofs()
            """{"transaction_id":"shared-poll","interval":1}"""
        }, deferred = {
            polls++
            entered.complete(Unit)
            release.await()
            batchTestResponse(proofs)
        })
        val first = fixture.wallet.copy().attachIssuanceSessionState(state).issuanceSessions(http)
        val review = first.start(WalletIssuanceSessionRequest(offerJson = offer()))
        val pending = assertIs<WalletIssuanceOutcome.Deferred>(first.continuePreAuthorized(review.id)).credentials.single()
        val second = fixture.wallet.copy().attachIssuanceSessionState(state).issuanceSessions(http)
        val polled = async { first.resumeWhenDue(pending.id) }
        entered.await()
        assertIs<WalletIssuanceOutcome.Failed>(second.resumeWhenDue(pending.id))
        assertIs<WalletIssuanceOutcome.Failed>(second.cancel(review.id))
        assertFailsWith<IllegalStateException> { second.clearSessions() }
        release.complete(Unit)
        assertEquals(1, assertIs<WalletIssuanceOutcome.Stored>(polled.await()).credentialIds.size)
        assertTrue(second.listDeferredCredentials().isEmpty())
        assertIs<WalletIssuanceOutcome.Failed>(second.resumeWhenDue(pending.id))
        assertEquals(1, polls)
        assertEquals(1, fixture.store.listCredentials().toList().size)
    }

    @Test fun isolatedFetchRetainsLocalSaveProgressAcrossRuntimeRecreationWithoutAnotherIssuerRequest() = runTest {
        for (rejectQuota in listOf(false, true)) {
            val fixture = fixture(true)
            val records = id.walt.wallet2.stores.inmemory.InMemoryIssuanceSessionStore()
            var writes = 0
            val failingCredentials = object : WalletCredentialStore by fixture.store {
                override suspend fun addCredential(entry: StoredCredential) {
                    writes++
                    check(rejectQuota || writes != 2) { "Second write unavailable" }
                    fixture.store.addCredential(entry)
                }
            }
            val wallet = fixture.wallet.copy(credentialStores = listOf(failingCredentials))
                .attachIssuanceSessionState(WalletIssuanceSessionState(fixture.wallet.id, records))
            var fetches = 0
            val http = client(credential = { fetches++; batchTestResponse(it.proofs().reversed()) })
            val bindings = fixture.selection(2).holderBindings
            val proofs = WalletIssuanceHandler.signProof(wallet,
                SignProofRequest(bindings, Url(ISSUER), "identity"), http).proofs
            val reported = mutableListOf<String>()
            val result = WalletIssuanceHandler.fetchCredential(wallet,
                FetchCredentialRequest(Url("$ISSUER/credential"), "access", "identity", proofs,
                    credentialIdentifier = "dataset-a", holderBindings = bindings,
                    storeInWallet = true, credentialIssuerBaseUrl = ISSUER), http,
                beforeCredentialsStored = { count -> assertEquals(2, count); check(!rejectQuota) { "Quota exceeded" } },
                onCredentialStored = { reported += it.id })
            assertEquals(2, result.rawCredentials.size)
            val failed = assertIs<WalletIssuanceOutcome.Failed>(result.storageOutcome)
            assertEquals(WalletIssuanceErrorCode.STORAGE, failed.error.code)
            assertEquals(if (rejectQuota) 0 else 1, failed.storedCredentialIds.size)
            assertEquals(reported, failed.storedCredentialIds)
            val handle = failed.deferredCredentials.single()
            assertNull(handle.intervalSeconds)
            assertEquals("dataset-a", handle.credentialIdentifier)
            val payload = Json.parseToJsonElement(records.list().single().payload).jsonObject
            assertFalse("request" in payload)
            assertFalse("accessToken" in payload)
            assertFalse("transactionId" in payload)
            val preparedIds = payload.getValue("receivedCredentials").jsonArray.map { it.jsonObject.getValue("id").jsonPrimitive.content }
            val restored = fixture.wallet.copy().attachIssuanceSessionState(WalletIssuanceSessionState(wallet.id, records))
                .issuanceSessions(http)
            assertEquals(listOf(handle), restored.listDeferredCredentials())
            val completed = assertIs<WalletIssuanceOutcome.Stored>(restored.resumeWhenDue(handle.id,
                beforeCredentialsStored = { assertEquals(if (rejectQuota) 2 else 1, it) },
                onCredentialStored = { reported += it.id }))
            assertEquals(preparedIds.toSet(), completed.credentialIds.toSet())
            assertEquals(2, reported.size)
            assertEquals(preparedIds.toSet(), reported.toSet())
            assertEquals(1, fetches)
            assertTrue(restored.listDeferredCredentials().isEmpty())
            assertEquals(2, fixture.store.listCredentials().toList().size)
        }
    }

    @Test fun isolatedFetchCheckpointFailurePreventsLocalWritesAndPreservesTheResponseInItsRuntime() = runTest {
        val fixture = fixture(true)
        val records = MemorySessionStore()
        var unavailable = true
        val store = object : WalletIssuanceSessionStore by records {
            override suspend fun put(record: WalletIssuanceSessionRecord) {
                check(!unavailable) { "Continuation store unavailable" }
                records.put(record)
            }
        }
        val wallet = fixture.wallet.attachIssuanceSessionState(WalletIssuanceSessionState(fixture.wallet.id, store))
        var fetches = 0
        val http = client(credential = { fetches++; batchTestResponse(it.proofs()) })
        val proofs = WalletIssuanceHandler.signProof(wallet,
            SignProofRequest(fixture.selection(1).holderBindings, Url(ISSUER), "identity"), http).proofs
        val result = WalletIssuanceHandler.fetchCredential(wallet,
            FetchCredentialRequest(Url("$ISSUER/credential"), "access", "identity", proofs,
                holderBindings = fixture.selection(1).holderBindings, storeInWallet = true, credentialIssuerBaseUrl = ISSUER), http,
            beforeCredentialsStored = { error("Must checkpoint before quota or writes") })
        val pending = assertIs<WalletIssuanceOutcome.Failed>(result.storageOutcome).deferredCredentials.single()
        assertTrue(fixture.store.listCredentials().toList().isEmpty())
        unavailable = false
        val completed = assertIs<WalletIssuanceOutcome.Stored>(wallet.issuanceSessions().resumeWhenDue(pending.id))
        assertEquals(1, completed.credentialIds.size)
        assertEquals(1, fetches)
    }

    @Test fun cancellingAnIsolatedFetchDuringCheckpointRetainsItsResponseWithoutStartingWrites() = runTest {
        val fixture = fixture(true)
        val records = MemorySessionStore()
        val checkpointed = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val store = object : WalletIssuanceSessionStore by records {
            override suspend fun put(record: WalletIssuanceSessionRecord) {
                records.put(record)
                checkpointed.complete(Unit)
                release.await()
            }
        }
        val wallet = fixture.wallet.attachIssuanceSessionState(WalletIssuanceSessionState(fixture.wallet.id, store))
        var fetches = 0
        val http = client(credential = { fetches++; batchTestResponse(it.proofs()) })
        val bindings = fixture.selection(1).holderBindings
        val proofs = WalletIssuanceHandler.signProof(wallet, SignProofRequest(bindings, Url(ISSUER), "identity"), http).proofs
        val receiving = async {
            WalletIssuanceHandler.fetchCredential(wallet,
                FetchCredentialRequest(Url("$ISSUER/credential"), "access", "identity", proofs,
                    holderBindings = bindings, storeInWallet = true, credentialIssuerBaseUrl = ISSUER), http,
                beforeCredentialsStored = { error("Cancelled receive must not reserve or write") })
        }
        checkpointed.await()
        receiving.cancel()
        release.complete(Unit)
        receiving.join()
        assertTrue(fixture.store.listCredentials().toList().isEmpty())
        val restarted = fixture.wallet.copy().attachIssuanceSessionState(WalletIssuanceSessionState(wallet.id, records))
            .issuanceSessions(http)
        val pending = restarted.listDeferredCredentials().single()
        assertEquals(1, assertIs<WalletIssuanceOutcome.Stored>(restarted.resumeWhenDue(pending.id)).credentialIds.size)
        assertEquals(1, fetches)
    }

    @Test fun isolatedPollResumesPartialStorageWithKnownOrUnknownTargetIdentityWithoutRepolling() = runTest {
        for (knownConfiguration in listOf(false, true)) {
            val fixture = fixture(true)
            val records = MemorySessionStore()
            var writes = 0
            val failingStore = object : WalletCredentialStore by fixture.store {
                override suspend fun addCredential(entry: StoredCredential) {
                    check(++writes != 2) { "Second write unavailable" }
                    fixture.store.addCredential(entry)
                }
            }
            val wallet = fixture.wallet.copy(credentialStores = listOf(failingStore))
                .attachIssuanceSessionState(WalletIssuanceSessionState(fixture.wallet.id, records))
            var polls = 0
            val http = client(credential = { error("Must not fetch another credential") }, deferred = {
                polls++
                buildJsonObject { put("credentials", JsonArray(fixture.keys.take(2).reversed().map { key ->
                    buildJsonObject { put("credential", batchTestCredential(key)) }
                })) }.toString()
            })
            val reported = mutableListOf<String>()
            val result = WalletIssuanceHandler.pollDeferred(wallet,
                PollDeferredRequest(deferredCredentialEndpoint = Url("$ISSUER/deferred"), accessToken = "access",
                    transactionId = "transaction", holderBindings = fixture.selection(2).holderBindings, proofRequired = true,
                    credentialIssuerBaseUrl = ISSUER.takeIf { knownConfiguration },
                    credentialConfigurationId = "identity".takeIf { knownConfiguration },
                    credentialIdentifier = "dataset-a".takeIf { knownConfiguration }),
                httpClient = http, beforeCredentialsStored = { assertEquals(2, it) }, onCredentialStored = { reported += it.id })
            val failed = assertIs<WalletIssuanceOutcome.Failed>(result.storageOutcome)
            assertNull(result.pending)
            assertEquals(reported, result.credentialIds)
            assertEquals(1, result.credentialIds.size)
            val handle = failed.deferredCredentials.single()
            assertEquals("identity".takeIf { knownConfiguration }, handle.credentialConfigurationId)
            assertEquals("dataset-a".takeIf { knownConfiguration }, handle.credentialIdentifier)
            val restored = fixture.wallet.copy().attachIssuanceSessionState(WalletIssuanceSessionState(wallet.id, records))
                .issuanceSessions(http)
            assertEquals(listOf(handle), restored.listDeferredCredentials())
            val complete = assertIs<WalletIssuanceOutcome.Stored>(restored.resumeWhenDue(handle.id,
                beforeCredentialsStored = { assertEquals(1, it) }, onCredentialStored = { reported += it.id }))
            assertEquals(2, reported.size)
            assertEquals(complete.credentialIds.toSet(), reported.toSet())
            assertEquals(1, polls)
            assertTrue(restored.listDeferredCredentials().isEmpty())
        }
    }

    @Test fun deferredQuotaRejectionCanRetryTheReceivedResponseWithoutAnotherIssuerPoll() = runTest {
        val fixture = fixture(true)
        val records = MemorySessionStore()
        var proofs = emptyList<String>()
        var polls = 0
        val http = client(credentialStatus = HttpStatusCode.Accepted, credential = {
            proofs = it.proofs()
            """{"transaction_id":"quota","interval":1}"""
        }, deferred = { polls++; batchTestResponse(proofs) })
        val original = newSessionService(fixture.wallet, sessionStore = records, httpClient = http)
        val review = original.start(WalletIssuanceSessionRequest(offerJson = offer()))
        val pending = assertIs<WalletIssuanceOutcome.Deferred>(original.continuePreAuthorized(review.id,
            credentials = listOf(fixture.selection(2)))).credentials.single()
        val failed = assertIs<WalletIssuanceOutcome.Failed>(original.resumeWhenDue(pending.id,
            beforeCredentialsStored = { count -> assertEquals(2, count); error("Quota exceeded") },
            onCredentialStored = { error("No write may be reported after quota rejection") }))
        assertEquals(WalletIssuanceErrorCode.STORAGE, failed.error.code)
        assertTrue(fixture.store.listCredentials().toList().isEmpty())
        assertEquals(listOf(pending.copy(intervalSeconds = null)), failed.deferredCredentials)
        val restored = newSessionService(fixture.wallet, sessionStore = records, httpClient = http)
        val saved = mutableListOf<String>()
        val completed = assertIs<WalletIssuanceOutcome.Stored>(restored.resumeWhenDue(pending.id,
            beforeCredentialsStored = { assertEquals(2, it) }, onCredentialStored = { saved += it.id }))
        assertEquals(2, saved.size)
        assertEquals(completed.credentialIds.toSet(), saved.toSet())
        assertEquals(1, polls)
    }

    @Test fun databaseFailurePreservesReceivedResponseAcrossRequestScopedWallets() = runTest {
        val fixture = fixture(true)
        val records = MemorySessionStore()
        var failWrites = false
        val failingStore = object : WalletIssuanceSessionStore by records {
            override suspend fun put(record: WalletIssuanceSessionRecord) {
                check(!failWrites) { "Database unavailable" }
                records.put(record)
            }
            override suspend fun compareAndSet(expected: WalletIssuanceSessionRecord, replacement: WalletIssuanceSessionRecord?): Boolean {
                check(!failWrites) { "Database unavailable" }
                return records.compareAndSet(expected, replacement)
            }
        }
        val state = WalletIssuanceSessionState(fixture.wallet.id, failingStore)
        var proofs = emptyList<String>()
        var polls = 0
        val http = client(credentialStatus = HttpStatusCode.Accepted, credential = {
            proofs = it.proofs()
            """{"transaction_id":"db-outage","interval":1}"""
        }, deferred = { polls++; failWrites = true; batchTestResponse(proofs) })
        val first = fixture.wallet.copy().attachIssuanceSessionState(state).issuanceSessions(http)
        val review = first.start(WalletIssuanceSessionRequest(offerJson = offer()))
        val pending = assertIs<WalletIssuanceOutcome.Deferred>(first.continuePreAuthorized(review.id,
            credentials = listOf(fixture.selection(2)))).credentials.single()
        val failed = assertIs<WalletIssuanceOutcome.Failed>(first.resumeWhenDue(pending.id))
        assertEquals(WalletIssuanceErrorCode.STORAGE, failed.error.code)
        assertTrue(fixture.store.listCredentials().toList().isEmpty())
        failWrites = false
        val restarted = newSessionService(fixture.wallet, sessionStore = failingStore, httpClient = http)
        assertEquals(WalletIssuanceErrorCode.REMOTE_OUTCOME_UNCERTAIN,
            assertIs<WalletIssuanceOutcome.Failed>(restarted.resumeWhenDue(pending.id)).error.code)
        val next = fixture.wallet.copy().attachIssuanceSessionState(state).issuanceSessions(http)
        assertEquals(2, assertIs<WalletIssuanceOutcome.Stored>(next.resumeWhenDue(pending.id)).credentialIds.size)
        assertEquals(1, polls)
        assertTrue(next.listDeferredCredentials().isEmpty())
    }

    @Test fun independentRuntimesCannotRepeatAnInFlightDeferredRequest() = runTest {
        val fixture = fixture(true)
        val records = id.walt.wallet2.stores.inmemory.InMemoryIssuanceSessionStore()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var proofs = emptyList<String>()
        var polls = 0
        val http = client(credentialStatus = HttpStatusCode.Accepted, credential = {
            proofs = it.proofs()
            """{"transaction_id":"concurrent","interval":1}"""
        }, deferred = { polls++; entered.complete(Unit); release.await(); batchTestResponse(proofs) })
        val first = newSessionService(fixture.wallet, sessionStore = records, httpClient = http)
        val review = first.start(WalletIssuanceSessionRequest(offerJson = offer()))
        val pending = assertIs<WalletIssuanceOutcome.Deferred>(first.continuePreAuthorized(review.id)).credentials.single()
        val second = newSessionService(fixture.wallet, sessionStore = records, httpClient = http)
        val running = async { first.resumeWhenDue(pending.id) }
        entered.await()
        try {
            assertEquals(WalletIssuanceErrorCode.REMOTE_OUTCOME_UNCERTAIN,
                assertIs<WalletIssuanceOutcome.Failed>(second.resumeWhenDue(pending.id)).error.code)
            assertEquals(WalletIssuanceErrorCode.INVALID_SESSION,
                assertIs<WalletIssuanceOutcome.Failed>(second.cancel(review.id)).error.code)
            assertFailsWith<IllegalStateException> { second.clearSessions() }
        } finally { release.complete(Unit) }
        assertIs<WalletIssuanceOutcome.Stored>(running.await())
        assertEquals(WalletIssuanceErrorCode.INVALID_SESSION,
            assertIs<WalletIssuanceOutcome.Failed>(second.resumeWhenDue(pending.id)).error.code)
        assertEquals(1, polls)
    }

    @Test fun independentRuntimesCannotTakeOverDeferredStorageOrReleaseItsClaim() = runTest {
        for (pauseAfterFirstSave in listOf(false, true)) {
            val fixture = fixture(true)
            val records = id.walt.wallet2.stores.inmemory.InMemoryIssuanceSessionStore()
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            var proofs = emptyList<String>()
            var polls = 0
            val http = client(credentialStatus = HttpStatusCode.Accepted, credential = {
                proofs = it.proofs()
                """{"transaction_id":"storage-owner","interval":1}"""
            }, deferred = { polls++; batchTestResponse(proofs) })
            val owner = newSessionService(fixture.wallet, sessionStore = records, httpClient = http)
            val review = owner.start(WalletIssuanceSessionRequest(offerJson = offer()))
            val pending = assertIs<WalletIssuanceOutcome.Deferred>(owner.continuePreAuthorized(review.id,
                credentials = listOf(fixture.selection(2)))).credentials.single()
            val saved = mutableListOf<String>()
            val running = async {
                owner.resumeWhenDue(pending.id, beforeCredentialsStored = {
                    assertEquals(2, it)
                    if (!pauseAfterFirstSave) { entered.complete(Unit); release.await() }
                }, onCredentialStored = {
                    saved += it.id
                    if (pauseAfterFirstSave && saved.size == 1) { entered.complete(Unit); release.await() }
                })
            }
            entered.await()
            try {
                val claim = records.list().single()
                val observer = newSessionService(fixture.wallet, sessionStore = records, httpClient = http)
                val observed = assertIs<WalletIssuanceOutcome.Failed>(observer.resumeWhenDue(pending.id,
                    beforeCredentialsStored = { error("Another writer owns the quota reservation") },
                    onCredentialStored = { error("Another writer owns the save callback") }))
                assertEquals(WalletIssuanceErrorCode.STORAGE_OUTCOME_UNCERTAIN, observed.error.code)
                assertEquals(saved, observed.storedCredentialIds)
                assertEquals(listOf(pending.copy(intervalSeconds = null)), observed.deferredCredentials)
                assertEquals(WalletIssuanceErrorCode.INVALID_SESSION,
                    assertIs<WalletIssuanceOutcome.Failed>(observer.cancel(review.id)).error.code)
                assertFailsWith<IllegalStateException> { observer.clearSessions() }
                assertFailsWith<IllegalStateException> { observer.closeSessions() }
                assertFailsWith<IllegalStateException> { owner.closeSessions() }
                // Failed or cancelled credential reads are observations, not ownership of this claim.
                for (cancel in listOf(false, true)) {
                    val unreadable = object : WalletCredentialStore by fixture.store {
                        override suspend fun getCredential(id: String): StoredCredential? {
                            if (cancel) throw kotlinx.coroutines.CancellationException("Read cancelled")
                            error("Credential store unavailable")
                        }
                    }
                    val failingObserver = newSessionService(
                        fixture.wallet.copy(credentialStores = listOf(unreadable)), sessionStore = records, httpClient = http)
                    if (cancel) assertFailsWith<kotlinx.coroutines.CancellationException> {
                        failingObserver.resumeWhenDue(pending.id)
                    } else assertEquals(WalletIssuanceErrorCode.STORAGE,
                        assertIs<WalletIssuanceOutcome.Failed>(failingObserver.resumeWhenDue(pending.id)).error.code)
                    assertEquals(claim, records.list().single())
                }
            } finally { release.complete(Unit) }
            val result = assertIs<WalletIssuanceOutcome.Stored>(running.await())
            assertEquals(2, saved.size)
            assertEquals(result.credentialIds, saved)
            assertEquals(2, fixture.store.listCredentials().toList().size)
            assertEquals(1, polls)
            assertTrue(records.list().isEmpty())
        }
    }

    @Test fun cancelledDeferredSaveReleasesOnlyItsOwnClaimAndRetriesRemainingWrites() = runTest {
        val fixture = fixture(true)
        val records = id.walt.wallet2.stores.inmemory.InMemoryIssuanceSessionStore()
        var proofs = emptyList<String>()
        var polls = 0
        val http = client(credentialStatus = HttpStatusCode.Accepted, credential = {
            proofs = it.proofs()
            """{"transaction_id":"interrupted-storage","interval":1}"""
        }, deferred = { polls++; batchTestResponse(proofs) })
        val owner = newSessionService(fixture.wallet, sessionStore = records, httpClient = http)
        val review = owner.start(WalletIssuanceSessionRequest(offerJson = offer()))
        val pending = assertIs<WalletIssuanceOutcome.Deferred>(owner.continuePreAuthorized(review.id,
            credentials = listOf(fixture.selection(2)))).credentials.single()
        val saved = mutableListOf<String>()
        assertFailsWith<kotlinx.coroutines.CancellationException> {
            owner.resumeWhenDue(pending.id, onCredentialStored = {
                saved += it.id
                throw kotlinx.coroutines.CancellationException("Interrupted after the first committed write")
            })
        }
        assertEquals(1, saved.size)
        val restarted = newSessionService(fixture.wallet, sessionStore = records, httpClient = http)
        val completed = assertIs<WalletIssuanceOutcome.Stored>(restarted.resumeWhenDue(pending.id,
            beforeCredentialsStored = { assertEquals(1, it) }, onCredentialStored = { saved += it.id }))
        assertEquals(2, saved.size)
        assertEquals(completed.credentialIds.toSet(), saved.toSet())
        assertEquals(1, polls)
        assertTrue(records.list().isEmpty())
    }

    @Test fun lostOrCancelledDeferredResponseCannotBeAutomaticallyRepolledAfterRestart() = runTest {
        for (cancel in listOf(false, true)) {
            val fixture = fixture(true)
            val records = MemorySessionStore()
            var polls = 0
            val http = client(credentialStatus = HttpStatusCode.Accepted,
                credential = { """{"transaction_id":"lost-response","interval":1}""" },
                deferred = {
                    polls++
                    if (cancel) throw kotlinx.coroutines.CancellationException("Interrupted after sending")
                    else throw java.io.IOException("Response lost after sending")
                })
            val service = newSessionService(fixture.wallet, sessionStore = records, httpClient = http)
            val review = service.start(WalletIssuanceSessionRequest(offerJson = offer()))
            val pending = assertIs<WalletIssuanceOutcome.Deferred>(service.continuePreAuthorized(review.id)).credentials.single()
            if (cancel) assertFailsWith<kotlinx.coroutines.CancellationException> { service.resumeWhenDue(pending.id) }
            else assertEquals(WalletIssuanceErrorCode.REMOTE_OUTCOME_UNCERTAIN,
                assertIs<WalletIssuanceOutcome.Failed>(service.resumeWhenDue(pending.id)).error.code)
            val restarted = newSessionService(fixture.wallet, sessionStore = records, httpClient = http)
            for (engine in listOf(service, restarted)) {
                val result = assertIs<WalletIssuanceOutcome.Failed>(engine.resumeWhenDue(pending.id))
                assertEquals(WalletIssuanceErrorCode.REMOTE_OUTCOME_UNCERTAIN, result.error.code)
                assertEquals(listOf(pending), result.deferredCredentials)
            }
            assertEquals(1, polls)
            assertTrue(fixture.store.listCredentials().toList().isEmpty())
        }
    }

    @Test fun cancelAndClearCannotDeleteAClaimMadeAfterTheirSnapshotRead() = runTest {
        for (clear in listOf(false, true)) {
            val fixture = fixture(true)
            val records = id.walt.wallet2.stores.inmemory.InMemoryIssuanceSessionStore()
            val snapshotRead = CompletableDeferred<Unit>()
            val releaseDeletion = CompletableDeferred<Unit>()
            val pollStarted = CompletableDeferred<Unit>()
            val releasePoll = CompletableDeferred<Unit>()
            val cancellingStore = object : WalletIssuanceSessionStore by records {
                override suspend fun list(): List<WalletIssuanceSessionRecord> {
                    val snapshot = records.list()
                    snapshotRead.complete(Unit)
                    releaseDeletion.await()
                    return snapshot
                }
            }
            var proofs = emptyList<String>()
            var polls = 0
            val http = client(credentialStatus = HttpStatusCode.Accepted, credential = {
                proofs = it.proofs()
                """{"transaction_id":"delete-race","interval":1}"""
            }, deferred = { polls++; pollStarted.complete(Unit); releasePoll.await(); batchTestResponse(proofs) })
            val first = newSessionService(fixture.wallet, sessionStore = records, httpClient = http)
            val review = first.start(WalletIssuanceSessionRequest(offerJson = offer()))
            val pending = assertIs<WalletIssuanceOutcome.Deferred>(first.continuePreAuthorized(review.id)).credentials.single()
            val second = newSessionService(fixture.wallet, sessionStore = cancellingStore, httpClient = http)
            val deleting = async {
                if (clear) assertFailsWith<IllegalStateException> { second.clearSessions() }
                else assertEquals(WalletIssuanceErrorCode.INVALID_SESSION,
                    assertIs<WalletIssuanceOutcome.Failed>(second.cancel(review.id)).error.code)
            }
            snapshotRead.await()
            val polling = async { first.resumeWhenDue(pending.id) }
            pollStarted.await()
            try {
                releaseDeletion.complete(Unit)
                deleting.await()
                assertEquals(1, records.list().size)
            } finally { releasePoll.complete(Unit) }
            assertIs<WalletIssuanceOutcome.Stored>(polling.await())
            assertEquals(1, polls)
            assertTrue(records.list().isEmpty())
        }
    }

    @Test fun deferredClaimMustBeDurableBeforeTheIssuerIsContacted() = runTest {
        val fixture = fixture(true)
        val records = MemorySessionStore()
        var unavailable = true
        val store = object : WalletIssuanceSessionStore by records {
            override suspend fun compareAndSet(expected: WalletIssuanceSessionRecord, replacement: WalletIssuanceSessionRecord?): Boolean {
                check(!unavailable || expected.kind != WalletIssuanceSessionRecordKind.DEFERRED_CREDENTIAL) { "Database unavailable" }
                return records.compareAndSet(expected, replacement)
            }
        }
        var proofs = emptyList<String>()
        var polls = 0
        val http = client(credentialStatus = HttpStatusCode.Accepted, credential = {
            proofs = it.proofs()
            """{"transaction_id":"claim-outage","interval":1}"""
        }, deferred = { polls++; batchTestResponse(proofs) })
        val service = newSessionService(fixture.wallet, sessionStore = store, httpClient = http)
        val review = service.start(WalletIssuanceSessionRequest(offerJson = offer()))
        val pending = assertIs<WalletIssuanceOutcome.Deferred>(service.continuePreAuthorized(review.id)).credentials.single()
        assertEquals(WalletIssuanceErrorCode.STORAGE,
            assertIs<WalletIssuanceOutcome.Failed>(service.resumeWhenDue(pending.id)).error.code)
        assertEquals(0, polls)
        unavailable = false
        assertIs<WalletIssuanceOutcome.Stored>(service.resumeWhenDue(pending.id))
        assertEquals(1, polls)
    }

    @Test fun cancelAndClearWinningTheRacePreventAStaleEngineFromPollingOrListingTheHandle() = runTest {
        for (clear in listOf(false, true)) {
            val fixture = fixture(true)
            val records = id.walt.wallet2.stores.inmemory.InMemoryIssuanceSessionStore()
            val claiming = CompletableDeferred<Unit>()
            val releaseClaim = CompletableDeferred<Unit>()
            val delayedStore = object : WalletIssuanceSessionStore by records {
                override suspend fun compareAndSet(expected: WalletIssuanceSessionRecord, replacement: WalletIssuanceSessionRecord?): Boolean {
                    if (replacement?.kind == WalletIssuanceSessionRecordKind.DEFERRED_CREDENTIAL) {
                        claiming.complete(Unit)
                        releaseClaim.await()
                    }
                    return records.compareAndSet(expected, replacement)
                }
            }
            var polls = 0
            val http = client(credentialStatus = HttpStatusCode.Accepted,
                credential = { """{"transaction_id":"cancel-wins","interval":1}""" },
                deferred = { polls++; error("Cancelled target must not reach the issuer") })
            val first = newSessionService(fixture.wallet, sessionStore = delayedStore, httpClient = http)
            val review = first.start(WalletIssuanceSessionRequest(offerJson = offer()))
            val pending = assertIs<WalletIssuanceOutcome.Deferred>(first.continuePreAuthorized(review.id)).credentials.single()
            val second = newSessionService(fixture.wallet, sessionStore = records, httpClient = http)
            val polling = async { first.resumeWhenDue(pending.id) }
            claiming.await()
            try {
                if (clear) second.clearSessions()
                else assertIs<WalletIssuanceOutcome.Cancelled>(second.cancel(review.id))
            } finally { releaseClaim.complete(Unit) }
            assertIs<WalletIssuanceOutcome.Failed>(polling.await())
            assertEquals(0, polls)
            assertTrue(records.list().isEmpty())
            assertTrue(first.listDeferredCredentials().isEmpty())
            assertEquals(WalletIssuanceErrorCode.INVALID_SESSION,
                assertIs<WalletIssuanceOutcome.Failed>(first.resumeWhenDue(pending.id)).error.code)
        }
    }

    @Test fun closingWalletRejectsLateImmediateAndDeferredFullFlowResponses() = runTest {
        for (pendingResponse in listOf(false, true)) {
            val fixture = fixture(true)
            val records = MemorySessionStore()
            val state = WalletIssuanceSessionState(fixture.wallet.id, records)
            val wallet = fixture.wallet.attachIssuanceSessionState(state)
            val requestStarted = CompletableDeferred<Unit>()
            val releaseResponse = CompletableDeferred<Unit>()
            var requests = 0
            val http = client(credentialStatus = if (pendingResponse) HttpStatusCode.Accepted else HttpStatusCode.OK,
                credential = {
                    requests++
                    requestStarted.complete(Unit)
                    releaseResponse.await()
                    if (pendingResponse) """{"transaction_id":"late-response","interval":1}"""
                    else batchTestResponse(it.proofs())
                })
            val receive = async {
                WalletIssuanceHandler.receiveCredential(wallet, ReceiveCredentialRequest(offerJson = offer()), httpClient = http,
                    beforeCredentialsStored = { error("A closed wallet must not reserve storage") },
                    onCredentialStored = { error("A closed wallet must not save a credential") })
            }
            requestStarted.await()
            val service = wallet.issuanceSessions(http)
            service.closeSessions()
            releaseResponse.complete(Unit)
            val result = receive.await()
            assertNotNull(result.failure)
            assertTrue(result.credentialIds.isEmpty())
            assertTrue(result.deferredCredentials.isEmpty())
            assertTrue(service.listDeferredCredentials().isEmpty())
            assertTrue(records.list().isEmpty())
            assertTrue(fixture.store.listCredentials().toList().isEmpty())
            assertFailsWith<IllegalStateException> {
                WalletIssuanceHandler.receiveCredential(wallet, ReceiveCredentialRequest(offerJson = offer()), httpClient = http)
            }
            assertEquals(1, requests)
        }
    }

    @Test fun acceptedFullFlowTargetSurvivesInitialContinuationWriteFailureAcrossRequests() = runTest {
        val fixture = fixture(true)
        val records = MemorySessionStore()
        var unavailable = true
        val store = object : WalletIssuanceSessionStore by records {
            override suspend fun put(record: WalletIssuanceSessionRecord) {
                check(!unavailable) { "Database unavailable" }
                records.put(record)
            }
        }
        val state = WalletIssuanceSessionState(fixture.wallet.id, store)
        val wallet = fixture.wallet.copy().attachIssuanceSessionState(state)
        var proofs = emptyList<String>()
        var requests = 0
        var polls = 0
        val http = client(credentialStatus = HttpStatusCode.Accepted, credential = {
            requests++
            proofs = it.proofs()
            """{"transaction_id":"accepted-during-outage","interval":1}"""
        }, deferred = { polls++; batchTestResponse(proofs) })
        val result = WalletIssuanceHandler.receiveCredential(wallet,
            ReceiveCredentialRequest(offerJson = offer()), httpClient = http)
        assertEquals(CredentialIssuanceStage.STORAGE, assertNotNull(result.failure).stage)
        val handle = assertNotNull(result.deferredCredentials.single().deferredCredentialId)
        unavailable = false
        val next = wallet.copy().attachIssuanceSessionState(state).issuanceSessions(http)
        assertEquals(handle, next.listDeferredCredentials().single().id)
        assertEquals(1, assertIs<WalletIssuanceOutcome.Stored>(next.resumeWhenDue(handle)).credentialIds.size)
        assertEquals(1, requests)
        assertEquals(1, polls)
    }

    @Test fun sharedStateDoesNotReuseAnotherRequestsKeyAccess() = runTest {
        val fixture = fixture(true)
        val state = WalletIssuanceSessionState(fixture.wallet.id, MemorySessionStore())
        var proofs = emptyList<String>()
        var polls = 0
        val http = client(credentialStatus = HttpStatusCode.Accepted, credential = {
            proofs = it.proofs()
            """{"transaction_id":"permissions","interval":1}"""
        }, deferred = { polls++; batchTestResponse(proofs) })
        val permitted = fixture.wallet.copy().attachIssuanceSessionState(state).issuanceSessions(http)
        val review = permitted.start(WalletIssuanceSessionRequest(offerJson = offer()))
        val pending = assertIs<WalletIssuanceOutcome.Deferred>(permitted.continuePreAuthorized(review.id)).credentials.single()
        val denied = fixture.wallet.copy(keyStores = emptyList()).attachIssuanceSessionState(state).issuanceSessions(http)
        assertIs<WalletIssuanceOutcome.Failed>(denied.resumeWhenDue(pending.id))
        assertEquals(0, polls)
        assertEquals(listOf(pending), permitted.listDeferredCredentials())
        assertIs<WalletIssuanceOutcome.Stored>(permitted.resumeWhenDue(pending.id))
        assertEquals(1, polls)
        assertFailsWith<IllegalArgumentException> {
            fixture.wallet.copy(id = "different-wallet").attachIssuanceSessionState(state)
        }
    }

    @Test fun sharedAuthorizationContinuationRechecksEveryAcceptedHolderKeyBeforeExchangingCode() = runTest {
        val fixture = fixture(true)
        val state = WalletIssuanceSessionState(fixture.wallet.id, MemorySessionStore())
        var exchanges = 0
        val http = client(token = { exchanges++; TOKEN }, credential = { error("No credential request is allowed") })
        val permitted = fixture.wallet.copy().attachIssuanceSessionState(state).issuanceSessions(http)
        val review = permitted.start(WalletIssuanceSessionRequest(credentialIssuer = ISSUER,
            credentialConfigurationIds = listOf("identity"), redirectUri = Url("openid://callback")))
        val authorization = permitted.beginAuthorization(review.id, listOf(fixture.selection(2)))
        // The second request may still access the sender key but no longer the second holder key.
        val restrictedKeys = InMemoryKeyStore()
        restrictedKeys.addCrypto2Key(fixture.keys.first().crypto2Key!!)
        val restricted = fixture.wallet.copy(keyStores = listOf(restrictedKeys))
            .attachIssuanceSessionState(state).issuanceSessions(http)
        assertIs<WalletIssuanceOutcome.Failed>(restricted.continueAuthorization(WalletIssuanceAuthorizationCallback(
            review.id, "openid://callback?code=code&state=${authorization.state}")))
        assertEquals(0, exchanges)
    }

    @Test fun walletInitiatedAuthorizationSelectsOnlyAcceptedConfigurationsAndRetainsKeys() = runTest {
        val fixture = fixture(true)
        val records = MemorySessionStore()
        val http = client(credential = { batchTestResponse(it.proofs()) })
        val service = newSessionService(fixture.wallet, httpClient = http, sessionStore = records)
        val preview = service.start(WalletIssuanceSessionRequest(
            credentialIssuer = ISSUER, credentialConfigurationIds = listOf("identity"),
            redirectUri = Url("openid://callback")))
        val authorization = service.beginAuthorization(preview.id, listOf(fixture.selection(2)))
        val details = Url(authorization.url).parameters["authorization_details"]!!
        assertEquals("identity", Json.parseToJsonElement(details).jsonArray.single().jsonObject["credential_configuration_id"]!!.jsonPrimitive.content)
        val restored = newSessionService(fixture.wallet, httpClient = http, sessionStore = records)
        val result = restored.continueAuthorization(WalletIssuanceAuthorizationCallback(preview.id,
            "openid://callback?code=code&state=${authorization.state}"))
        assertEquals(2, assertIs<WalletIssuanceOutcome.Stored>(result).credentialIds.size)
    }


    @Test fun nonceRetryRebuildsEveryProofWithSameHolderKeys() = runTest {
        val fixture = fixture(true)
        val collections = mutableListOf<List<String>>()
        var nonceRequests = 0
        val http = HttpClient(MockEngine) {
            engine { addHandler { request ->
                when (request.url.encodedPath) {
                    "/nonce" -> respond("""{"c_nonce":"nonce-${++nonceRequests}"}""",
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
                    "/credential" -> {
                        val body = Json.parseToJsonElement((request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()).jsonObject
                        collections += body.proofs()
                        respond(if (collections.size == 1) """{"error":"invalid_nonce"}""" else batchTestResponse(body.proofs()),
                            if (collections.size == 1) HttpStatusCode.BadRequest else HttpStatusCode.OK,
                            headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
                    }
                    else -> error("Unexpected request")
                }
            } }
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val config = Json.decodeFromString<id.walt.openid4vci.metadata.issuer.CredentialIssuerMetadata>(metadata())
        val selected = fixture.wallet.resolveCredentialSelections(listOf(fixture.selection(5)), listOf("identity"),
            config, fixture.keys.first(), null).single()
        WalletIssuanceHandler.requestCredentialWithNonceRetry(
            FetchCredentialRequest(Url("$ISSUER/credential"), "access", "identity"), "$ISSUER/nonce", http,
            buildProof = { WalletIssuanceHandler.buildProofCollection(fixture.wallet, selected, config.credentialConfigurationsSupported.getValue("identity"), ISSUER, it, null) })
        assertEquals(listOf(5, 5), collections.map { it.size })
        for ((index, proofs) in collections.withIndex()) {
            assertTrue(proofs.all { proof ->
                Json.parseToJsonElement(CompactJws.decodeUnverified(proof).payload.decodeToString()).jsonObject["nonce"]!!.jsonPrimitive.content == "nonce-${index + 1}"
            })
        }
        assertEquals(collections.first().map { CompactJws.decodeUnverified(it).protectedHeader["jwk"] },
            collections.last().map { CompactJws.decodeUnverified(it).protectedHeader["jwk"] })
    }

    @Test fun presentationUsesTheStoredBatchKeyInsteadOfWalletDefault() = runTest {
        for (crypto2 in listOf(false, true)) {
            val fixture = fixture(crypto2)
            val result = WalletIssuanceHandler.receiveCredential(fixture.wallet,
                ReceiveCredentialRequest(offerJson = offer(), credentials = listOf(fixture.selection(2))),
                httpClient = client(credential = { batchTestResponse(it.proofs().reversed()) }))
            val credentialId = result.credentialIds.first() // holder #2; wallet default is holder #1
            val request = id.walt.verifier.openid.models.authorization.AuthorizationRequest(
                clientId = "redirect_uri:https://verifier.example/callback",
                redirectUri = "https://verifier.example/callback", nonce = "presentation-nonce",
                responseType = id.walt.verifier.openid.models.openid.OpenID4VPResponseType.VP_TOKEN,
                dcqlQuery = id.walt.dcql.models.DcqlQuery(credentials = listOf(
                    id.walt.dcql.models.CredentialQuery(id = "identity", format = id.walt.dcql.models.CredentialFormat.DC_SD_JWT,
                        meta = id.walt.dcql.models.meta.SdJwtVcMeta(vctValues = listOf("identity")))) ),
            )
            val presentation = WalletPresentationHandler.buildVpToken(fixture.wallet,
                BuildVpTokenRequest(Url("https://verifier.example/request"), selectedCredentialIds = mapOf("identity" to listOf(credentialId))),
                resolveAuthorizationRequest = { id.waltid.openid4vp.wallet.request.ResolvedAuthorizationRequest.Plain(request) })
            val sdJwt = Json.parseToJsonElement(presentation.vpToken).jsonObject["identity"]!!.jsonArray.single().jsonPrimitive.content
            val keyBindingJwt = sdJwt.substringAfterLast('~')
            val holder = fixture.keys[1]
            if (holder.crypto2Key != null) CompactJws.verify(keyBindingJwt, holder.crypto2Key, id.walt.crypto2.jose.JwsAlgorithm.ES256)
            else assertTrue(holder.legacyKey!!.verifyJws(keyBindingJwt).isSuccess)
        }
    }


    @Test fun scaAuthenticationAndProofUseTheSelectedBatchKey() = runTest {
        val fixture = fixture(true)
        val vct = "https://webuildconsortium.eu/sca/sca-iban/1.0"
        val issued = WalletIssuanceHandler.receiveCredential(fixture.wallet,
            ReceiveCredentialRequest(offerJson = offer(), credentials = listOf(fixture.selection(2))),
            httpClient = client(metadata = metadata().replace("\"vct\":\"identity\"", "\"vct\":\"$vct\""),
                credential = { batchTestResponse(it.proofs().reversed(), vct) }))
        val credentialId = issued.credentialIds.first()
        val holder = fixture.keys[1].requireCrypto2Key()
        val transaction = buildJsonObject {
            put("type", "urn:eudi:sca:payment:1")
            putJsonArray("credential_ids") { add("payment") }
            putJsonArray("transaction_data_hashes_alg") { add("sha-256") }
            putJsonObject("payload") {
                put("transaction_id", "batch-payment"); put("currency", "EUR"); put("amount", 12.5)
                putJsonObject("payee") { put("name", "Test shop"); put("id", "test-shop") }
            }
        }.toString().encodeToByteArray().encodeToBase64Url()
        val delivered = AtomicReference<String>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/response") { exchange ->
            delivered.set(exchange.requestBody.bufferedReader().readText())
            exchange.responseHeaders.set("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, 2)
            exchange.responseBody.use { it.write("{}".toByteArray()) }
        }
        server.start()
        try {
            val request = id.walt.verifier.openid.models.authorization.AuthorizationRequest(
                clientId = "redirect_uri:https://verifier.example/callback",
                responseUri = "http://127.0.0.1:${server.address.port}/response",
                nonce = "batch-payment-nonce",
                responseType = id.walt.verifier.openid.models.openid.OpenID4VPResponseType.VP_TOKEN,
                responseMode = id.walt.verifier.openid.models.openid.OpenID4VPResponseMode.DIRECT_POST,
                transactionData = listOf(transaction),
                dcqlQuery = id.walt.dcql.models.DcqlQuery(listOf(id.walt.dcql.models.CredentialQuery(
                    id = "payment", format = id.walt.dcql.models.CredentialFormat.DC_SD_JWT,
                    meta = id.walt.dcql.models.meta.SdJwtVcMeta(listOf(vct)),
                ))),
            )
            val registry = TransactionDataTypeRegistry("urn:eudi:sca:payment:1")
            val preview = assertIs<PreviewPresentationResult.Ready>(WalletPresentationHandler.previewPresentation(
                wallet = fixture.wallet,
                request = PreviewPresentationRequest(Url("https://verifier.example/request")),
                executionKey = { fixture.keys.first() }, onEvent = {}, transactionDataTypeRegistry = registry,
                resolveAuthorizationRequest = { id.waltid.openid4vp.wallet.request.ResolvedAuthorizationRequest.Plain(request) },
            ))
            val factors = ScaAuthenticationMethods.KnowledgeAndPossession(
                ScaAuthenticationMethods.Knowledge.PIN_6_OR_MORE_DIGITS,
                ScaAuthenticationMethods.Possession.KEY_IN_LOCAL_NATIVE_WSCD,
            )
            val authenticated = mutableListOf<ScaPresentation>()
            val result = WalletPresentationHandler.submitPresentation(fixture.wallet,
                SubmitPresentationRequest(preview.handle, listOf(PresentationCredentialSelection("payment", credentialId))),
                transactionDataTypeRegistry = registry,
                scaAuthorizer = WalletScaPresentationAuthorizer { key, presentation ->
                    assertSame(holder, key)
                    authenticated += presentation
                    factors // Simulated factors; this test does not exercise device authentication.
                })
            assertEquals(true, result.transmissionSuccess)
            val proof = authenticated.single()
            assertEquals(holder.id.value, proof.holderKeyId)
            assertEquals(credentialId, proof.credentialId)
            assertEquals(listOf(transaction), proof.transactionData)
            val vp = Json.parseToJsonElement(assertNotNull(parseQueryString(assertNotNull(delivered.get()))["vp_token"]))
                .jsonObject.getValue("payment").jsonArray.single().jsonPrimitive.content
            val verified = CompactJws.verify(vp.substringAfterLast('~'), holder, JwsAlgorithm.ES256)
            val payload = Json.parseToJsonElement(verified.payload.decodeToString()).jsonObject
            assertEquals(proof.proofId, payload.getValue("jti").jsonPrimitive.content)
            assertEquals(Json.parseToJsonElement("""[{"knowledge":"pin_6_or_more_digits"},{"possession":"key_in_local_native_wscd"}]"""), payload["amr"])
        } finally { server.stop(0) }
    }

    @Test fun deferredBatchWithMissingHolderKeyFailsBeforePollingAndRetainsRecord() = runTest {
        for (persisted in listOf(false, true)) {
            val fixture = fixture(true)
            val records = MemorySessionStore().takeIf { persisted }
            var polls = 0
            val http = client(credentialStatus = HttpStatusCode.Accepted,
                credential = { """{"transaction_id":"batch","interval":1}""" },
                deferred = { polls++; error("Must not poll with a missing key") })
            val service = newSessionService(fixture.wallet, httpClient = http, sessionStore = records)
            val preview = service.start(WalletIssuanceSessionRequest(offerJson = offer()))
            val pending = assertIs<WalletIssuanceOutcome.Deferred>(
                service.continuePreAuthorized(preview.id, credentials = listOf(fixture.selection(2))))
            fixture.wallet.keyStores.single().removeKey(fixture.keys[1].keyId)
            assertIs<WalletIssuanceOutcome.Failed>(service.resumeWhenDue(pending.credentials.single().id))
            val restored = newSessionService(fixture.wallet, httpClient = http, sessionStore = records)
            assertIs<WalletIssuanceOutcome.Failed>(restored.resumeWhenDue(pending.credentials.single().id))
            assertEquals(0, polls)
            assertEquals(1, service.listDeferredCredentials().size)
        }
    }

    private suspend fun fixture(crypto2: Boolean): Fixture {
        val keys = InMemoryKeyStore()
        val ids = (1..5).map { index ->
            if (crypto2) keys.addCrypto2Key(CryptoRuntime(defaultSoftwareKeyProviders()).generateSoftwareKey(
                GenerateSoftwareKeyRequest(KeyId("holder-$index"), KeySpec.Ec(EcCurve.P256), setOf(KeyUsage.SIGN, KeyUsage.VERIFY))))
            else keys.addKey(JWKKey.generate(KeyType.secp256r1))
        }
        val store = InMemoryCredentialStore()
        val wallet = Wallet("batch-wallet", keyStores = listOf(keys), credentialStores = listOf(store), defaultKeyId = ids.first())
        return Fixture(wallet, store, ids.map { wallet.resolveKeyMaterial(it, setOf(KeyUsage.SIGN))!! })
    }

    private data class Fixture(val wallet: Wallet, val store: InMemoryCredentialStore, val keys: List<WalletKeyStoreEntry>) {
        fun selection(count: Int) = WalletCredentialSelection("identity", holderBindings = keys.take(count).map { CredentialHolderBinding(it.keyId) })
    }

    private class MemorySessionStore : WalletIssuanceSessionStore {
        val records = mutableMapOf<String, WalletIssuanceSessionRecord>()
        override suspend fun get(id: String) = records[id]
        override suspend fun put(record: WalletIssuanceSessionRecord) { records[record.id] = record }
        override suspend fun compareAndSet(expected: WalletIssuanceSessionRecord, replacement: WalletIssuanceSessionRecord?): Boolean {
            require(replacement == null || expected.id == replacement.id)
            if (records[expected.id] != expected) return false
            if (replacement == null) records.remove(expected.id) else records[expected.id] = replacement
            return true
        }
        override suspend fun remove(id: String): Boolean = records.remove(id) != null
        override suspend fun list() = records.values.toList()
    }

    private fun JsonObject.proofs(): List<String> = getValue("proofs").jsonObject.getValue("jwt").jsonArray.map { it.jsonPrimitive.content }
    private fun offer(): JsonObject = Json.parseToJsonElement(
        """{"credential_issuer":"$ISSUER","credential_configuration_ids":["identity"],
            "grants":{"urn:ietf:params:oauth:grant-type:pre-authorized_code":{"pre-authorized_code":"pre-code"}}}""").jsonObject

    private fun metadata(batchSize: Int = 5) = """{"credential_issuer":"$ISSUER","credential_endpoint":"$ISSUER/credential",
        "deferred_credential_endpoint":"$ISSUER/deferred","nonce_endpoint":"$ISSUER/nonce","batch_credential_issuance":{"batch_size":$batchSize},
        "credential_configurations_supported":{"identity":{"format":"dc+sd-jwt","vct":"identity","scope":"identity",
        "cryptographic_binding_methods_supported":["jwk"],"proof_types_supported":{"jwt":{"proof_signing_alg_values_supported":["ES256"]}}}}}"""

    private fun client(
        metadata: String = metadata(),
        authorizationDetailsSupported: Boolean = true,
        token: (Parameters) -> String = { TOKEN },
        par: ((Parameters) -> Unit)? = null,
        credentialStatus: HttpStatusCode = HttpStatusCode.OK,
        credential: suspend (JsonObject) -> String,
        deferredStatus: () -> HttpStatusCode = { HttpStatusCode.OK },
        deferred: suspend (JsonObject) -> String = { error("Unexpected deferred request") },
    ) = HttpClient(MockEngine) {
        engine {
            addHandler { request ->
                var status = HttpStatusCode.OK
                val content = when (request.url.toString()) {
                    "$ISSUER/.well-known/openid-credential-issuer" -> metadata
                    "$ISSUER/.well-known/oauth-authorization-server" ->
                        """{"issuer":"$ISSUER","token_endpoint":"$ISSUER/token","authorization_endpoint":"$ISSUER/authorize",
                            ${if (authorizationDetailsSupported) "\"authorization_details_types_supported\":[\"openid_credential\"]," else ""}
                            ${if (par != null) "\"pushed_authorization_request_endpoint\":\"$ISSUER/par\",\"require_pushed_authorization_requests\":true," else ""}
                            "response_types_supported":["code"]}"""
                    "$ISSUER/token" -> token(parseQueryString((request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()))
                    "$ISSUER/par" -> {
                        status = HttpStatusCode.Created
                        requireNotNull(par)(parseQueryString((request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()))
                        """{"request_uri":"urn:ietf:params:oauth:request_uri:test","expires_in":60}"""
                    }
                    "$ISSUER/nonce" -> """{"c_nonce":"nonce"}"""
                    "$ISSUER/credential" -> {
                        assertEquals("Bearer access", request.headers[HttpHeaders.Authorization])
                        status = credentialStatus
                        credential(Json.parseToJsonElement((request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()).jsonObject)
                    }
                    "$ISSUER/deferred" -> {
                        status = deferredStatus()
                        deferred(Json.parseToJsonElement((request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()).jsonObject)
                    }
                    else -> error("Unexpected endpoint ${request.url}")
                }
                respond(content, status, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
            }
        }
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }

    companion object {
        const val ISSUER = "https://issuer.example"
        const val TOKEN = """{"access_token":"access","token_type":"Bearer"}"""
    }
}
