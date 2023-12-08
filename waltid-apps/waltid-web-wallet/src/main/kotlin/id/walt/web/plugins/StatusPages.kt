package id.walt.web.plugins

import id.walt.web.UnauthorizedException
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

    else -> HttpStatusCode.InternalServerError
}

fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<UnauthorizedException> { call, cause ->
            call.respond(HttpStatusCode.Unauthorized, cause.message ?: "")
            cause.printStackTrace()
            //call.respond(HttpStatusCode.Forbidden, cause.message ?: "")
        }
        exception<Throwable> { call, cause ->
            cause.printStackTrace()

            call.respond(
                statusCodeForException(cause), Json.encodeToString(
                    mapOf(
                        "exception" to "true",
                        "status" to statusCodeForException(cause).description,
                        "code" to statusCodeForException(cause).value.toString(),
                        "message" to cause.message
                    )
                )
            )
        }
    }
}
