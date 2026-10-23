package id.walt.verifier2

import id.walt.commons.config.ConfigManager
import id.walt.commons.testing.E2ETest
import id.walt.crypto.utils.Base64Utils.encodeToBase64
import id.walt.verifier.openid.models.authorization.ClientMetadata
import id.walt.vical.VicalFetchRequest
import id.walt.vical.VicalFetchResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class VicalFetchModeRouteTest {

    private val payload = byteArrayOf(0x84.toByte(), 0x43, 0xA1.toByte(), 0x01, 0x26)

    @Test
    fun `disabled removes fetch and leaves validate routed`() = withMode("disabled") { http ->
        test("fetch is absent and validate is routed") {
            assertEquals(HttpStatusCode.NotFound, fetch(http, "file:///etc/hostname").status)
            assertNotEquals(HttpStatusCode.NotFound, validate(http).status)
        }
    }

    @Test
    fun `network-only rejects file urls`() = withMode("network-only") { http ->
        test("file url is rejected") {
            val path = Files.createTempFile("vical", ".cbor")
            try {
                assertEquals(HttpStatusCode.BadRequest, fetch(http, path.toUri().toString()).status)
            } finally {
                Files.deleteIfExists(path)
            }
        }
    }

    @Test
    fun `filesystem-only reads a local file and rejects http`() = withMode("filesystem-only") { http ->
        test("local file is returned and http is rejected") {
            val path = Files.createTempFile("vical", ".cbor")
            try {
                Files.write(path, payload)
                val response = fetch(http, path.toUri().toString())
                assertEquals(HttpStatusCode.OK, response.status)
                assertEquals(payload.encodeToBase64(), response.body<VicalFetchResponse>().vicalBase64)
                assertEquals(HttpStatusCode.BadRequest, fetch(http, "http://127.0.0.1:9/vical").status)
            } finally {
                Files.deleteIfExists(path)
            }
        }
    }

    @Test
    fun `both allows a file and still refuses a private network url`() = withMode("both") { http ->
        test("file is allowed and a private address is refused") {
            val path = Files.createTempFile("vical", ".cbor")
            try {
                Files.write(path, payload)
                assertEquals(HttpStatusCode.OK, fetch(http, path.toUri().toString()).status)
                assertEquals(HttpStatusCode.BadRequest, fetch(http, "http://10.1.2.3/vical").status)
            } finally {
                Files.deleteIfExists(path)
            }
        }
    }

    private fun withMode(mode: String, block: suspend E2ETest.(HttpClient) -> Unit) {
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
                        vicalFetchMode = mode,
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
