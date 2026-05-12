package id.waltid.openid4vp.wallet

import com.sun.net.httpserver.HttpServer
import id.walt.verifier.openid.models.authorization.AuthorizationRequest
import id.walt.verifier.openid.models.openid.OpenID4VPResponseMode
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@OptIn(ExperimentalSerializationApi::class)
class WalletRejectDirectPostHandlingJvmTest {

    @Test
    fun directPost_sendsErrorResponseToResponseUri() = runTest {
        withRecordingServer { responseUri, receivedBody ->
            val result = WalletPresentFunctionality2.walletRejectHandling(
                authorizationRequest = AuthorizationRequest(
                    responseUri = responseUri,
                    responseMode = OpenID4VPResponseMode.DIRECT_POST,
                    state = "state-123",
                ),
                error = WalletPresentFunctionality2.Oid4vpErrorCode.access_denied,
                errorDescription = "User denied",
            ).getOrThrow()

            assertEquals(true, result.transmissionSuccess)
            assertEquals("acknowledged", result.verifierResponse?.jsonObjectValue("status"))
            assertEquals("error=access_denied&error_description=User+denied&state=state-123", assertNotNull(receivedBody.get()))
        }
    }

    @Test
    fun directPostJwt_sendsPermittedUnencryptedErrorResponseToResponseUri() = runTest {
        withRecordingServer { responseUri, receivedBody ->
            val result = WalletPresentFunctionality2.walletRejectHandling(
                authorizationRequest = AuthorizationRequest(
                    responseUri = responseUri,
                    responseMode = OpenID4VPResponseMode.DIRECT_POST_JWT,
                    state = "state-456",
                ),
                error = "access_denied",
            ).getOrThrow()

            assertEquals(true, result.transmissionSuccess)
            assertEquals("acknowledged", result.verifierResponse?.jsonObjectValue("status"))
            assertEquals("error=access_denied&state=state-456", assertNotNull(receivedBody.get()))
        }
    }

    private suspend fun withRecordingServer(
        block: suspend (responseUri: String, receivedBody: AtomicReference<String?>) -> Unit,
    ) {
        val receivedBody = AtomicReference<String?>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/response") { exchange ->
            receivedBody.set(exchange.requestBody.readBytes().decodeToString())
            val response = """{"status":"acknowledged"}""".toByteArray(StandardCharsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, response.size.toLong())
            exchange.responseBody.use { it.write(response) }
        }

        server.start()
        try {
            block("http://127.0.0.1:${server.address.port}/response", receivedBody)
        } finally {
            server.stop(0)
        }
    }

    private fun JsonElement.jsonObjectValue(name: String): String? =
        jsonObject[name]?.jsonPrimitive?.content
}
