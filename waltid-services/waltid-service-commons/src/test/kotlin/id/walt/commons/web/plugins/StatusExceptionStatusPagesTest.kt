package id.walt.commons.web.plugins

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
}
