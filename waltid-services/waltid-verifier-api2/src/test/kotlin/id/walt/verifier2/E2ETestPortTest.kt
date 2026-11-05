package id.walt.verifier2

import id.walt.commons.testing.E2ETest
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import java.net.ServerSocket
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class E2ETestPortTest {
    @Test
    fun `clients use the port owned by the running server`() {
        ServerSocket(0).use { occupied ->
            E2ETest(host = "127.0.0.1", port = 0, failEarly = true).testBlock(
                features = emptyList(),
                init = {},
                module = { routing { get("/port-probe") { call.respondText("ready") } } },
            ) {
                test("The kernel-selected listener is used by both URL and client") {
                    val actualPort = URI(getBaseURL()).port
                    assertTrue(actualPort > 0)
                    assertNotEquals(occupied.localPort, actualPort)
                    testHttpClient().use { http ->
                        assertEquals("ready", http.get("/port-probe").bodyAsText())
                        assertEquals("ready", http.get("${getBaseURL()}/port-probe").bodyAsText())
                    }
                }
            }
        }
    }
}
