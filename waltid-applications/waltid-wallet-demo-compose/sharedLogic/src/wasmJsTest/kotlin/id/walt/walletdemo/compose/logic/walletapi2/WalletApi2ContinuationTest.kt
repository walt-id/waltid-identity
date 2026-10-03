package id.walt.walletdemo.compose.logic.walletapi2

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class WalletApi2ContinuationTest {
    @Test
    fun requestsDetailsWhileRemainingCompatibleWithOlderServers() = runTest {
        for (status in listOf(null, "AWAITING_LOCAL_SAVE")) {
            val http = HttpClient(MockEngine { request ->
                assertEquals("/wallet/wallet/credentials/receive/deferred", request.url.encodedPath)
                assertEquals("true", request.url.parameters["includeDetails"])
                val details = status?.let { ",\"status\":\"$it\"" }.orEmpty()
                respond("""[{"id":"retained","credentialConfigurationId":null$details}]""",
                    headers = headersOf(HttpHeaders.ContentType, "application/json"))
            }) {
                install(ContentNegotiation) { json(walletApi2Json) }
            }
            try {
                val client = WalletApi2Client("https://wallet.example", "synthetic-token", http)
                assertEquals(DeferredCredentialHandleDto("retained", status = status), client.listDeferred("wallet").single())
            } finally { http.close() }
        }
    }
}
