package id.walt.commons.testing

import io.ktor.client.HttpClient
import io.ktor.client.engine.java.Java
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.*
import java.net.ServerSocket
import java.util.Collections

/**
 * A test-only proxy around a real issuer. It never creates OAuth errors, grants or proofs.
 * Hiding unsigned discovery exercises response-only negotiation. Poisoning a successful PAR
 * freshness header makes the real token endpoint reject the wallet's remembered challenge.
 * The issuer must advertise [baseUrl], so its issuer identity and proof audiences stay intact.
 */
class AttestationChallengeGateway(
    upstreamBaseUrl: String,
    private val responseOnly: Boolean,
    port: Int = ServerSocket(0).use { it.localPort },
    host: String = "127.0.0.1",
) : AutoCloseable {
    val baseUrl = "http://$host:$port"
    data class Exchange(
        val path: String,
        val form: String,
        val attestation: String?,
        val pop: String?,
        val status: Int,
        val error: String?,
        val challenge: String?,
        val cacheControl: String?,
    )
    private val recorded = Collections.synchronizedList(mutableListOf<Exchange>())
    val exchanges: List<Exchange> get() = synchronized(recorded) { recorded.toList() }
    private val client = HttpClient(Java) {
        followRedirects = false
        expectSuccess = false
        engine { protocolVersion = java.net.http.HttpClient.Version.HTTP_1_1 }
        install(HttpTimeout) { requestTimeoutMillis = 30_000 }
    }
    private val hopHeaders = setOf("host", "content-length", "transfer-encoding", "connection", "accept-encoding", "upgrade", "http2-settings")
    private val server = embeddedServer(CIO, host = "0.0.0.0", port = port) {
        routing {
            route("/{path...}") {
                handle {
                    val requestBody = if (call.request.httpMethod in listOf(HttpMethod.Post, HttpMethod.Put, HttpMethod.Patch)) call.receiveText() else ""
                    val response = client.request(upstreamBaseUrl.trimEnd('/') + call.request.uri) {
                        method = call.request.httpMethod
                        headers {
                            call.request.headers.forEach { name, values ->
                                if (name.lowercase() !in hopHeaders) appendAll(name, values)
                            }
                        }
                        if (requestBody.isNotEmpty()) setBody(requestBody)
                    }
                    var body = response.bodyAsText()
                    val path = call.request.path()
                    val json = runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull()
                    if (responseOnly && path.contains(".well-known/oauth-authorization-server") && json != null) {
                        body = JsonObject(json.filterKeys { it != "challenge_endpoint" }).toString()
                    }
                    val poisonPar = responseOnly && path.endsWith("/par") && response.status == HttpStatusCode.Created
                    response.headers.forEach { name, values ->
                        if (name.lowercase() !in hopHeaders && !name.equals(HttpHeaders.ContentType, true) &&
                            !(poisonPar && name.equals(CHALLENGE_HEADER, true))) {
                            values.forEach { call.response.headers.append(name, it) }
                        }
                    }
                    if (poisonPar) call.response.headers.append(CHALLENGE_HEADER, "invalid-remembered-test-challenge")
                    recorded += Exchange(path, requestBody, call.request.headers["OAuth-Client-Attestation"],
                        call.request.headers["OAuth-Client-Attestation-PoP"], response.status.value,
                        json?.get("error")?.jsonPrimitive?.contentOrNull, response.headers[CHALLENGE_HEADER],
                        response.headers[HttpHeaders.CacheControl])
                    call.respondText(body, response.contentType(), response.status)
                }
            }
        }
    }.start(wait = false)

    override fun close() {
        server.stop(0, 1_000)
        client.close()
    }

    companion object { const val CHALLENGE_HEADER = "OAuth-Client-Attestation-Challenge" }
}
