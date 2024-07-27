package id.walt.commons.web.plugins

import id.walt.commons.web.WebException
import io.klogging.logger
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import kotlinx.serialization.json.JsonPrimitive


private fun statusCodeForException(cause: Throwable) = when (cause) {
    is NotFoundException -> HttpStatusCode.NotFound
    is IllegalArgumentException -> HttpStatusCode.BadRequest
    is BadRequestException -> HttpStatusCode.BadRequest
    is IllegalStateException -> HttpStatusCode.InternalServerError
    is WebException -> cause.status

    else -> HttpStatusCode.InternalServerError
}

private val logger = logger("Web exception")

fun Application.configureStatusPages() {
    install(StatusPages) {

        fun exceptionMap(cause: Throwable) =
            mutableMapOf(
                "exception" to JsonPrimitive(true),
                "status" to JsonPrimitive(statusCodeForException(cause).description),
                "code" to JsonPrimitive(statusCodeForException(cause).value.toString()),
                "message" to JsonPrimitive(cause.message)
            ).apply {
                if (cause.cause != null && logger.isTraceEnabled()) put("cause", JsonPrimitive(cause.cause!!.message))
            }


        exception<WebException> { call, cause ->
            logger.error(cause)
            call.respond(cause.status, exceptionMap(cause))
        }
        exception<Throwable> { call, cause ->
            logger.error(cause)

            call.respond(statusCodeForException(cause), exceptionMap(cause))
        }
    }
}
