package id.walt.commons.web.plugins

import id.walt.commons.web.WebException
import io.klogging.logger
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import redis.clients.jedis.exceptions.JedisException
import kotlin.reflect.jvm.jvmName


private val logger = logger("Web exception")

fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<WebException> { call, cause ->
            logger.error(cause)
            call.respond(cause.status, exceptionMap(cause, cause.status))
        }
        exception<Throwable> { call, cause ->
            logger.error(cause)

            val status = statusCodeForException(cause)
            call.respond(status, exceptionMap(cause, status))
        }
    }
}

private fun statusCodeForException(cause: Throwable) = when (cause) {
    is NotFoundException -> HttpStatusCode.NotFound
    is IllegalArgumentException -> HttpStatusCode.BadRequest
    is BadRequestException -> HttpStatusCode.BadRequest
    is IllegalStateException -> HttpStatusCode.InternalServerError
    is JedisException -> HttpStatusCode.InternalServerError
    is WebException -> cause.status
    else -> HttpStatusCode.InternalServerError
}

fun exceptionMap(cause: Throwable, status: HttpStatusCode) = mutableMapOf(
    "exception" to JsonPrimitive(true),
    "id" to JsonPrimitive(cause::class.simpleName ?: cause::class.jvmName),
    "status" to JsonPrimitive(status.description),
    "code" to JsonPrimitive(status.value.toString()),
    "message" to JsonPrimitive(cause.message)
).apply {
    if (cause.cause != null && logger.isTraceEnabled()) put("cause", JsonPrimitive(cause.cause!!.message))
}
