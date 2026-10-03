package id.walt.walletdemo.compose.logic.walletapi2

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertFailsWith

class WalletApi2SessionExpiryTest {
    @Test fun oldSessionCallbacksCannotChangeTheNewAccount() {
        val old = WalletApi2Session("http://localhost:7006", "old-test-token", "old-wallet", "old@example.test")
        val current = WalletApi2Session("http://localhost:7006", "new-test-token", "new-wallet", "new@example.test")
        try {
            WalletApi2BrowserSessionStore.save(old)
            WalletApi2BrowserSessionStore.save(current)
            WalletApi2BrowserSessionStore.updateWalletIdIfCurrent(old, "late-old-wallet")
            WalletApi2BrowserSessionStore.clearIfCurrent(old)
            assertEquals("new-wallet", WalletApi2BrowserSessionStore.load()?.walletId)
            assertEquals(current.email, WalletApi2BrowserSessionStore.load()?.email)
            WalletApi2BrowserSessionStore.updateWalletIdIfCurrent(current, "replacement-wallet")
            assertEquals("replacement-wallet", WalletApi2BrowserSessionStore.load()?.walletId)
            WalletApi2BrowserSessionStore.clearIfCurrent(current)
            assertNull(WalletApi2BrowserSessionStore.load())
        } finally { WalletApi2BrowserSessionStore.clear() }
    }

    @Test fun accountErrorsExplainRecoveryWithoutTechnicalEnvelopes() {
        assertEquals("Invalid email or password.", authErrorMessage(HttpStatusCode.Unauthorized, "{broken}", false))
        assertEquals("An account with this email already exists. Sign in instead.", authErrorMessage(HttpStatusCode.Conflict, "", true))
        val body = """{"message":"Password must contain at least eight characters."}"""
        assertEquals("Password must contain at least eight characters.", authErrorMessage(HttpStatusCode.BadRequest, body, true))
        assertEquals("Password must contain at least eight characters.", authErrorMessage(HttpStatusCode.BadRequest, kotlinx.serialization.json.JsonPrimitive(body).toString(), true))
        assertEquals("Unable to sign in. Try again.", authErrorMessage(HttpStatusCode.InternalServerError, "<html>upstream error</html>", false))
    }

    @Test
    fun unauthorizedResponsesExpireOnceAcrossAllRequestPaths() = runTest {
        val paths = listOf<suspend (WalletApi2Client) -> Unit>(
            { it.listWallets() },
            { it.deleteCredential("wallet", "credential") },
            { it.receivePreAuthorized("wallet", "offer", null, null, "https://wallet.example", emptyList()) },
        )
        for (request in paths) {
            var expired = 0
            val http = HttpClient(MockEngine { respond("Session expired", HttpStatusCode.Unauthorized) }) {
                install(ContentNegotiation) { json(walletApi2Json) }
            }
            val client = WalletApi2Client("https://wallet.example", "synthetic-token", http) { expired++ }
            try {
                repeat(2) { assertFailsWith<WalletApi2Exception> { request(client) } }
                assertEquals(1, expired, "Each HTTP path expires the session exactly once")
            } finally { http.close() }
        }
    }

    @Test
    fun forbiddenAccessDoesNotExpireAValidSession() = runTest {
        var expired = 0
        val http = HttpClient(MockEngine { respond("Forbidden", HttpStatusCode.Forbidden) })
        val client = WalletApi2Client("https://wallet.example", "synthetic-token", http) { expired++ }
        try {
            val error = assertFailsWith<WalletApi2Exception> { client.listWallets() }
            assertEquals(HttpStatusCode.Forbidden, error.status)
            assertEquals(0, expired)
        } finally { http.close() }
    }
}
