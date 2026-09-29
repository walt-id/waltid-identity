package id.walt.wallet2.handlers

import id.walt.wallet2.data.Wallet
import io.ktor.client.HttpClient
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

class ReleasedReceiveResultTest {
    @Test
    fun releasedCollectorsKeepMapConstructionCopyComponentsAndStrictWireShape() = runTest {
        val expected = ReceiveCredentialResult(listOf("stored"), mapOf("identity" to "transaction"))
        val copied = expected.copy(credentialIds = emptyList())
        val transactions: Map<String, String> = copied.component2()
        assertEquals(mapOf("identity" to "transaction"), transactions)
        val json = Json { encodeDefaults = true }
        assertEquals(expected, json.decodeFromString<ReceiveCredentialResult>(json.encodeToString(expected)))
        for (entry in Entry.entries) {
            val fixture = batchTestFixture(true)
            batchTestClient(credentialStatus = HttpStatusCode.Accepted,
                credential = { """{"transaction_id":"transaction","interval":5}""" }).use { http ->
                val result = entry.receive(fixture.wallet, http)
                assertEquals(copied, result)
                assertEquals(setOf("credentialIds", "deferredTransactionIds"), json.encodeToJsonElement(result).jsonObject.keys)
                assertEquals(1, fixture.wallet.issuanceSessions(http).listDeferredCredentials().size)
            }
        }
    }

    @Test
    fun releasedCollectorsRetainEveryDeferredDatasetAndPartialFailure() = runTest {
        for (entry in Entry.entries) for (failSecond in listOf(false, true)) {
            val fixture = batchTestFixture(true)
            var requests = 0
            batchTestClient(token = { """{"access_token":"access","token_type":"Bearer",
                "authorization_details":[{"type":"openid_credential","credential_configuration_id":"identity",
                "credential_identifiers":["a","b"]}]}""" }, credentialStatus = HttpStatusCode.Accepted,
                credential = { request ->
                    requests++
                    if (failSecond && requests == 2) error("Second credential request failed")
                    val id = request.getValue("credential_identifier").jsonPrimitive.content
                    """{"transaction_id":"tx-$id","interval":5}"""
                }).use { http ->
                val result = assertFailsWith<CredentialReceiveException> { entry.receive(fixture.wallet, http) }.result
                val expected = if (failSecond) listOf("a") else listOf("a", "b")
                assertEquals(expected.map { "tx-$it" }, result.deferredCredentials.map { it.transactionId })
                assertEquals(expected, result.deferredCredentials.map { it.credentialIdentifier })
                assertEquals(2, requests)
                assertTrue(result.credentialIds.isEmpty())
                if (failSecond) assertEquals(CredentialIssuanceStage.REQUEST, assertNotNull(result.failure).stage)
                else assertNull(result.failure)
                val retained = fixture.wallet.issuanceSessions(http).listDeferredCredentials().map { it.id }.toSet()
                assertEquals(retained, result.deferredCredentials.map { it.deferredCredentialId }.toSet())
                assertEquals(expected.size, retained.size)
            }
        }
    }

    private enum class Entry {
        DIRECT, PREVIEW, AUTHORIZED;

        suspend fun receive(wallet: Wallet, http: HttpClient): ReceiveCredentialResult = when (this) {
            DIRECT -> WalletIssuanceHandler.receiveCredential(wallet, ReceiveCredentialRequest(offerJson = batchTestOffer()), httpClient = http)
            PREVIEW -> {
                val preview = WalletIssuanceHandler.previewOffer(wallet, ResolveOfferRequest(offerJson = batchTestOffer()), httpClient = http)
                WalletIssuanceHandler.receiveCredential(wallet, ReceiveCredentialFromPreviewRequest(preview.previewHandle), httpClient = http)
            }
            AUTHORIZED -> WalletIssuanceHandler.receiveCredentialAuthCode(wallet,
                ReceiveAuthorizedCredentialRequest(code = "code", credentialIssuer = BATCH_TEST_ISSUER,
                    credentialEndpoint = Url("$BATCH_TEST_ISSUER/credential"), credentialConfigurationId = "identity"), httpClient = http)
        }
    }
}
