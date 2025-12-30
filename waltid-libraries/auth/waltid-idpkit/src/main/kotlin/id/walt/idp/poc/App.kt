@file:OptIn(ExperimentalTime::class)

package id.walt.idp.poc

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.JWSObject
import com.nimbusds.jose.Payload
import com.nimbusds.jose.crypto.Ed25519Signer
import com.nimbusds.jose.jwk.JWK
import id.walt.idp.oidc.AuthorizeRequest
import id.walt.idp.oidc.CustomAccessToken
import id.walt.idp.oidc.IdToken
import id.walt.idp.oidc.TokenResponse
import id.walt.idp.utils.JsonUtils.toJsonObject
import id.walt.idp.verifier.VerificationStatus
import id.walt.idp.verifier.Verifier
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
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.doublereceive.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.util.*
import io.ktor.util.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.io.File
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/*
 * TODO: switch to service-commons
 */
lateinit var config: PocIdpKitConfiguration

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

    val configFile = File("idp-config.json")
    println("Loading config from $configFile...")
    config = Json.decodeFromString(configFile.readText())

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
                  Headers: ${call.response.headers.allValues().toMap().dropCommonHeaders()}
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

val key by lazy { JWK.parse(config.key.trimIndent()).toOctetKeyPair() }

val signer by lazy { Ed25519Signer(key) }


fun signPayload(payload: JsonObject) = JWSObject(
    JWSHeader.Builder(JWSAlgorithm.EdDSA)
        .keyID(key.keyID).build(),
    Payload(payload.toString())
).apply {
    sign(signer)
}.serialize()


@OptIn(ExperimentalUuidApi::class)
fun Application.test() {
    routing {

        get("/jwks") {
            call.respond(
                mapOf(
                "keys" to listOf(key).map { it.toPublicJWK().toJSONObject() }
            ))
        }


        // Step 0. (call by RP)
        get(".well-known/openid-configuration") {
            val issuer = config.issuer

            val wellKnown = mapOf(
                "issuer" to issuer,
                "authorization_endpoint" to "$issuer/authorize",
                "token_endpoint" to "$issuer/token",
                "userinfo_endpoint" to "$issuer/userinfo",
                //"jwks_uri" to "$issuer/jwks",
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

            val verificationRequest = config.verifierRequest

            val (url, token) = Verifier.verify(verificationRequest, redirectUrl = "${config.redirectUrl}?state=${req.state}")
            reqCache[req.state] = token
            urlCache[req.state] = url


            val walletUrl = config.walletUrl + "?" + url.substringAfter("?")
            println("Wallet url: $walletUrl")

            //language=HTML
            call.respondText(
                """
                    <html><body>
                    <p>Present your credential: <code>$url</code></p>
                    <div id="qrcode"></div>
                    <p>
                    <a href="/login?state=${req.state}"><button>Click here when presented</button></a> (todo: make automatic)
                    </p>
                    <p>
                    <a href="/login?state=${req.state}&debug=autologin"><button>Debug autologin</button> (enabled=${config.enableDebug})</a>
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

        @Serializable
        data class LoginData(val code: String, val claims: Map<String, String?>, val time: Instant)

        val loginResultCache = HashMap<String, LoginData>()

        get("/login") {
            val state = call.request.queryParameters.getOrFail("state")
            val debug = call.request.queryParameters["debug"]

            val authReq = authCache[state] ?: error("No such state: $state")

            val requestedClaims = config.claimMapping

            val time = Clock.System.now()

            if (debug == "autologin" && config.enableDebug) {
                val generatedCode = Uuid.random().toString()

                loginResultCache[generatedCode] = LoginData(
                    code = state,
                    claims = mapOf(
                        "sub" to "demo",
//                        "name" to "Demo User",
//                        "preferred_username" to "demo",
//                        "email" to "demo@example.org",
                        "debug" to "autologin",
                    ),
                    time = time
                )

                call.respondRedirect(authReq.redirectUri + "?code=$generatedCode&state=${authReq.state}")
                return@get
            }

            val verificationResult = Verifier.getVerificationResult(reqCache[state] ?: error("No req for state: $state"), requestedClaims)

            when (verificationResult.state) {
                VerificationStatus.WAITING_FOR_SUBMISSION -> {
                    val url = urlCache[state] ?: error("No such state: $state")

                    //language=HTML
                    call.respondText(
                        """
                            <html><body>
                            <p>Not presented yet, please try again</p>
                            <p>Present your credential: <code>$url</code> (just imagine real hard that this is a QR code)</p>
                            <a href="/login?state=${state}"><button>Present</button></a> Click here when presented (just imagine real hard that this is automatic)
                        </body></html>
                        """.trimIndent(), ContentType.Text.Html
                    )
                }

                VerificationStatus.RESPONSE_RECEIVED ->
                    if (verificationResult.success == true) {
                        val generatedCode = Uuid.random().toString()

                        loginResultCache[generatedCode] = LoginData(
                            code = state,
                            claims = verificationResult.claims!!,
                            time = time
                        )

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

            val loginData = loginResultCache[code] ?: error("No such code: $code")
            val req = authCache[loginData.code] ?: error("No such req for state: ${loginData.code}")

            check(req.redirectUri == redirectUri) { "Redirect uri does not match: ${req.redirectUri} - $redirectUri" }

            //val accessToken = "access-idpkit_" + Uuid.random().toString() // should not have to be a JWT
            val accessToken = signPayload(
                Json.encodeToJsonElement(
                    CustomAccessToken(
                        iss = config.issuer,
                        sub = "x",
                        aud = listOf("my-client-id"), // TODO: OIDC client id
                        exp = Clock.System.now().plus(365.days),
                        iat = Clock.System.now(),
                        nonce = req.nonce,
                    )
                ).jsonObject
            )

            val claims = loginData.claims

            tokenClaimCache[accessToken] = claims

            /*val accessToken = signPayload(mapOf(
                "iss" to config.issuer, // TODO: url here
                "aud" to "my-client-id", // TODO: OIDC client id
                "exp" to

                "tokentype" to "access_token",
                "sub" to "x"
            ))*/

            val idTokenData = IdToken(
                iss = config.issuer,
                sub = claims["sub"]!!,
                aud = listOf("my-client-id"), // TODO: OIDC client id
                exp = Clock.System.now().plus(365.days),
                iat = Clock.System.now(),
                auth_time = loginData.time,
                nonce = req.nonce,
            )
            val idToken = signPayload(Json.encodeToJsonElement(idTokenData).jsonObject)

            val tokenResponse = TokenResponse(
                idToken = idToken,
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
