package id.walt.commons.web.plugins

import id.walt.commons.web.ConflictException
import id.walt.commons.web.DuplicateTargetException
import id.walt.commons.web.WebException
import id.walt.errors.StatusException
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals

class StatusExceptionStatusPagesTest {

    @Test
    fun `a library status exception is answered with its own status`() {
        assertEquals(HttpStatusCode.NotFound, statusCodeForException(StatusException(404, "not here")))
        assertEquals(HttpStatusCode.Conflict, statusCodeForException(object : StatusException(409, "taken") {}))
    }

    @Test
    fun `web exceptions are answered with their own status`() {
        assertEquals(HttpStatusCode.PaymentRequired, statusCodeForException(WebException(402, "pay")))
        assertEquals(HttpStatusCode.Conflict, statusCodeForException(ConflictException("taken")))
        assertEquals(HttpStatusCode.Conflict, statusCodeForException(DuplicateTargetException(target = "a.b")))
    }

    @Test
    fun `a status error that is also an illegal argument keeps its own status`() {
        class NotFoundArgument : IllegalArgumentException("no such entry"), id.walt.errors.HttpStatusError {
            override val status = 404
        }
        assertEquals(HttpStatusCode.NotFound, statusCodeForException(NotFoundArgument()))
    }
}
