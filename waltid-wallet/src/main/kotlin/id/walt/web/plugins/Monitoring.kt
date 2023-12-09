package id.walt.web.plugins

import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.util.*
import io.ktor.util.logging.*
import kotlinx.coroutines.runBlocking

internal val LOGGER = KtorSimpleLogger("WebLog")

val RequestTracePlugin = createRouteScopedPlugin("RequestTracePlugin", { }) {

    fun ApplicationCall.toUrlLogString(): String {
        val httpMethod = request.httpMethod.value
        val userAgent = request.headers["User-Agent"]?.take(14)?.split("/")?.first()
        val url = url()

        return "$httpMethod $url ($userAgent)"
    }

    fun Any.toLogString(name: String) = when {
        this == NullBody
                || this.toString().isEmpty() -> "no $name"

        this is Collection<*> && this.isEmpty() -> "no[] $name"
        this is Map<*, *> && this.isEmpty() -> "no{} $name "
        else -> "$name: \"$this\""
    }

    onCall { call ->
        val body = runBlocking { call.receiveText() }
        val cookies = call.request.cookies.rawCookies
        //println(call.request.headers.entries().map { "${it.key} -> ${it.value}" })
        LOGGER.info("REQUEST  -> ${call.toUrlLogString()}: ${body.toLogString("body")}, ${cookies.toLogString("cookies")}")
    }
    this.onCallRespond { call, body ->
        val status = call.response.status()?.toString() ?: "Default"
        val statusNum = call.response.status()?.value ?: "DEF"
        LOGGER.info("RESP $statusNum -> ${call.toUrlLogString()}: ${body.toLogString("body")} ($status)")
    }
}


fun Application.configureMonitoring() {
    install(RequestTracePlugin)
    /*install(CallLogging) {

        level = Level.INFO


        this.format { call ->
            val status = call.response.status()
            val httpMethod = call.request.httpMethod.value
            val userAgent = call.request.headers["User-Agent"]?.take(10)
            val p = call.url()
            val body = runBlocking { call.receiveText() }

            "$status: $httpMethod $p ($userAgent) - $body"
        }
        //filter { call -> call.request.path().startsWith("/") }
        callIdMdc("call-id")
    }
    install(CallId) {
        header(HttpHeaders.XRequestId)
        verify { callId: String ->
            callId.isNotEmpty()
        }
    }*/
}
