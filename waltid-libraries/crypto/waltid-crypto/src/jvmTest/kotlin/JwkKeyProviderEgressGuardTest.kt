import com.sun.net.httpserver.HttpServer
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.keys.jwk.JwkKeyProvider
import id.walt.webdatafetching.WebDataFetcherId
import id.walt.webdatafetching.WebDataFetcherManager
import id.walt.webdatafetching.WebDataFetchingConfiguration
import id.walt.webdatafetching.config.AllowList
import id.walt.webdatafetching.config.url.UrlConfiguration
import id.walt.webdatafetching.ssrf.BlockedAddressException
import id.walt.webdatafetching.ssrf.PrivateNetworkGuardSettings
import kotlinx.coroutines.test.runTest
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A `jwks_uri` often comes from untrusted metadata, so the default client must refuse internal addresses. */
class JwkKeyProviderEgressGuardTest {
    private lateinit var server: HttpServer
    private val hits = AtomicInteger()
    private lateinit var kid: String
    private val jwksUri get() = "http://127.0.0.1:${server.address.port}/jwks"

    @BeforeTest
    fun start() = runTest {
        val key = JWKKey.generate(KeyType.Ed25519)
        val publicJwk = key.getPublicKey().exportJWK()
        kid = JWKKey.importJWK(publicJwk).getOrThrow().getKeyId()
        val body = """{"keys":[$publicJwk]}""".toByteArray()
        server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0).apply {
            createContext("/jwks") { exchange ->
                hits.incrementAndGet()
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            start()
        }
    }

    @AfterTest
    fun stop() {
        server.stop(0)
        PrivateNetworkGuardSettings.clearAllowLoopbackChoice()
        WebDataFetcherManager.appendConfiguration(WebDataFetcherId.JWKS_KEY_PROVIDER.name, WebDataFetchingConfiguration())
    }

    private suspend fun assertBlocked(uri: String) {
        val error = runCatching { JwkKeyProvider(uri).getKey(kid) }.exceptionOrNull()
        assertTrue(
            generateSequence(error) { it.cause }.any { it is BlockedAddressException },
            "expected a blocked-address failure, got: $error",
        )
    }

    @Test
    fun `fetches the JWKS when loopback is allowed`() = runTest {
        PrivateNetworkGuardSettings.allowLoopback = true
        val key = JwkKeyProvider(jwksUri).getKey(kid).getOrThrow()
        assertEquals(kid, key.getKeyId())
        assertEquals(1, hits.get())
    }

    @Test
    fun `refuses a loopback JWKS endpoint without requesting it`() = runTest {
        PrivateNetworkGuardSettings.allowLoopback = false
        assertBlocked(jwksUri)
        assertEquals(0, hits.get(), "the guard must refuse before the request reaches the server")
    }

    @Test
    fun `refuses the cloud metadata address even when loopback is allowed`() = runTest {
        PrivateNetworkGuardSettings.allowLoopback = true
        assertBlocked("http://169.254.169.254/latest/meta-data/jwks")
    }

    @Test
    fun `honours the URL allow-list configured for the JWKS fetcher`() = runTest {
        PrivateNetworkGuardSettings.allowLoopback = true
        WebDataFetcherManager.appendConfiguration(
            WebDataFetcherId.JWKS_KEY_PROVIDER.name,
            WebDataFetchingConfiguration(url = UrlConfiguration(hosts = AllowList(blacklist = listOf("127.0.0.1")))),
        )
        val error = runCatching { JwkKeyProvider(jwksUri).getKey(kid) }.exceptionOrNull()
        assertTrue(
            generateSequence(error) { it.cause }.any { it is IllegalArgumentException && it.message == "Host is not allowed: 127.0.0.1" },
            "expected the allow-list to refuse the host, got: $error",
        )
        assertEquals(0, hits.get(), "a refused host must not be requested")
    }
}
