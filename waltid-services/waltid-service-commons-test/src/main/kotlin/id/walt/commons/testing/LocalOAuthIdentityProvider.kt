package id.walt.commons.testing

import io.ktor.http.*
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.net.ServerSocket
import java.util.Base64

/** Deterministic external login for issuer tests; authorization codes and PKCE stay with issuer2. */
class LocalOAuthIdentityProvider : AutoCloseable {
    private val port = ServerSocket(0).use { it.localPort }
    val baseUrl = "http://127.0.0.1:$port"
    private val server = embeddedServer(CIO, host = "127.0.0.1", port = port) {
        routing {
            get("/authorize") {
                call.respondRedirect(URLBuilder(requireNotNull(call.parameters["redirect_uri"])).apply {
                    parameters.append("code", "local-login")
                    parameters.append("state", requireNotNull(call.parameters["state"]))
                }.buildString())
            }
            post("/token") {
                fun encoded(value: String) = Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray())
                val idToken = "${encoded("""{"alg":"none"}""")}.${encoded("""{"sub":"challenge-holder"}""")}."
                call.respondText("""{"access_token":"local-idp-token","token_type":"Bearer","id_token":"$idToken"}""",
                    ContentType.Application.Json)
            }
        }
    }.start(wait = false)
    override fun close() { server.stop(0, 1_000) }
}
