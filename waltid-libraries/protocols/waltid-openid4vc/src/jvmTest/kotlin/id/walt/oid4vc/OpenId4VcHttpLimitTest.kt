package id.walt.oid4vc

import com.sun.net.httpserver.HttpServer
import id.walt.webdatafetching.ResponseBodyTooLargeException
import id.walt.webdatafetching.WebDataFetchingConfiguration
import kotlinx.coroutines.test.runTest
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URLEncoder
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFails
import kotlin.test.assertTrue

class OpenId4VcHttpLimitTest {

    @BeforeTest
    fun allowLoopbackForLocalServers() {
        System.setProperty("walt.ssrf.allowLoopback", "true")
    }

    @AfterTest
    fun clearLoopbackProperty() {
        System.clearProperty("walt.ssrf.allowLoopback")
    }

    @Test
    fun `an oversized credential offer uri fails before the body is parsed`() = runTest {
        val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        server.createContext("/offer") { exchange ->
            exchange.sendResponseHeaders(200, WebDataFetchingConfiguration.DEFAULT_MAX_RESPONSE_BYTES + 1)
            exchange.close()
        }
        server.start()
        try {
            val offerUrl = "http://127.0.0.1:${server.address.port}/offer"
            val requestUrl = "openid-credential-offer://?credential_offer_uri=${URLEncoder.encode(offerUrl, Charsets.UTF_8)}"
            val error = assertFails {
                OpenID4VCI.parseAndResolveCredentialOfferRequestUrl(requestUrl)
            }
            val causes = generateSequence(error) { it.cause }.toList()
            assertTrue(causes.any { it is ResponseBodyTooLargeException }, causes.joinToString { it::class.simpleName.orEmpty() })
            assertTrue(causes.none { it is OpenID4VCI.CouldNotParseCredentialOfferException })
        } finally {
            server.stop(0)
        }
    }
}
