package id.walt.verifier2

import id.walt.commons.config.ConfigManager
import id.walt.commons.testing.E2ETest
import id.walt.verifier.openid.models.authorization.ClientMetadata
import id.walt.vical.VicalFetchRequest
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class VicalFetchModeRouteTest {

    @Test
    fun `disabled removes fetch and leaves validate routed`() = withSettings(enabled = false) { http ->
        test("fetch is absent and validate is routed") {
            assertEquals(HttpStatusCode.NotFound, fetch(http, "https://example.com/vical").status)
            assertNotEquals(HttpStatusCode.NotFound, validate(http).status)
        }
    }

    @Test
    fun `enabled fetch rejects a file url`() = withSettings(enabled = true) { http ->
        test("file url is rejected") {
            val path = Files.createTempFile("vical", ".cbor")
            try {
                val response = fetch(http, path.toUri().toString())
                assertEquals(HttpStatusCode.BadRequest, response.status)
                assertTrue(response.bodyAsText().contains("only http and https"))
            } finally {
                Files.deleteIfExists(path)
            }
        }
    }

    @Test
    fun `private network opt-in is applied on the fetch route`() = withSettings(enabled = true, allowPrivate = true) { http ->
        test("a private literal is not refused as a blocked destination") {
            val response = fetch(http, "http://10.1.2.3/vical")
            assertTrue(!response.bodyAsText().contains("destination not allowed"))
        }
    }

    @Test
    fun `private networks stay refused when the opt-in is off`() = withSettings(enabled = true, allowPrivate = false) { http ->
        test("a private literal is refused") {
            val response = fetch(http, "http://10.1.2.3/vical")
            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertTrue(response.bodyAsText().contains("destination not allowed"))
        }
    }

    private fun withSettings(
        enabled: Boolean,
        allowPrivate: Boolean = false,
        block: suspend E2ETest.(HttpClient) -> Unit,
    ) {
        val host = "127.0.0.1"
        val port = freePort()
        E2ETest(host, port, true).testBlock(
            features = listOf(OSSVerifier2FeatureCatalog),
            preload = {
                ConfigManager.preloadConfig(
                    "verifier-service",
                    OSSVerifier2ServiceConfig(
                        clientId = null,
                        clientMetadata = ClientMetadata(clientName = "Verifier2"),
                        urlPrefix = "http://$host:$port/verification-session",
                        urlHost = "openid4vp://authorize",
                        vicalFetchEnabled = enabled,
                        allowPrivateNetworkVical = allowPrivate,
                    )
                )
            },
            init = {},
            module = Application::verifierModule,
        ) {
            block(testHttpClient())
        }
    }

    private suspend fun fetch(http: HttpClient, url: String): HttpResponse =
        http.post("/vical/fetch") { setBody(VicalFetchRequest(url)) }

    private suspend fun validate(http: HttpClient): HttpResponse =
        http.post("/vical/validate") { setBody("{}") }
}
