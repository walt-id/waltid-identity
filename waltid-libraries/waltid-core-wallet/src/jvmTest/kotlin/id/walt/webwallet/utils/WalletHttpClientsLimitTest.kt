package id.walt.webwallet.utils

import com.sun.net.httpserver.HttpServer
import id.walt.webdatafetching.ResponseBodyTooLargeException
import id.walt.webdatafetching.WebDataFetcher
import id.walt.webdatafetching.WebDataFetcherId
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

    private var previousClientFactory = WalletHttpClients.defaultMethod

    @BeforeTest
    fun useProductionClient() {
        System.setProperty("walt.ssrf.allowLoopback", "true")
        previousClientFactory = WalletHttpClients.defaultMethod
        WalletHttpClients.defaultMethod = {
            WebDataFetcher(WebDataFetcherId.CORE_WALLET_HTTP_CLIENT, WebDataFetchingConfiguration())
        }
    }

    @AfterTest
    fun restoreClientFactory() {
        WalletHttpClients.defaultMethod = previousClientFactory
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
            assertTrue(
                generateSequence(error) { it.cause }.any { it is ResponseBodyTooLargeException },
                error.stackTraceToString(),
            )
        } finally {
            server.stop(0)
            client.close()
        }
    }
}
