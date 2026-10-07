package id.walt.wallet2.handlers

import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.*

class ReleasedFetchResultTest {
    @Test
    fun releasedShapeStaysExactAndDetailedResultsRejectImpossibleCombinations() {
        val json = Json { encodeDefaults = true }
        val released = FetchCredentialResult(listOf("raw"))
        val (raws) = released.copy()
        assertEquals(listOf("raw"), raws)
        assertEquals("""{"rawCredentials":["raw"]}""", json.encodeToString(released))
        assertEquals(released, json.decodeFromString<FetchCredentialResult>(json.encodeToString(released)))
        val stored = FetchCredentialsResult(raws, storageOutcome = WalletIssuanceOutcome.Stored("session", listOf("id")))
        assertEquals(released, FetchCredentialResult(stored))
        val pending = DeferredCredentialTransaction("identity", transactionId = "tx",
            holderBindings = listOf(CredentialHolderBinding()), intervalSeconds = 2)
        for (invalid in listOf<() -> FetchCredentialsResult>(
            { FetchCredentialsResult() },
            { FetchCredentialsResult(raws, pending) },
            { FetchCredentialsResult(deferredCredential = pending, storageOutcome = stored.storageOutcome) },
            { FetchCredentialsResult(raws, storageOutcome = WalletIssuanceOutcome.Cancelled("session")) },
        )) assertFailsWith<IllegalArgumentException> { invalid() }
        val deferred = FetchCredentialsResult(deferredCredential = pending)
        assertEquals(deferred, assertFailsWith<CredentialFetchException> { FetchCredentialResult(deferred) }.result)
    }

    @Test
    fun releasedFetchRetainsRemoteDeferralWithoutRetryingTheRequest() = runTest {
        val fixture = batchTestFixture(true)
        var requests = 0
        val http = batchTestClient(credentialStatus = HttpStatusCode.Accepted,
            credential = { requests++; """{"transaction_id":"tx","interval":2}""" })
        try {
            val request = FetchCredentialRequest(Url("$BATCH_TEST_ISSUER/credential"), "access", "identity")
            val error = assertFailsWith<CredentialFetchException> {
                WalletIssuanceHandler.fetchCredential(fixture.wallet, request, http)
            }
            val pending = assertNotNull(error.result.deferredCredential)
            assertEquals("tx", pending.transactionId)
            assertEquals(2L, pending.intervalSeconds)
            assertEquals(1, requests)
        } finally { http.close() }
    }

    @Test
    fun releasedFetchRetainsLocalSaveFailureAndDoesNotReportSuccess() = runTest {
        val fixture = batchTestFixture(true)
        var requests = 0
        val http = batchTestClient(credential = { requests++; batchTestResponse(it.batchProofs()) })
        try {
            val bindings = fixture.selection(1).holderBindings
            val proofs = WalletIssuanceHandler.signProofs(fixture.wallet,
                SignProofsRequest(bindings, Url(BATCH_TEST_ISSUER), "identity"), http).proofs
            val error = assertFailsWith<CredentialFetchException> {
                WalletIssuanceHandler.fetchCredential(fixture.wallet,
                    FetchCredentialRequest(Url("$BATCH_TEST_ISSUER/credential"), "access", "identity",
                        proofs = proofs, holderBindings = bindings, storeInWallet = true,
                        credentialIssuerBaseUrl = BATCH_TEST_ISSUER), http,
                    beforeCredentialsStored = { error("quota unavailable") })
            }
            assertEquals(1, error.result.rawCredentials.size)
            val failure = assertIs<WalletIssuanceOutcome.Failed>(error.result.storageOutcome)
            assertEquals(WalletIssuanceErrorCode.STORAGE, failure.error.code)
            assertEquals(1, failure.deferredCredentials.size)
            assertTrue(failure.storedCredentialIds.isEmpty())
            assertEquals(1, requests)
        } finally { http.close() }
    }
}
