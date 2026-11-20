package id.walt.commons.web.plugins

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * [clientVisibleMessage] must disclose exactly what [exceptionMap] returns to a client: code that records
 * an exception where clients can read it (an event log) must not reveal more than the HTTP response does.
 */
class ClientVisibleMessageTest {

    @Test
    fun `a client error keeps its message`() {
        assertEquals("id_token is required", clientVisibleMessage(IllegalArgumentException("id_token is required")))
    }

    @Test
    fun `a server-side failure has no visible message`() {
        assertNull(clientVisibleMessage(IllegalStateException("mongo://secret-host:27017 timeout")))
        assertNull(clientVisibleMessage(RuntimeException("mongo://secret-host:27017 timeout")))
    }
}
