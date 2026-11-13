package id.walt.walletdemo.compose.logic.walletapi2

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class WalletApi2CredentialStatusTest {
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
        var calls = 0
        val http = HttpClient(MockEngine {
            calls++
            respond(if (calls == 1) "{broken}" else "Unavailable",
                if (calls == 1) HttpStatusCode.OK else HttpStatusCode.NotFound,
                headersOf(HttpHeaders.ContentType, "application/json"))
        }) { install(ContentNegotiation) { json(walletApi2Json) } }
        val client = WalletApi2Client("https://wallet.example", "synthetic-token", http = http)
        try {
            assertFailsWith<Exception> { client.credentialStatusSnapshot("wallet") }
            assertEquals(HttpStatusCode.NotFound, assertFailsWith<WalletApi2Exception> {
                client.credentialStatusSnapshot("wallet")
            }.status)
        } finally { http.close() }
    }

    private val emptySnapshot = """{"evaluatedAt":"2026-11-12T10:00:00Z","credentials":[],"refreshing":[]}"""
}
