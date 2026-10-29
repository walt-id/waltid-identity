package id.walt.webwallet.utils

import com.sun.net.httpserver.HttpServer
import id.walt.webdatafetching.ResponseBodyTooLargeException
import id.walt.webdatafetching.WebDataFetchingConfiguration
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.test.runTest
import java.net.InetAddress
import java.net.InetSocketAddress
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFails
import kotlin.test.assertTrue

class WalletHttpClientsLimitTest {

    @BeforeTest
    fun allowLoopbackForLocalServers() {
        System.setProperty("walt.ssrf.allowLoopback", "true")
    }

    @AfterTest
    fun clearLoopbackProperty() {
        System.clearProperty("walt.ssrf.allowLoopback")
    }

    @Test
    fun `an oversized response is rejected`() = runTest {
        val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        server.createContext("/big") { exchange ->
            exchange.sendResponseHeaders(200, WebDataFetchingConfiguration.DEFAULT_MAX_RESPONSE_BYTES + 1)
            exchange.close()
        }
        server.start()
        val client = WalletHttpClients.getHttpClient()
        try {
            val error = assertFails {
                client.get("http://127.0.0.1:${server.address.port}/big").bodyAsText()
            }
            assertTrue(generateSequence(error) { it.cause }.any { it is ResponseBodyTooLargeException })
        } finally {
            server.stop(0)
            client.close()
        }
    }
}
