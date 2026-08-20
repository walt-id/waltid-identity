package id.walt.openid4vp.conformance.adapter

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Exposes an OpenID4VP authorization endpoint that hands requests to Wallet2.
 *
 * In a wallet conformance run the suite acts as the verifier and redirects to the wallet's
 * `authorization_endpoint`; this adapter is that endpoint. It deliberately does nothing but forward:
 * resolving the request, fetching `request_uri`, authenticating the client identifier, matching
 * credentials, building the VP token and posting it to `response_uri` are all the wallet's job and
 * are exactly what is under test. Doing any of that here would test the adapter instead of Wallet2.
 */
class VpWalletConformanceAdapter(
    private val walletApiUrl: String,
    private val adapterPort: Int,
    private val walletId: String,
) {
    private var server: EmbeddedServer<*, *>? = null

    val authorizationEndpoint: String get() = "http://127.0.0.1:$adapterPort$AUTHORIZE_PATH"

    fun start(httpClient: HttpClient) {
        println("[Adapter] Starting on port $adapterPort -> wallet $walletApiUrl (wallet $walletId)")
        server = embeddedServer(CIO, port = adapterPort) {
            routing {
                get(AUTHORIZE_PATH) { present(call, httpClient) }
                post(AUTHORIZE_PATH) { present(call, httpClient) }
            }
        }.start(wait = false)
        println("[Adapter] Authorization endpoint: $authorizationEndpoint")
    }

    fun stop() {
        server?.stop(gracePeriodMillis = 500, timeoutMillis = 2000)
        server = null
    }

    /**
     * Hand the incoming authorization request to the wallet as an `openid4vp://` URL.
     *
     * The wallet's single-call presentation endpoint runs the whole flow and reports whether the
     * response reached the verifier, so the adapter only has to relay the outcome.
     */
    private suspend fun present(call: ApplicationCall, httpClient: HttpClient) {
        val query = call.request.queryString()
        if (query.isBlank()) {
            call.respondText("No authorization request parameters received", status = HttpStatusCode.BadRequest)
            return
        }

        val requestUrl = "$OPENID4VP_SCHEME?$query"
        println("[Adapter] Presenting request: ${requestUrl.take(160)}")

        val response = httpClient.post("$walletApiUrl/wallet/$walletId/credentials/present") {
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject { put("requestUrl", requestUrl) })
        }

        if (!response.status.isSuccess()) {
            val error = response.bodyAsText()
            println("[Adapter] Wallet refused the request: ${response.status} $error")
            // A wallet that rejects a malformed request is the expected outcome of the suite's
            // negative modules, so surface it rather than turning it into an adapter failure.
            call.respondText(error, ContentType.Application.Json, HttpStatusCode.BadRequest)
            return
        }

        val result = response.body<JsonObject>()
        println("[Adapter] Wallet presented: transmission_success=${result["transmission_success"]}")
        // Serialised by hand: this server installs no ContentNegotiation, so responding with an
        // object would fail negotiation with 406 and the caller would lose the wallet's result.
        call.respondText(result.toString(), ContentType.Application.Json, HttpStatusCode.OK)
    }

    companion object {
        private const val AUTHORIZE_PATH = "/openid4vp/authorize"

        /** Canonical form a wallet receives an authorization request in. */
        private const val OPENID4VP_SCHEME = "openid4vp://authorize"
    }
}
