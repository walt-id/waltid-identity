package id.walt.walletdemo.compose.logic.walletapi2

import id.walt.walletdemo.compose.logic.WalletDemoCredentialHolders
import id.walt.walletdemo.compose.logic.WalletDemoCredentialSelection
import id.walt.walletdemo.compose.logic.WalletDemoSigningProtection
import id.walt.walletdemo.compose.logic.WalletDemoPresentationPreviewHandle
import id.walt.walletdemo.compose.logic.WalletDemoIssuanceOutcome
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WalletApi2IssuanceOwnershipTest {
    @Test
    fun cancelledAuthorizationCannotBeRestoredByLateResponse() = runTest {
        withFixture {
            val session = start(authorization = true)
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            beforeRequest = { request ->
                if (request.url.encodedPath.endsWith("authorization-url/batch")) {
                    entered.complete(Unit)
                    release.await()
                }
            }
            val pending = async { runCatching { wallet.beginAuthorizationIssuance(session, copies()) } }
            entered.await()
            wallet.cancelIssuance(session)
            release.complete(Unit)
            assertTrue(pending.await().isFailure)
            assertNull(wallet.pendingAuthorizationIssuance())
            assertFailsWith<IllegalStateException> { wallet.beginAuthorizationIssuance(session, copies()) }
            assertTrue(deletedKeys.isEmpty(), "Accepted keys may already be in use and must be retained")
        }
    }

    @Test
    fun concurrentAcceptanceCannotSendTheSameSessionTwice() = runTest {
        withFixture {
            val session = start()
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            beforeRequest = { request ->
                if (request.url.encodedPath.endsWith("/receive")) {
                    entered.complete(Unit)
                    release.await()
                }
            }
            val first = async { wallet.continuePreAuthorizedIssuance(session, null, copies()) }
            entered.await()
            val duplicate = async {
                runCatching { wallet.continuePreAuthorizedIssuance(session, null, copies()) }
            }
            // Let the second operation either reject or reach HTTP, without blocking the first.
            testScheduler.runCurrent()
            try {
                assertTrue(duplicate.isCompleted, "Concurrent acceptance must reject without waiting for HTTP")
            } finally {
                release.complete(Unit)
            }
            assertIs<WalletDemoIssuanceOutcome.Stored>(first.await())
            assertTrue(duplicate.await().isFailure)
            assertEquals(1, receiveBodies.size)
            assertEquals(2, generatedKeys)
        }
    }

    @Test
    fun cancellingKeyPreparationCleansOnlyAcknowledgedKeys() = runTest {
        withFixture {
            val session = start()
            val entered = CompletableDeferred<Unit>()
            beforeRequest = { request ->
                if (request.url.encodedPath.endsWith("dids/create") && generatedKeys == 2) {
                    entered.complete(Unit)
                    CompletableDeferred<Unit>().await()
                }
            }
            val pending = async { wallet.continuePreAuthorizedIssuance(session, null, copies()) }
            entered.await()
            pending.cancelAndJoin()
            assertEquals(listOf("key-1", "key-2"), deletedKeys)
            assertEquals(listOf("did:jwk:key-1"), deletedDids)
            assertTrue(receiveBodies.isEmpty())
            beforeRequest = {}
            assertIs<WalletDemoIssuanceOutcome.Stored>(wallet.continuePreAuthorizedIssuance(session, null, copies()))
            assertEquals(4, generatedKeys, "Preparation cancelled before acceptance can be retried")
        }
    }

    @Test
    fun sessionCancellationDuringPreparationCannotAdoptNewKeys() = runTest {
        withFixture {
            val session = start()
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            beforeRequest = { request ->
                if (request.url.encodedPath.endsWith("dids/create") && generatedKeys == 2) {
                    entered.complete(Unit)
                    release.await()
                }
            }
            val pending = async { runCatching { wallet.continuePreAuthorizedIssuance(session, null, copies()) } }
            entered.await()
            wallet.cancelIssuance(session)
            release.complete(Unit)
            assertTrue(pending.await().isFailure)
            assertEquals(listOf("key-1", "key-2"), deletedKeys)
            assertEquals(listOf("did:jwk:key-1", "did:jwk:key-2"), deletedDids)
            assertTrue(receiveBodies.isEmpty())
        }
    }

    @Test
    fun failedReceiveRetainsSelectionsAndRetryDoesNotAllocateAgain() = runTest {
        withFixture {
            val session = start()
            failReceive = true
            assertFailsWith<WalletApi2Exception> { wallet.continuePreAuthorizedIssuance(session, "bad", copies()) }
            assertFailsWith<IllegalArgumentException> { wallet.continuePreAuthorizedIssuance(session, "ok", copies(1)) }
            failReceive = false
            assertIs<WalletDemoIssuanceOutcome.Stored>(wallet.continuePreAuthorizedIssuance(session, "ok", copies()))
            assertEquals(2, generatedKeys)
            assertEquals(2, receiveBodies.size)
            assertEquals(receiveBodies[0]["credentials"], receiveBodies[1]["credentials"])
            assertTrue(deletedKeys.isEmpty())
        }
    }

    @Test
    fun cleanupContinuesAfterOneDeleteFails() = runTest {
        withFixture {
            val session = start()
            failSecondDid = true
            failDidDeletion = true
            val error = assertFailsWith<WalletApi2Exception> {
                wallet.continuePreAuthorizedIssuance(session, null, copies())
            }
            assertEquals(listOf("key-1", "key-2"), deletedKeys)
            assertEquals(listOf("did:jwk:key-1"), deletedDids)
            assertEquals(1, error.suppressedExceptions.size)
            assertTrue(receiveBodies.isEmpty())
        }
    }

    @Test
    fun olderSessionCompletionAndCancellationPreserveNewerPendingAuthorization() = runTest {
        withFixture {
            val older = start()
            val newer = start(authorization = true)
            wallet.beginAuthorizationIssuance(newer, copies())
            assertIs<WalletDemoIssuanceOutcome.Stored>(wallet.continuePreAuthorizedIssuance(older, null, copies()))
            assertEquals(newer, assertNotNull(wallet.pendingAuthorizationIssuance()).id)
            wallet.cancelIssuance(older)
            assertEquals(newer, assertNotNull(wallet.pendingAuthorizationIssuance()).id)
        }
    }

    @Test
    fun browserRestorationUsesAcceptedBindingsForAuthorizationCallback() = runTest {
        withFixture {
            val session = start(authorization = true)
            wallet.beginAuthorizationIssuance(session, copies())
            val accepted = assertNotNull(WalletApi2BrowserSessionStore.loadPendingIssuance()).credentials
            val restored = newWallet()
            assertEquals(session, assertNotNull(restored.pendingAuthorizationIssuance()).id)
            assertIs<WalletDemoIssuanceOutcome.Stored>(
                restored.continueAuthorizationIssuance(session, "https://wallet.example/callback?code=code&state=state"),
            )
            assertEquals(2, generatedKeys)
            val bindings = receiveBodies.single()["credentials"]!!.jsonArray.single().jsonObject["holderBindings"]!!.jsonArray
            assertEquals(accepted.single().holderBindings.map { it.keyId }, bindings.map { it.jsonObject["keyId"].toString().trim('"') })
            assertNull(WalletApi2BrowserSessionStore.loadPendingIssuance())
            assertTrue(deletedKeys.isEmpty())
        }
    }

    @Test
    fun resetRejectsInFlightResolutionPreparationAndReceive() = runTest {
        for (suffix in listOf("resolve-offer/batch", "dids/create", "/receive")) {
            withFixture {
                val session = if (suffix == "resolve-offer/batch") null else start()
                val entered = CompletableDeferred<Unit>()
                val release = CompletableDeferred<Unit>()
                beforeRequest = { request ->
                    if (request.url.encodedPath.endsWith(suffix)) {
                        entered.complete(Unit)
                        release.await()
                    }
                }
                val pending = async {
                    if (session == null) start() else wallet.continuePreAuthorizedIssuance(session, null, copies())
                }
                entered.await()
                try {
                    assertFailsWith<IllegalStateException> { wallet.deleteWallet() }
                    assertEquals(0, walletDeletes)
                    assertEquals(0, walletCreates)
                } finally {
                    release.complete(Unit)
                }
                pending.await()
                beforeRequest = {}
                wallet.deleteWallet()
                assertEquals(1, walletDeletes)
                assertEquals(1, walletCreates)
                assertEquals(listOf("replacement-1"), publishedWalletIds)
                assertTrue(requests.none { it == "GET /wallet/replacement-1" }, "Reset must leave bootstrap to the controller")
                start()
                assertEquals("POST /wallet/replacement-1/credentials/receive/resolve-offer/batch", requests.last())
                val identity = wallet.bootstrap(WalletDemoSigningProtection.None)
                assertEquals("bootstrap-key", identity.keyId)
                assertEquals("did:jwk:bootstrap", identity.did)
            }
        }
    }

    @Test
    fun admittedResetRejectsNewOperationsAndConcurrentReset() = runTest {
        withFixture {
            val session = start(authorization = true)
            wallet.beginAuthorizationIssuance(session, copies())
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            beforeRequest = { request ->
                if (request.method == HttpMethod.Delete && request.url.encodedPath == "/wallet/wallet") {
                    entered.complete(Unit)
                    release.await()
                }
            }
            val reset = async { wallet.deleteWallet() }
            entered.await()
            val requestCount = requests.size
            val handle = WalletDemoPresentationPreviewHandle("request")
            val operations: List<suspend () -> Unit> = listOf(
                { wallet.bootstrap(WalletDemoSigningProtection.None) },
                { wallet.listCredentials() },
                { wallet.listDeferredIssuance() },
                { start() },
                { wallet.beginAuthorizationIssuance(session, copies()) },
                { wallet.continuePreAuthorizedIssuance(session, null, copies()) },
                { wallet.continueAuthorizationIssuance(session, "https://wallet.example/callback?code=code&state=state") },
                { wallet.resumeDeferredIssuance("deferred") },
                { wallet.present("request", null) },
                { wallet.previewPresentation("request") },
                { wallet.submitPresentation(handle, emptyList(), emptyList(), null) },
                { wallet.rejectPresentation(handle) },
                { wallet.deleteCredential("credential") },
                { wallet.deleteWallet() },
            )
            try {
                assertNull(wallet.pendingAuthorizationIssuance())
                for (operation in operations) assertFailsWith<IllegalStateException> { operation() }
                assertEquals(requestCount, requests.size, "Rejected operations must not reach HTTP")
            } finally {
                release.complete(Unit)
            }
            reset.await()
            assertFailsWith<IllegalStateException> { wallet.beginAuthorizationIssuance(session, copies()) }
        }
    }

    @Test
    fun deletionFailureClosesWalletUntilExplicitResetRetry() = runTest {
        withFixture {
            val session = start(authorization = true)
            wallet.beginAuthorizationIssuance(session, copies())
            failWalletDelete = true
            assertFailsWith<WalletApi2Exception> { wallet.deleteWallet() }
            assertEquals(0, walletCreates)
            assertNull(wallet.pendingAuthorizationIssuance())
            assertFailsWith<IllegalStateException> { start() }
            failWalletDelete = false
            wallet.deleteWallet()
            assertEquals(2, walletDeletes)
            assertEquals(1, walletCreates)
            start()
        }
    }

    @Test
    fun replacementFailureRetriesWithoutDeletingAcknowledgedOldWalletAgain() = runTest {
        withFixture {
            failWalletCreate = true
            assertFailsWith<WalletApi2Exception> { wallet.deleteWallet() }
            assertFailsWith<IllegalStateException> { wallet.listCredentials() }
            failWalletCreate = false
            wallet.deleteWallet()
            assertEquals(1, walletDeletes)
            assertEquals(2, walletCreates)
            assertEquals(listOf("replacement-2"), publishedWalletIds)
            start()
        }
    }

    @Test
    fun publicationFailureRetriesOnlyTheKnownReplacementId() = runTest {
        withFixture {
            failPublication = true
            assertFailsWith<IllegalStateException> { wallet.deleteWallet() }
            assertFailsWith<IllegalStateException> { start() }
            failPublication = false
            wallet.deleteWallet()
            assertEquals(1, walletDeletes)
            assertEquals(1, walletCreates)
            assertEquals(listOf("replacement-1", "replacement-1"), publishedWalletIds)
            start()
        }
    }

    @Test
    fun cancellationDuringResetLeavesOperationsClosedUntilRetry() = runTest {
        withFixture {
            val entered = CompletableDeferred<Unit>()
            beforeRequest = { request ->
                if (request.method == HttpMethod.Delete) {
                    entered.complete(Unit)
                    CompletableDeferred<Unit>().await()
                }
            }
            val reset = async { wallet.deleteWallet() }
            entered.await()
            reset.cancelAndJoin()
            assertFailsWith<IllegalStateException> { start() }
            beforeRequest = {}
            wallet.deleteWallet()
            assertEquals(1, walletDeletes)
            assertEquals(1, walletCreates)
            start()
        }
    }

    private fun copies(count: Int = 2) = listOf(
        WalletDemoCredentialSelection("pid", WalletDemoCredentialHolders.NewKeys(count)),
    )

    private suspend fun withFixture(block: suspend Fixture.() -> Unit) {
        WalletApi2BrowserSessionStore.clearPendingIssuance()
        val fixture = Fixture()
        try {
            fixture.block()
        } finally {
            fixture.http.close()
            WalletApi2BrowserSessionStore.clearPendingIssuance()
        }
    }

    private class Fixture {
        val requests = mutableListOf<String>()
        val publishedWalletIds = mutableListOf<String>()
        var walletDeletes = 0
        var walletCreates = 0
        var failWalletDelete = false
        var failWalletCreate = false
        var failPublication = false
        var authorizationOffer = false
        var generatedKeys = 0
        val deletedKeys = mutableListOf<String>()
        val deletedDids = mutableListOf<String>()
        val receiveBodies = mutableListOf<JsonObject>()
        var beforeRequest: suspend (HttpRequestData) -> Unit = {}
        var failReceive = false
        var failSecondDid = false
        var failDidDeletion = false
        val http = HttpClient(MockEngine { request ->
            requests += "${request.method.value} ${request.url.encodedPath}"
            beforeRequest(request)
            var status = HttpStatusCode.OK
            val path = request.url.encodedPath
            val body = when {
                request.method == HttpMethod.Delete && path.count { it == '/' } == 2 -> {
                    walletDeletes++
                    status = if (failWalletDelete) HttpStatusCode.ServiceUnavailable else HttpStatusCode.NoContent
                    ""
                }
                request.method == HttpMethod.Post && path == "/wallet" -> {
                    walletCreates++
                    status = if (failWalletCreate) HttpStatusCode.ServiceUnavailable else HttpStatusCode.Created
                    """{"walletId":"replacement-$walletCreates"}"""
                }
                request.method == HttpMethod.Get && path.count { it == '/' } == 2 ->
                    """{"walletId":"${path.substringAfterLast('/')}","defaultKeyId":"bootstrap-key","defaultDidId":"did:jwk:bootstrap"}"""
                request.method == HttpMethod.Get && path.endsWith("/dids") -> """[{"did":"did:jwk:bootstrap"}]"""
                request.method == HttpMethod.Put && path.endsWith("set-default") -> { status = HttpStatusCode.NoContent; "" }
                request.method == HttpMethod.Get && path.endsWith("/credentials") -> "[]"
                path.endsWith("resolve-offer/batch") -> """{"offer":{"credentialIssuer":"https://issuer.example","credentialEndpoint":"https://issuer.example/credential","preAuthorizedCode":${if (authorizationOffer) "null" else "\"pre-authorized-code\""},"issuer":{"credentialIssuer":"https://issuer.example"},"offeredCredentials":[{"configurationId":"pid","format":"mso_mdoc"}]},"batchSize":2}"""
                path.endsWith("keys/generate") -> {
                    generatedKeys++
                    status = HttpStatusCode.Created
                    """{"keyId":"key-$generatedKeys"}"""
                }
                path.endsWith("dids/create") -> {
                    status = if (failSecondDid && generatedKeys == 2) HttpStatusCode.BadRequest else HttpStatusCode.Created
                    """{"did":"did:jwk:key-$generatedKeys"}"""
                }
                request.method == HttpMethod.Delete && "/dids/" in path -> {
                    deletedDids += path.substringAfter("/dids/")
                    status = if (failDidDeletion) HttpStatusCode.ServiceUnavailable else HttpStatusCode.NoContent
                    ""
                }
                request.method == HttpMethod.Delete && "/keys/" in path -> {
                    deletedKeys += path.substringAfter("/keys/")
                    status = HttpStatusCode.NoContent
                    ""
                }
                path.endsWith("authorization-url/batch") -> """{"authorizationUrl":"https://issuer.example/authorize","state":"state","codeVerifier":"verifier","credentialConfigurationIds":["pid"],"credentialIssuerBaseUrl":"https://issuer.example"}"""
                path.endsWith("/receive") || path.endsWith("authorized/batch") -> {
                    receiveBodies += walletApi2Json.parseToJsonElement((request.body as TextContent).text).jsonObject
                    if (failReceive) status = HttpStatusCode.BadRequest
                    """{"credentialIds":["credential-1","credential-2"]}"""
                }
                else -> error("Unexpected request: ${request.method} $path")
            }
            respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
        }) {
            install(ContentNegotiation) { json(walletApi2Json) }
        }
        private val client = WalletApi2Client("https://wallet-api.example", "token", http)
        val wallet = newWallet()
        fun newWallet() = WalletApi2DemoWallet(client, "wallet", "https://wallet.example/callback") { id ->
            publishedWalletIds += id
            check(!failPublication) { "Browser storage unavailable" }
        }
        suspend fun start(authorization: Boolean = false): String {
            authorizationOffer = authorization
            return wallet.startIssuance("openid-credential-offer://offer", "https://wallet.example/callback", "did:example:holder").id
        }
    }
}
