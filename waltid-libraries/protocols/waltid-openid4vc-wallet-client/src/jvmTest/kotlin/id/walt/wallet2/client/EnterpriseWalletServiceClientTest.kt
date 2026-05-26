package id.walt.wallet2.client

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EnterpriseWalletServiceClientTest {

    @Test
    fun receivePreAuthorizedSendsEnterpriseHeadersAndPayload() = runTest {
        var captured: HttpRequestData? = null
        val client = EnterpriseWalletServiceClient(
            environment = WalletClientEnvironment(
                enterpriseBaseUrl = "https://enterprise.example",
                enterpriseHostHeader = "tenant.enterprise.localhost",
                bearerToken = "token-123",
                walletPath = "org.tenant.wallet",
            ),
            httpClient = HttpClient(MockEngine) {
                install(ContentNegotiation) {
                    json(Json { ignoreUnknownKeys = true })
                }
                engine {
                    addHandler { request ->
                        captured = request
                        respond(
                            content = """[{"id":"credential-1"}]""",
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                        )
                    }
                }
            },
        )

        client.receivePreAuthorized(
            offerUrl = "openid-credential-offer://offer",
            keyReference = "org.tenant.kms.wallet_key",
        )

        val request = checkNotNull(captured)
        assertEquals(
            "https://enterprise.example/v2/org.tenant.wallet/wallet-service-api/credentials/receive/pre-authorized",
            request.url.toString(),
        )
        assertEquals("tenant.enterprise.localhost", request.headers[HttpHeaders.Host])
        assertEquals("Bearer token-123", request.headers[HttpHeaders.Authorization])
        assertTrue((request.body as TextContent).text.contains(""""useClientAttestation":true"""))
    }
}
