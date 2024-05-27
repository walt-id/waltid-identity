package id.walt.webwallet.web.plugins

import id.walt.webwallet.web.WebException
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json


private fun statusCodeForException(cause: Throwable) = when (cause) {
    is IllegalArgumentException -> HttpStatusCode.BadRequest
    is NotFoundException -> HttpStatusCode.NotFound
    is WebException -> cause.status

    else -> HttpStatusCode.InternalServerError
}

fun Application.configureStatusPages() {
    install(StatusPages) {

        fun exceptionString(cause: Throwable) = Json.encodeToString(
            mapOf(
                "exception" to "true",
                "status" to statusCodeForException(cause).description,
                "code" to statusCodeForException(cause).value.toString(),
                "message" to cause.message
            )
        )

        exception<WebException> { call, cause ->
            cause.printStackTrace()
            call.respond(cause.status, exceptionString(cause))
        }
        exception<Throwable> { call, cause ->
            cause.printStackTrace()

            call.respond(
                statusCodeForException(cause), exceptionString(cause)
            )
        }
    }
}
