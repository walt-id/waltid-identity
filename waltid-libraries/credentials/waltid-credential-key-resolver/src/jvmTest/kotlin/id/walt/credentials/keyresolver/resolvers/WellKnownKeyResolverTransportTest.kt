package id.walt.credentials.keyresolver.resolvers

import com.sun.net.httpserver.HttpServer
import id.walt.webdatafetching.WebDataFetcherManager
import id.walt.webdatafetching.config.HttpEngine
import id.walt.webdatafetching.ssrf.PrivateNetworkGuardSettings
import io.ktor.http.Url
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class WellKnownKeyResolverTransportTest {
    @Test
    fun `an HTTPS issuer delegates only to HTTPS key sets`() {
        val issuer = Url("https://issuer.example")
        val keySet = Url("https://keys.example/jwks")
        assertEquals(keySet, keySetRetrievalUrl(issuer, keySet.toString()))
        assertFailsWith<IllegalArgumentException> {
            keySetRetrievalUrl(issuer, "http://keys.example/jwks")
        }
    }

    @Test
    fun `issuer metadata resolves a remote JWKS under the Enterprise engine configuration`() = runTest {
        val allowLoopback = PrivateNetworkGuardSettings.allowLoopback.takeIf { PrivateNetworkGuardSettings.allowLoopbackExplicit }
        val global = WebDataFetcherManager.globalDefaultConfiguration
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val issuer = "http://127.0.0.1:${server.address.port}"
        val key = buildJsonObject {
            put("kid", "fixture-key")
            put("kty", "OKP")
            put("crv", "Ed25519")
            put("x", "qBDsYw3k62mUT8UmEx99Xz3yckiSRmTsL6aa21ZcAVM")
        }
        val selector = buildJsonObject { put("kid", "fixture-key") }
        val status = AtomicInteger(503)
        val paths = CopyOnWriteArrayList<String>()
        server.createContext("/") { exchange ->
            val path = exchange.requestURI.path
            paths.add(path)
            val (code, json) = when (path) {
                "/.well-known/jwt-vc-issuer" -> 200 to """{"jwks_uri":"$issuer/keys?token=fixture"}"""
                "/keys" -> status.get() to """{"keys":[$key]}"""
                else -> 404 to "{}"
            }
            val body = json.encodeToByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(code, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        try {
            WebDataFetcherManager.globalDefaultConfiguration = global.copy(http = HttpEngine.CIO)
            PrivateNetworkGuardSettings.allowLoopback = true
            server.start()
            val failure = assertFailsWith<IllegalArgumentException> {
                WellKnownKeyResolver.resolveJwkFromWellKnown(issuer, selector)
            }
            assertEquals("Could not retrieve issuer key document", failure.message)
            status.set(200)
            val result = WellKnownKeyResolver.resolveJwkFromWellKnown(issuer, selector)
            assertEquals(key, Json.parseToJsonElement(result.data.toByteArray().decodeToString()))
            assertEquals(listOf("/.well-known/jwt-vc-issuer", "/keys", "/.well-known/jwt-vc-issuer", "/keys"), paths)
        } finally {
            server.stop(0)
            WebDataFetcherManager.globalDefaultConfiguration = global
            if (allowLoopback == null) PrivateNetworkGuardSettings.clearAllowLoopbackChoice()
            else PrivateNetworkGuardSettings.allowLoopback = allowLoopback
        }
    }
}
