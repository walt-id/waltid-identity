package id.walt.commons.web

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.httpVersion
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.sse.SSEServerContent
import io.ktor.server.sse.ServerSSESession
import io.ktor.server.sse.ServerSSESessionWithSerialization
import io.ktor.util.reflect.TypeInfo

/**
 * Opens a server-sent event stream the way Ktor's `sse { }` route does, for a plain `get` handler that has already
 * looked up what the stream is about.
 *
 * Ktor's `sse { }` block runs after the 200 event-stream response has started, so an unknown session or other
 * error raised inside it can only end the stream empty: the client never sees the 404. Validate first, while an
 * exception still becomes its HTTP status, then call this.
 */
suspend fun ApplicationCall.respondEventStream(handler: suspend ServerSSESession.() -> Unit) {
    eventStreamHeaders()
    respond(SSEServerContent(this, handler))
}

/** [respondEventStream] for a stream that sends typed values through [serialize]. */
suspend fun ApplicationCall.respondEventStream(
    serialize: (TypeInfo, Any) -> String,
    handler: suspend ServerSSESessionWithSerialization.() -> Unit,
) {
    eventStreamHeaders()
    respond(SSEServerContent(this, { (this as ServerSSESessionWithSerialization).handler() }, serialize))
}

// The headers Ktor's sse() route sets.
private fun ApplicationCall.eventStreamHeaders() {
    response.header(HttpHeaders.ContentType, ContentType.Text.EventStream.toString())
    response.header(HttpHeaders.CacheControl, "no-store")
    if (request.httpVersion.startsWith("HTTP/1.")) response.header(HttpHeaders.Connection, "keep-alive")
    response.header("X-Accel-Buffering", "no")
}
