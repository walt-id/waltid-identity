package id.walt.openid4vci.deferred

import id.walt.openid4vci.CredentialFormat
import id.walt.openid4vci.DefaultClient
import id.walt.openid4vci.metadata.issuer.CredentialConfiguration
import id.walt.openid4vci.requests.credential.AcceptedCredentialRequest
import id.walt.openid4vci.requests.credential.DefaultCredentialRequest
import id.walt.openid4vci.responses.credential.CredentialResponse
import id.walt.openid4vci.responses.credential.IssuedCredential
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.*
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class DeferredIssuanceTest {
    private var time = Instant.parse("2026-01-01T00:00:00Z")
    private val store = Store(DeferredSessionSnapshot("offer", time + 5.minutes, DeferredIssuanceState()))
    private fun service() = DeferredIssuance(store, now = { time })
    private fun accepted(id: String = "credential") = AcceptedCredentialRequest(
        DefaultCredentialRequest(
            client = DefaultClient("wallet", emptyList(), emptySet(), emptySet()), credentialIdentifier = id,
            credentialConfigurationId = null, proofs = null, credentialResponseEncryption = null,
            accessTokenClientId = "wallet",
        ), id, "config", CredentialConfiguration(CredentialFormat.SD_JWT_VC, vct = "https://example.com/credential"), emptyList(),
    )

    @Test fun pendingReadyConsumedAndIdempotentApproval() = runTest {
        val service = service()
        val deadline = time + 7.days
        service.start("offer", deadline)
        val transaction = service.accept("offer", accepted())
        val pending = assertIs<DeferredIssuance.Collection.Pending>(service.collect("offer", transaction.id, setOf("credential"), "wallet"))
        assertEquals(30L, pending.response.interval)
        service.decide("offer", DeferredStatus.READY)
        service.decide("offer", DeferredStatus.READY)
        assertNull(store.value!!.state!!.transactions[transaction.id]!!.response, "Completion must not issue credentials")
        val claim = assertIs<DeferredIssuance.Collection.Claimed>(service.collect("offer", transaction.id, setOf("credential"), "wallet")).transaction
        service.checkpoint("offer", claim, CredentialResponse(listOf(IssuedCredential(JsonPrimitive("signed")))))
        service.consume("offer", claim)
        assertEquals("invalid_transaction_id", assertFailsWith<DeferredProtocolException> { service.collect("offer", transaction.id, setOf("credential"), "wallet") }.error.error)
        service.decide("offer", DeferredStatus.READY)
        assertEquals(deadline, store.value!!.state!!.deadline)
        assertEquals(409, assertFailsWith<DeferredDecisionException> { service.decide("offer", DeferredStatus.DENIED) }.httpStatus)
    }

    @Test fun denialIsRetainedAndCannotBeReversed() = runTest {
        val service = service()
        val transaction = service.accept("offer", accepted())
        service.decide("offer", DeferredStatus.DENIED)
        service.decide("offer", DeferredStatus.DENIED)
        assertEquals("credential_request_denied", assertFailsWith<DeferredProtocolException> { service.collect("offer", transaction.id, setOf("credential"), "wallet") }.error.error)
        assertEquals(409, assertFailsWith<DeferredDecisionException> { service.decide("offer", DeferredStatus.READY) }.httpStatus)
        time += 6.minutes
        assertEquals("invalid_transaction_id", assertFailsWith<DeferredProtocolException> { service.collect("offer", transaction.id, setOf("credential"), "wallet") }.error.error)
        assertEquals(404, assertFailsWith<DeferredDecisionException> { service.decide("offer", DeferredStatus.DENIED) }.httpStatus)
    }

    @Test fun authorizationAndOrdinaryOfferFailures() = runTest {
        val transaction = service().accept("offer", accepted())
        for ((ids, client) in listOf(setOf("other") to "wallet", setOf("credential") to "other")) {
            assertEquals("invalid_transaction_id", assertFailsWith<DeferredProtocolException> { service().collect("offer", transaction.id, ids, client) }.error.error)
        }
        store.value = store.value!!.copy(state = null)
        assertEquals(409, assertFailsWith<DeferredDecisionException> { service().decide("offer", DeferredStatus.READY) }.httpStatus)
        store.value = null
        assertEquals(404, assertFailsWith<DeferredDecisionException> { service().decide("offer", DeferredStatus.READY) }.httpStatus)
    }

    @Test fun concurrentPollsHaveOneOwnerAndRestartReusesCheckpoint() = runTest {
        val service = service()
        service.start("offer", time + 7.days)
        service.decide("offer", DeferredStatus.READY)
        val first = service.accept("offer", accepted())
        val second = service.accept("offer", accepted("second"))
        val results = (1..20).map { async { service.collect("offer", first.id, setOf("credential"), "wallet") } }.awaitAll()
        assertEquals(1, results.count { it is DeferredIssuance.Collection.Claimed })
        val claim = results.filterIsInstance<DeferredIssuance.Collection.Claimed>().single().transaction
        val response = CredentialResponse(listOf(IssuedCredential(JsonPrimitive("only-signed-once"))))
        service.checkpoint("offer", claim, response)
        // Persist and reload continuation bytes, then allow the abandoned claim's lease to expire.
        val snapshot = store.value!!
        store.value = snapshot.copy(state = Json.decodeFromString(Json.encodeToString(snapshot.state!!)))
        time += 6.minutes
        val restarted = service()
        val resumed = assertIs<DeferredIssuance.Collection.Claimed>(restarted.collect("offer", first.id, setOf("credential"), "wallet")).transaction
        assertEquals(response, resumed.response)
        assertFailsWith<IllegalStateException> { service.checkpoint("offer", claim, response) }
        restarted.consume("offer", resumed)
        assertIs<DeferredIssuance.Collection.Claimed>(restarted.collect("offer", second.id, setOf("second"), "wallet"))
        val originalDeadline = store.value!!.state!!.deadline
        restarted.start("offer", time + 7.days)
        assertEquals(originalDeadline, store.value!!.state!!.deadline)
        time = originalDeadline!!
        assertEquals("invalid_transaction_id", assertFailsWith<DeferredProtocolException> { restarted.collect("offer", second.id, setOf("second"), "wallet") }.error.error)
    }

    @Test fun equivalentConcurrentRetriesReuseOriginalAcceptanceAcrossReload() = runTest {
        val first = service().accept("offer", accepted())
        val results = (1..20).map { async { service().accept("offer", accepted()) } }.awaitAll()
        assertTrue(results.all { it.id == first.id && it.accepted == first.accepted })
        assertEquals(1, store.value!!.state!!.transactions.size)
        val snapshot = store.value!!
        store.value = snapshot.copy(state = Json.decodeFromString(Json.encodeToString(snapshot.state!!)))
        assertEquals(first, service().accept("offer", accepted()))
        service().decide("offer", DeferredStatus.READY)
        val claim = assertIs<DeferredIssuance.Collection.Claimed>(service().collect("offer", first.id, setOf("credential"), "wallet")).transaction
        assertEquals(first.id, service().accept("offer", accepted()).id)
        assertIs<DeferredIssuance.Collection.Pending>(service().collect("offer", first.id, setOf("credential"), "wallet"))
        val response = CredentialResponse(listOf(IssuedCredential(JsonPrimitive("signed-once"))))
        service().checkpoint("offer", claim, response)
        service().release("offer", claim)
        assertEquals(response, service().accept("offer", accepted()).response)
        val recovered = assertIs<DeferredIssuance.Collection.Claimed>(service().collect("offer", first.id, setOf("credential"), "wallet")).transaction
        service().consume("offer", recovered)
        assertFailsWith<DeferredProtocolException> { service().accept("offer", accepted()) }
    }

    @Test fun conflictingRetriesDoNotChangeAcceptedTransaction() = runTest {
        val original = accepted()
        service().accept("offer", original)
        val snapshot = store.value
        for (conflict in listOf(
            original.copy(request = original.request.copy(accessTokenClientId = "another-wallet")),
            original.copy(request = original.request.copy(requestForm = mapOf("display" to listOf("changed")))),
            original.copy(credentialConfigurationId = "other-config"),
            original.copy(configuration = original.configuration.copy(vct = "https://example.com/other")),
        )) {
            assertEquals("invalid_credential_request", assertFailsWith<DeferredProtocolException> {
                service().accept("offer", conflict)
            }.error.error)
            assertEquals(snapshot, store.value)
        }
        service().decide("offer", DeferredStatus.DENIED)
        assertEquals("credential_request_denied", assertFailsWith<DeferredProtocolException> { service().accept("offer", original) }.error.error)
        time += 6.minutes
        assertEquals("invalid_transaction_id", assertFailsWith<DeferredProtocolException> { service().accept("offer", original) }.error.error)
    }

    private class Store(var value: DeferredSessionSnapshot?) : DeferredIssuanceStore {
        private val mutex = Mutex()
        override suspend fun get(id: String) = mutex.withLock { value?.takeIf { it.id == id } }
        override suspend fun compareAndSet(expected: DeferredSessionSnapshot, replacement: DeferredIssuanceState) = mutex.withLock {
            if (value != expected) false else {
                value = expected.copy(state = replacement, expiresAt = replacement.deadline ?: expected.expiresAt)
                true
            }
        }
    }
}
