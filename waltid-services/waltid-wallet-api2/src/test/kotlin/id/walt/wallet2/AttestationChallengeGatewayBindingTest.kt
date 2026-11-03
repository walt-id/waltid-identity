package id.walt.wallet2

import id.walt.commons.testing.AttestationChallengeGateway
import io.ktor.server.engine.EmbeddedServer
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class AttestationChallengeGatewayBindingTest {
    @Test fun bindsToHostUnlessExplicitlyOverridden() = runBlocking {
        for (bind in listOf<String?>(null, "0.0.0.0")) {
            val gateway = if (bind == null) AttestationChallengeGateway("http://127.0.0.1:9", false)
                else AttestationChallengeGateway("http://127.0.0.1:9", false, bindHost = bind)
            try {
                val field = gateway.javaClass.getDeclaredField("server").apply { isAccessible = true }
                val server = field.get(gateway) as EmbeddedServer<*, *>
                assertEquals(bind ?: "127.0.0.1", server.engine.resolvedConnectors().single().host)
                kotlin.test.assertTrue(gateway.baseUrl.startsWith("http://127.0.0.1:"))
            } finally { gateway.close() }
        }
    }
}
