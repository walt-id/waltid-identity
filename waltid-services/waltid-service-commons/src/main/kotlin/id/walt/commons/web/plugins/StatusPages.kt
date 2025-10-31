package id.walt.commons.web.plugins

import id.walt.commons.web.AuthException
import id.walt.commons.web.SerializableWebException
import id.walt.commons.web.WebException
import io.klogging.logger
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import kotlinx.serialization.json.*
import redis.clients.jedis.exceptions.JedisException
import kotlin.reflect.jvm.jvmName


private val logger = logger("Web exception")

fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<WebException> { call, cause ->
            logger.error(cause)
            val status = HttpStatusCode.fromValue(cause.status)
            call.respond(status, exceptionMap(cause, status))
        }
        exception<Throwable> { call, cause ->
            logger.error(cause)
            val status = statusCodeForException(cause)
            call.respond(status, exceptionMap(cause, status))
        }
        exception<AuthException> { call, cause ->
            logger.error(cause)
            val status = HttpStatusCode.fromValue(cause.status.value)
            call.respond(status, exceptionMap(cause, status))
        }



    }
}

private fun statusCodeForException(cause: Throwable): HttpStatusCode = when (cause) {
    is NotFoundException -> HttpStatusCode.NotFound
    is IllegalArgumentException -> HttpStatusCode.BadRequest
    is BadRequestException -> HttpStatusCode.BadRequest
    is IllegalStateException -> HttpStatusCode.InternalServerError
    is JedisException -> HttpStatusCode.InternalServerError
    is SerializableWebException -> HttpStatusCode.fromValue(cause.status)
    is WebException -> HttpStatusCode.fromValue(cause.status)
    else -> HttpStatusCode.InternalServerError
}

fun exceptionMap(cause: Throwable, status: HttpStatusCode): JsonObject =
    if (cause is SerializableWebException) {
        Json.encodeToJsonElement(cause).jsonObject
    } else {
        JsonObject(
            mutableMapOf(
                "exception" to JsonPrimitive(true),
                "id" to JsonPrimitive(cause::class.simpleName ?: cause::class.jvmName),
                "status" to JsonPrimitive(status.description),
                "code" to JsonPrimitive(status.value.toString()),
                "message" to JsonPrimitive(cause.message)
            ).apply {
                var underlyingCause = cause.cause
                var errorCounter = 1

                while (underlyingCause != null) {
                    put(
                        "cause${errorCounter}_exception",
                        JsonPrimitive(underlyingCause::class.simpleName ?: underlyingCause::class.jvmName)
                    )
                    if (cause.cause != null && logger.isTraceEnabled()) {
                        put("cause${errorCounter}_message", JsonPrimitive(underlyingCause.message))
                    }
                    underlyingCause = underlyingCause.cause
                    errorCounter++
                }
            })
    }
