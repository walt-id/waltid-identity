package id.walt.ktorauthnz.valkey

import id.walt.ktorauthnz.sessions.ValkeySessionStore
import id.walt.ktorauthnz.tokens.ktorauthnztoken.ValkeyAuthnzTokenStore
import kotlinx.coroutines.test.runTest
import java.net.ServerSocket
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ValkeyConnectionTest {

    /** A local port nothing listens on. */
    private val closedPort = ServerSocket(0).use { it.localPort }

    @Test
    fun `each store reports an unreachable valkey under its own name`() = runTest {
        val session = assertFailsWith<IllegalArgumentException> {
            ValkeySessionStore(null, "127.0.0.1", closedPort, null, null).tryConnect()
        }
        assertTrue(session.message!!.startsWith("Could not connect to valkey session store"), session.message)

        val token = assertFailsWith<IllegalArgumentException> {
            ValkeyAuthnzTokenStore(null, "127.0.0.1", closedPort, null, null).tryConnect()
        }
        assertTrue(token.message!!.startsWith("Could not connect to valkey token store"), token.message)
    }
}
