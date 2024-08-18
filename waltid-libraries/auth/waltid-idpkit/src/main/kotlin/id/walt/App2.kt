@file:OptIn(ExperimentalStdlibApi::class)

package id.walt

import io.klogging.Level
import io.klogging.config.loggingConfiguration
import io.klogging.rendering.RENDER_ANSI
import io.klogging.sending.STDERR
import io.klogging.sending.STDOUT
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.doublereceive.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.util.*
import io.ktor.util.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlin.uuid.Uuid

fun main() {
    loggingConfiguration(true) {
        sink("stdout", RENDER_ANSI, STDOUT)
        sink("stderr", RENDER_ANSI, STDERR)

        logging {
            fromLoggerBase("io.ktor", stopOnMatch = true)
            fromMinLevel(Level.INFO) {
                toSink("stdout")
            }
        }
        logging {
            fromLoggerBase("org.sqlite.core.NativeDB", stopOnMatch = true)
            fromMinLevel(Level.DEBUG) {
                toSink("stdout")
            }
        }
        logging {
            fromMinLevel(Level.ERROR) {
                toSink("stderr")
            }
            inLevelRange(Level.TRACE, Level.WARN) {
                toSink("stdout")
            }
        }
        minDirectLogLevel(Level.TRACE)
    }

    embeddedServer(CIO, port = 8080) {
        install(ContentNegotiation) {
            json()
        }

        install(DoubleReceive)

        install(CallLogging) {
            level = org.slf4j.event.Level.DEBUG
            format { call ->
                """
                  -v-v-v-
                  > Request:
                  ${call.request.httpMethod.value} ${call.request.uri}
                  Headers: ${call.request.headers.toMap().dropCommonHeaders()}
                  ${if ((call.request.contentLength() ?: 0) > 0) "Body: ${runBlocking { call.receiveText() }}" else "No body"}
                  
                  > Response:
                  ${call.response.status()} (${call.response.responseType?.type?.simpleName})
                  -^-^-^-
                """.trimIndent()
            }
        }

        install(StatusPages) {
            exception<Throwable> { call, cause ->
                cause.printStackTrace()
                call.respond(HttpStatusCode.InternalServerError, cause.localizedMessage)
            }
        }

        test()
    }.start(wait = true)
}

private fun Map<String, List<String>>.dropCommonHeaders(): Map<String, List<String>> =
    this.toMutableMap().apply {
        keys.removeIf { it.startsWith("Sec-") || it.startsWith("Upgrade-") || it.startsWith("Accept") }
        keys.removeAll(
            listOf(
                "Connection",
                "Cookie",
                "DNT",
                "User-Agent", "Priority"
            )
        )
    }

@Serializable
data class AuthorizeRequest(
    /** CSRF protection */
    val state: String? = null,
    /** server-side relay protection */
    val nonce: String? = null,
    /** scope */
    //val scope: List<String>,
    val scope: String,
    /** OIDC Provider will redirect here */
    @SerialName("redirect_uri")
    val redirectUri: String,
    @SerialName("response_type")
    val responseType: String = "code",
    /** Relying Party identifier */
    @SerialName("client_id")
    val clientId: String,
)

@Serializable
data class TokenResponse(
    @SerialName("id_token")
    val idToken: String,
    @SerialName("access_token")
    val accessToken: String,
    @SerialName("token_type")
    val tokenType: String,
    @SerialName("expires_in")
    val expiresIn: Int,
)

fun Application.test() {
    routing {
        // Step 0. (call by RP)
        get(".well-known/openid-configuration") {
            val issuer = "http://localhost:8080"

            val wellKnown = mapOf(
                "issuer" to issuer,
                "authorization_endpoint" to "$issuer/authorize",
                "token_endpoint" to "$issuer/token",
                "userinfo_endpoint" to "$issuer/userinfo",
                "jwks_uri" to "$issuer/jwks",
                "response_types_supported" to listOf("code", "id_token", "token id_token"),
                "subject_types_supported" to listOf("public"),
                "id_token_signing_alg_values_supported" to listOf("RS256"),
                "scopes_supported" to listOf("openid", "profile", "email"),
                //"token_endpoint_auth_methods_supported" to listOf("client_secret_basic", "client_secret_post"),
                "token_endpoint_auth_methods_supported" to listOf("client_secret_basic"),
                "claims_supported" to listOf("sub", "iss", "aud", "iat", "exp", "name", "email")
            ).toJsonObject()
            call.respond(wellKnown)
        }

        // (RP 302 redirects user agent to /authorize with state, nonce, scope, redirect_uri, response_type, client_id)

        val authCache = HashMap<String, AuthorizeRequest>() // state -> req
        val reqCache = HashMap<String, String>()
        val urlCache = HashMap<String, String>()

        // Step 1.
        get("/authorize") {
            val query = call.request.queryParameters
            var queryString = query.formUrlEncode()

            val map = query.toMap().mapValues { it.value.joinToString(" ") }
            var req = Json.decodeFromJsonElement<AuthorizeRequest>(Json.encodeToJsonElement(map))

            if (req.state == null) {
                // Generate state
                req = req.copy(state = Uuid.random().toString())
                queryString += "&state=${req.state}"
            }

            authCache[req.state!!] = req
            println("Saved req to authCache: $req")


            val (url, token) = Verifier.verify("http://localhost:8080/login?state=${req.state!!}")
            reqCache[req.state!!] = token
            urlCache[req.state!!] = url


            val walletUrl = "http://localhost:7101/api/siop/initiatePresentation?" + url.substringAfter("?")
            println("Wallet url: $walletUrl")

            //language=HTML
            call.respondText(
                """
                    <html><body>
                    <p>Present your credential: <code>$url</code></p>
                    <div id="qrcode"></div>
                    <p>
                    <a href="/login?state=${req.state!!}"><button>Click here when presented</button></a> (just imagine real hard that this is automatic)
                    </p>
                    <p>
                    <a href="$walletUrl"><button>Present with web wallet</button></a>
                    </p>
                    <p>Debug query: <code>$queryString</code></p>
                    
                    
                    <script src='https://cdn.jsdelivr.net/gh/davidshimjs/qrcodejs@gh-pages/qrcode.min.js'></script>
                    <script>
                        new QRCode(document.getElementById("qrcode"), "$url");
                    </script>
                </body></html>
                """.trimIndent(), ContentType.Text.Html
            )
        }

        val codeCache = HashMap<String, String>()

        val verifyResultCache = HashMap<String, Map<String, String?>>()

        get("/login") {
            val state = call.request.queryParameters.getOrFail("state")

            val authReq = authCache[state] ?: error("No such state: $state")

            val requestedClaims = listOf(
                "$.name",
                "$.credentialSubject.achievement.description",
                "$.credentialSubject.achievement.criteria.narrative"
            )

            val verificationResult = Verifier.getVerificationResult(reqCache[state] ?: error("No req for state: $state"), requestedClaims)

            when (verificationResult.state) {
                VerificationStatus.WAITING_FOR_SUBMISSION -> {
                    val url = urlCache[state] ?: error("No such state: $state")

                    //language=HTML
                    call.respondText(
                        """
                            <html><body>
                            <p>Not presented yet, please try again<p>
                            <p>Present your credential: <code>$url</code> (just imagine real hard that this is a QR code)</p>
                            <a href="/login?state=${state}"><button>Present</button></a> Click here when presented (just imagine real hard that this is automatic)
                        </body></html>
                        """.trimIndent(), ContentType.Text.Html
                    )
                }
                VerificationStatus.RESPONSE_RECEIVED ->
                    if (verificationResult.success == true) {
                        val generatedCode = Uuid.random().toString()

                        codeCache[generatedCode] = state
                        verifyResultCache[generatedCode] = verificationResult.claims!!

                        call.respondRedirect(authReq.redirectUri + "?code=$generatedCode&state=${authReq.state}") // todo handle redirect URIs that already have parameters
                    } else {
                        call.respondText("Presentation failed!")
                    }
            }
        }


        val tokenClaimCache = HashMap<String, Map<String, String?>>()


        // Step 2
        post("/token") {
            val form = call.receiveParameters()


            /*val (clientId, clientSecret) = call.request.authorization()?.decodeBase64String()?.split(":")

                throw IllegalArgumentException(
                    "Client id or client secret missing in basic auth"
                )*/

            val basicAuth = call.request.authorization()?.removePrefix("Basic ")?.decodeBase64String()?.split(":")

            val clientId = basicAuth?.get(0) ?: form["client_id"] ?: error("No client_id provided")
            val clientSecret = basicAuth?.get(1) ?: form["client_secret"] ?: error("No client_secret provided")

            println("Client id: $clientId, client secret: $clientSecret")


            val grantType = form.getOrFail("grant_type")
            val code = form.getOrFail("code")
            val redirectUri = form.getOrFail("redirect_uri")

            println("grant type = $grantType")

            val req = authCache[codeCache[code] ?: error("No such code: $code")] ?: error("No such req for state: ${codeCache[code]}")

            check(req.redirectUri == redirectUri) { "Redirect uri does not match: ${req.redirectUri} - $redirectUri" }


            val accessToken = "access-idpkit_" + Uuid.random().toString()


            val claims = verifyResultCache[code]!!

            tokenClaimCache[accessToken] = claims

            val tokenResponse = TokenResponse(
                idToken = "id token here",
                accessToken = accessToken,
                tokenType = "Bearer",
                expiresIn = 3600
            )


            call.respond(tokenResponse)
        }


        get("/userinfo") {
            val auth = call.request.authorization()?.removePrefix("Bearer ")

            if (tokenClaimCache.containsKey(auth)) {
                val claims = tokenClaimCache[auth]!!

                call.respond(
                    claims.toJsonObject()
                )
            } else {
                call.respond(HttpStatusCode.Unauthorized)
            }
        }

    }
}
