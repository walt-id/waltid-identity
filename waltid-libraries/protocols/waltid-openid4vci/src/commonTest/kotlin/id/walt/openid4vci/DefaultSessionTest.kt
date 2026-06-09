package id.walt.openid4vci

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

class DefaultSessionTest {

    @Test
    fun `custom attributes survive session copy and mutations`() {
        val expiresAt = Clock.System.now() + 5.minutes
        val session = DefaultSession(subject = "subject")
            .withCustomAttribute("issuance_session_id", "session-123")
            .withExpiresAt(TokenType.ACCESS_TOKEN, expiresAt)
            .withSubject("updated-subject")
            .copy()

        assertEquals("updated-subject", session.subject)
        assertEquals(expiresAt, session.expiresAt[TokenType.ACCESS_TOKEN])
        assertEquals("session-123", session.customAttributes["issuance_session_id"])
    }
}
