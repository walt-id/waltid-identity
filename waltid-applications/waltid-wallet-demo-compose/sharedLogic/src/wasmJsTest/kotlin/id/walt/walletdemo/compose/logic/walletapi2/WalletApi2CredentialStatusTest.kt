package id.walt.walletdemo.compose.logic.walletapi2

import id.walt.walletdemo.compose.logic.WalletDemoPresentationCredentialSelection
import id.walt.walletdemo.compose.logic.WalletDemoPresentationPreviewHandle
import id.walt.walletdemo.compose.logic.WalletDemoStatusReviewChanged
import id.walt.walletdemo.compose.logic.WalletDemoOperationResult
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.content.TextContent
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertIs

class WalletApi2CredentialStatusTest {
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun anEarlierPollCannotOverwriteTheResultOfAnExplicitRefresh() = runTest {
        val context = """{"id":"active-context","expiresAt":"2026-11-12T10:05:00Z"}"""
        val unchecked = presentation("NOT_CHECKED", "NONE", "unchecked")
        val invalid = presentation("INVALID", "INVALID", "invalid")
        val suspended = presentation("SUSPENDED", "SUSPENDED", "suspended")
        var current = unchecked
        var checks = 0
        var holdPoll = false
        val pollStarted = CompletableDeferred<Unit>()
        val releasePoll = CompletableDeferred<Unit>()
        val http = HttpClient(MockEngine(MockEngineConfig().apply {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler { request ->
                val body = when {
                    request.url.encodedPath.endsWith("/context") -> snapshot(unchecked, context)
                    request.url.encodedPath.endsWith("/refresh") -> {
                        checks++
                        current = if (checks == 1) invalid else suspended
                        """[{"reference":{"storeIndex":0,"credentialId":"identity"},"outcome":"COMPLETED","presentation":$current,"evaluatedAt":"2026-11-12T10:00:00Z","hasCheckErrors":false,"statusContext":$context}]"""
                    }
                    else -> {
                        val captured = current
                        if (holdPoll) { holdPoll = false; pollStarted.complete(Unit); releasePoll.await() }
                        snapshot(captured, context)
                    }
                }
                respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        })) { install(ContentNegotiation) { json(walletApi2Json) } }
        val statuses = WalletApi2CredentialStatuses(WalletApi2Client("https://wallet.example", "token", http = http), "wallet")
        try {
            assertEquals(1, statuses.refresh(null, true).completed)
            assertEquals("INVALID", statuses.snapshot().credentials.getValue("identity").issue)
            holdPoll = true
            val earlierPoll = async { statuses.snapshot() }
            pollStarted.await()
            val refresh = async { statuses.refresh(setOf("identity"), true) }
            runCurrent()
            assertEquals(false, refresh.isCompleted, "The earlier HTTP response must settle before this refresh")
            releasePoll.complete(Unit)
            assertEquals("INVALID", earlierPoll.await().credentials.getValue("identity").issue)
            assertEquals(1, refresh.await().completed)
            assertEquals("SUSPENDED", statuses.snapshot().credentials.getValue("identity").issue)
            assertEquals(2, checks)
        } finally { http.close() }
    }

    @Test
    fun immediateAdverseResultPollingAndBothSubmissionRequestsShareTheActiveContext() = runTest {
        for (kind in WalletApiKind.entries) assertActiveContext(kind)
    }

    private suspend fun assertActiveContext(kind: WalletApiKind) {
        var localReads = 0
        var refreshes = 0
        val reviewedPaths = mutableListOf<String>()
        val context = """{"id":"active-context","expiresAt":"2026-11-12T10:05:00Z"}"""
        val unchecked = presentation("NOT_CHECKED", "NONE", "unchecked")
        val adverse = presentation("INVALID", "INVALID", "adverse")
        val http = HttpClient(MockEngine { request ->
            val path = request.url.encodedPath
            val body = when {
                path.endsWith("/credential-status/context") -> snapshot(unchecked, context)
                path.endsWith("/credential-status/refresh") -> {
                    refreshes++
                    assertEquals("active-context", walletApi2Json.parseToJsonElement((request.body as TextContent).text).jsonObject["statusContext"]?.jsonPrimitive?.content)
                    """[{"reference":{"storeIndex":0,"credentialId":"identity"},"outcome":"COMPLETED","status":{"future-protocol":"opaque"},"presentation":$adverse,"evaluatedAt":"2026-11-12T10:00:00Z","hasCheckErrors":false,"statusContext":$context}]"""
                }
                path.endsWith("/credential-status") -> {
                    localReads++
                    if (request.url.parameters["statusContext"] == null) snapshot(unchecked)
                    else { assertEquals("active-context", request.url.parameters["statusContext"]); snapshot(adverse, context) }
                }
                path.endsWith("/status-reviewed") -> {
                    reviewedPaths += path
                    val submitted = walletApi2Json.parseToJsonElement((request.body as TextContent).text).jsonObject
                    assertEquals("active-context", submitted["statusContext"]?.jsonPrimitive?.content)
                    assertEquals("adverse", submitted.getValue("reviewedStatus").jsonObject["identity"]?.jsonPrimitive?.content)
                    if (path.contains("/build-vp-token/")) """{"vpToken":"signed-presentation"}""" else """{"transmission_success":true}"""
                }
                else -> error("Unexpected request ${request.method} $path")
            }
            respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }) { install(ContentNegotiation) { json(walletApi2Json) } }
        val wallet = WalletApi2DemoWallet(WalletApi2Client("https://wallet.example", "token", kind, http), kind,
            "wallet", "https://wallet.example/callback") {}
        try {
            assertEquals("NOT_CHECKED", wallet.credentialStatusSnapshot().credentials.getValue("identity").kind)
            val summary = wallet.refreshCredentialStatuses(setOf("identity"), true)
            assertEquals(1, summary.completed)
            assertEquals(0, summary.checkErrors)
            val immediate = wallet.credentialStatusSnapshot().credentials.getValue("identity")
            assertEquals("INVALID", immediate.issue)
            assertEquals(1, localReads, "The immediate result must not depend on another HTTP read")
            assertEquals("INVALID", wallet.credentialStatusSnapshot().credentials.getValue("identity").issue)
            val submitted = wallet.submitStatusReviewedPresentation(WalletDemoPresentationPreviewHandle("https://verifier.example/request"),
                listOf(WalletDemoPresentationCredentialSelection("query", "identity")), emptyList(), mapOf("identity" to immediate.advisoryRevision))
            assertIs<WalletDemoOperationResult.Success>(submitted)
            assertEquals(1, refreshes, "Polling and submission may not trigger issuer checks")
            assertEquals(listOf("build-vp-token/status-reviewed", "send-response/status-reviewed"),
                reviewedPaths.map { it.substringAfterLast("/present/") })
        } finally { http.close() }
    }

    @Test
    fun contextExpiryInvalidatesSubmissionAndRecoversWithOnlyAColdLocalRead() = runTest {
        for (recovery in ExpiryRecovery.entries) assertExpiredContext(recovery)
    }

    private enum class ExpiryRecovery { SUBMISSION, POLLING, MANUAL_REFRESH, RENDERED_COLD_REVIEW }

    private suspend fun assertExpiredContext(recovery: ExpiryRecovery) {
        var expired = false
        var localReads = 0
        var refreshes = 0
        val context = """{"id":"active-context","expiresAt":"2026-11-12T10:05:00Z"}"""
        val unchecked = presentation("NOT_CHECKED", "NONE", "unchecked")
        val http = HttpClient(MockEngine { request ->
            val path = request.url.encodedPath
            val isExpiredRead = expired && path.endsWith("/credential-status") && request.url.parameters["statusContext"] != null
            val body = when {
                isExpiredRead -> """{"code":"credential_status_review_changed"}"""
                path.endsWith("/credential-status/context") -> { expired = false; snapshot(unchecked, context) }
                path.endsWith("/credential-status/refresh") -> { refreshes++; "[]" }
                path.endsWith("/credential-status") -> {
                    localReads++
                    snapshot(unchecked, context.takeIf { request.url.parameters["statusContext"] != null })
                }
                else -> error("Expired review must not reach ${request.method} $path")
            }
            respond(body, if (isExpiredRead) HttpStatusCode.Conflict else HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }) { install(ContentNegotiation) { json(walletApi2Json) } }
        val statuses = WalletApi2CredentialStatuses(WalletApi2Client("https://wallet.example", "token", http = http), "wallet")
        try {
            assertEquals(0, statuses.refresh(null, true).completed)
            val rendered = statuses.snapshot()
            assertEquals("NOT_CHECKED", rendered.credentials.getValue("identity").kind)
            expired = true
            val pollBeforeSubmission = recovery != ExpiryRecovery.SUBMISSION
            if (pollBeforeSubmission) {
                val recovered = statuses.snapshot()
                assertEquals(rendered.credentials, recovered.credentials, "This expiry does not change issuer facts or advisory revisions")
                assertTrue(recovered.reviewEpoch > rendered.reviewEpoch, "The UI must invalidate pending confirmation even when issuer facts are identical")
            }
            if (recovery == ExpiryRecovery.RENDERED_COLD_REVIEW) {
                assertFailsWith<WalletDemoStatusReviewChanged> { statuses.submissionSnapshot(rendered.reviewEpoch) }
                assertEquals(1, localReads, "An old receipt cannot consume or resume the newly rendered review")
                val recovered = statuses.snapshot()
                assertNull(statuses.submissionSnapshot(recovered.reviewEpoch)?.statusContext,
                    "Fresh consent may use cold facts immediately without an extra failed attempt")
                assertEquals(1, refreshes, "Recovery and deliberate cold consent must not check issuers")
            } else if (recovery == ExpiryRecovery.MANUAL_REFRESH) {
                assertEquals(0, statuses.refresh(null, true).completed)
                assertEquals("NOT_CHECKED", statuses.snapshot().credentials.getValue("identity").kind)
                assertEquals("active-context", statuses.submissionSnapshot()?.statusContext?.id,
                    "A new explicit check and rendered result must not require an extra failed submit")
                assertEquals(2, refreshes)
            } else {
                assertFailsWith<WalletDemoStatusReviewChanged> { statuses.submissionSnapshot() }
                assertEquals(if (pollBeforeSubmission) 1 else 0, localReads, "No automatic submission or recovery read at a consent boundary")
                assertEquals("NOT_CHECKED", statuses.snapshot().credentials.getValue("identity").kind)
                assertEquals(if (pollBeforeSubmission) 2 else 1, localReads)
                assertEquals(1, refreshes)
                assertEquals("NOT_CHECKED", statuses.submissionSnapshot()?.credentials?.single()?.presentation?.kind,
                    "A later deliberate attempt may use the newly read review")
            }
        } finally { http.close() }
    }

    @Test
    fun onlyAnInitiallyUnsupportedWalletMayUseLegacyCapability() = runTest {
        for (unsupported in listOf(HttpStatusCode.NotFound, HttpStatusCode.MethodNotAllowed)) {
            var calls = 0
            val http = HttpClient(MockEngine {
                calls++
                respond(if (calls == 2) emptySnapshot else "Unavailable",
                    if (calls == 2) HttpStatusCode.OK else unsupported,
                    headersOf(HttpHeaders.ContentType, "application/json"))
            }) { install(ContentNegotiation) { json(walletApi2Json) } }
            val client = WalletApi2Client("https://wallet.example", "synthetic-token", http = http)
            try {
                assertNull(client.credentialStatusSnapshot("wallet"))
                assertNotNull(client.credentialStatusSnapshot("wallet"))
                assertEquals(unsupported, assertFailsWith<WalletApi2Exception> {
                    client.credentialStatusSnapshot("wallet")
                }.status)
                assertNull(client.credentialStatusSnapshot("another-wallet"), "Capability is scoped to the wallet")
            } finally { http.close() }
        }
    }

    @Test
    fun malformedSuccessfulStatusCannotDowngradeToLegacyOnTheNextRead() = runTest {
        for (openContext in listOf(false, true)) {
            var calls = 0
            val http = HttpClient(MockEngine {
                calls++
                respond(if (calls == 1) "{broken}" else "Unavailable",
                    if (calls == 1) HttpStatusCode.OK else HttpStatusCode.NotFound,
                    headersOf(HttpHeaders.ContentType, "application/json"))
            }) { install(ContentNegotiation) { json(walletApi2Json) } }
            val client = WalletApi2Client("https://wallet.example", "synthetic-token", http = http)
            try {
                assertFailsWith<Exception> {
                    if (openContext) client.createCredentialStatusContext("wallet") else client.credentialStatusSnapshot("wallet")
                    Unit
                }
                assertEquals(HttpStatusCode.NotFound, assertFailsWith<WalletApi2Exception> {
                    client.credentialStatusSnapshot("wallet")
                }.status)
            } finally { http.close() }
        }
    }

    private val emptySnapshot = """{"evaluatedAt":"2026-11-12T10:00:00Z","credentials":[],"refreshing":[]}"""

    private fun presentation(kind: String, issue: String, revision: String) =
        """{"kind":"$kind","issue":"$issue","freshness":"UNKNOWN","action":"CHECK","advisoryRevision":"$revision","lastKnown":false}"""

    private fun snapshot(presentation: String, context: String? = null) =
        """{"credentials":[{"reference":{"storeIndex":0,"credentialId":"identity"},"presentation":$presentation}],"statusContext":${context ?: "null"}}"""
}
