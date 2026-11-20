package id.walt.commons.web.plugins

import id.walt.commons.web.SerializableWebException
import id.walt.crypto.exceptions.KeyAlreadyExistsException
import id.walt.crypto.exceptions.KeySerializationException
import id.walt.errors.HttpStatusError
import io.klogging.logger
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import kotlinx.serialization.json.*
import redis.clients.jedis.exceptions.JedisException
import java.util.UUID
import kotlin.reflect.jvm.jvmName


private val logger = logger("Web exception")

fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            val errorId = UUID.randomUUID().toString()
            logger.error(cause) { "Unhandled exception, errorId=$errorId" }
            val status = statusCodeForException(cause)
            call.response.header("X-Error-Id", errorId)
            call.respond(status, exceptionMap(cause, status , errorId))
        }
    }
}

internal fun statusCodeForException(cause: Throwable): HttpStatusCode = when (cause) {
    // Before the type rules below: a status error may also be, e.g., an IllegalArgumentException.
    is HttpStatusError -> HttpStatusCode.fromValue(cause.status)
    is KeyAlreadyExistsException -> HttpStatusCode.Conflict
    is KeySerializationException -> HttpStatusCode.InternalServerError
    is NotFoundException -> HttpStatusCode.NotFound
    is IllegalArgumentException -> HttpStatusCode.BadRequest
    is BadRequestException -> HttpStatusCode.BadRequest
    // Ktor raises this when a request body cannot be deserialized into the expected type. It extends
    // IOException rather than BadRequestException, so without this branch a malformed body reaches the
    // catch-all below and is reported as 500, blaming the server for the caller's payload.
    is ContentTransformationException -> HttpStatusCode.BadRequest
    is IllegalStateException -> HttpStatusCode.InternalServerError
    is JedisException -> HttpStatusCode.InternalServerError
    else -> HttpStatusCode.InternalServerError
}

/**
 * The message [exceptionMap] shows a client for [cause], or null when it hides it because the failure is
 * server-side. For code that records an exception somewhere clients can read it, such as an event log,
 * so it discloses no more there than the HTTP response does.
 */
fun clientVisibleMessage(cause: Throwable): String? = when {
    cause is SerializableWebException -> cause.message
    statusCodeForException(cause).value >= 500 -> null
    else -> humanizeRequestBodyError(cause)?.message ?: cause.message
}

fun exceptionMap(
    cause: Throwable,
    status: HttpStatusCode,
    errorId: String = UUID.randomUUID().toString(),
): JsonObject =
    if (cause is SerializableWebException) {
        Json.encodeToJsonElement(cause).jsonObject
    } else if (status.value >= 500) {
        // Server-side failure: the message is internal by definition. Details are only in the log, under errorId.
        buildJsonObject {
            put("exception", true)
            put("status", status.description)
            put("code", status.value.toString())
            put("message", "Internal server error")
            put("errorId", errorId)
        }
    } else {
        // Client error: the message describes the caller's input, so it is safe to return. The cause chain is not.
        val bodyError = humanizeRequestBodyError(cause)
        buildJsonObject {
            put("exception", true)
            put("id", cause::class.simpleName ?: cause::class.jvmName)
            put("status", status.description)
            put("code", status.value.toString())
            put("message", bodyError?.message ?: cause.message)
            put("errorId", errorId)
            if (bodyError != null && bodyError.details.isNotEmpty()) {
                put("details", bodyError.detailsJsonObject())
            }
        }
    }