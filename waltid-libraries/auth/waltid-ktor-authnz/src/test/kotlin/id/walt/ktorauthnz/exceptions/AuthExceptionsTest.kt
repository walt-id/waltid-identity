package id.walt.ktorauthnz.exceptions

import id.walt.errors.StatusException
import kotlin.test.Test
import kotlin.test.assertEquals

class AuthExceptionsTest {

    @Test
    fun `authentication failures carry the status the services answer them with`() {
        val statuses: Map<StatusException, Int> = mapOf(
            InvalidCredentialsException() to 401,
            ExpiredTokenException("expired") to 401,
            JWTVerificationException() to 401,
            RadiusAuthException() to 406,
            OTPAuthException() to 401,
            Web3AuthException("bad signature") to 401,
            InvalidChallengeException() to 401,
            AccountDataNotFoundException("userpass") to 404,
        )
        statuses.forEach { (exception, status) -> assertEquals(status, exception.status, exception::class.simpleName) }
        assertEquals("expired", ExpiredTokenException("expired").message)
    }
}
