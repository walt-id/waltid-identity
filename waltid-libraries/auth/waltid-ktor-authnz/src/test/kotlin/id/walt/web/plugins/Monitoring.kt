package id.walt.web.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*

fun Application.configureMonitoring() {
    install(CallLogging) {

    }
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            cause.printStackTrace()
            call.respondText(text = "500: ${cause.stackTraceToString()}", status = HttpStatusCode.InternalServerError)
        }
    }
}
