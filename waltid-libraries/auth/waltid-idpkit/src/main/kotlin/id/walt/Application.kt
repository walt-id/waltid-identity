package id.walt

import kotlinx.serialization.json.*

/*
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import com.nimbusds.oauth2.sdk.*
import com.nimbusds.oauth2.sdk.auth.ClientAuthentication
import com.nimbusds.oauth2.sdk.auth.ClientSecretBasic
import com.nimbusds.oauth2.sdk.auth.ClientSecretPost
import com.nimbusds.oauth2.sdk.http.HTTPRequest
import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import io.klogging.Level
import io.klogging.config.loggingConfiguration
import io.klogging.logger
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
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.ktor.server.util.*
import io.ktor.util.*
import kotlinx.datetime.Clock
import kotlinx.datetime.toJavaInstant
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.net.URL
import java.util.Date
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

private val log = logger("walt.id IDP")

data class UserSession(val id: String, val state: String)

@Serializable
data class TokenStore(
    val accessToken: String,
    val tokenType: String,
    val expiresIn: Long,
    val idToken: String,
    val issueTime: kotlinx.datetime.Instant,
    val expirationTime: kotlinx.datetime.Instant,
) {*/
/*fun toJSON(): String {
        return Json.encodeToString(this)
    }*//*

}

val tokenStore = mutableMapOf<String, TokenStore>()

fun main() {
    loggingConfiguration(true) {
        sink("stdout", RENDER_ANSI, STDOUT)
        sink("stderr", RENDER_ANSI, STDERR)

        logging {
            fromLoggerBase("io.ktor.routing.Routing", stopOnMatch = true)
            fromMinLevel(Level.DEBUG) {
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

        install(CallLogging) {
            this.level = org.slf4j.event.Level.DEBUG
        }

        install(Sessions) {
            cookie<UserSession>("USER_SESSION") {
                cookie.path = "/"
                cookie.httpOnly = true
                cookie.extensions["SameSite"] = "Strict"
            }
        }
        install(StatusPages) {
            exception<Throwable> { call, cause ->
                cause.printStackTrace()
                call.respond(HttpStatusCode.InternalServerError, cause.localizedMessage)
            }
        }
        routing {
            get("/") {
                call.respondText("OIDC IDP Server", ContentType.Text.Plain)
            }
            wellKnownConfiguration()
            authenticateUser()
            handleOIDCRequests()
            handleLogout()
        }
    }.start(wait = true)
}


private const val thisIdp = "http://localhost:8080"


// Function to get claims from SignedJWT
suspend fun getClaimsFromJWT(idToken: String): JWTClaimsSet? {
    log.trace { "Getting claims from idToken: $idToken" }
    return try {
        val signedJWT = SignedJWT.parse(idToken)
        signedJWT.jwtClaimsSet.also { log.trace { "Claims from idToken $idToken are: ${signedJWT.jwtClaimsSet}" } }
    } catch (e: ParseException) {
        null
    }
}

// Token removal logic
suspend fun removeToken(tokenRequest: String) {
    log.trace { "Removing token: $tokenRequest" }
    tokenStore.values.removeIf { it.accessToken == tokenRequest || it.idToken == tokenRequest }
}

fun Routing.handleOIDCRequests() {
    get("/authorize") {
        val queryParams = call.request.queryParameters
        log.trace { "OIDC /authorize: with query: ${queryParams.entries()}" }
        val authRequest = AuthorizationRequest.parse(queryParams.toMap())
        log.trace { "OIDC /authorize: validating auth request: (query encoded) ${authRequest.toQueryString()}" }
        validateAuthRequest(authRequest)

        //val customLogicResult = performCustomLogic()
        val x = queryParams.get("custom-login-stuff")
        if (x == null) {
            call.respondRedirect("/login?${authRequest.toQueryString()}")
            return@get
        }

        val customLogicResult = x == "success"


        if (customLogicResult) {
            val authCode = AuthorizationCode()
            val redirectURI = authRequest.redirectionURI.toString() + "?code=${authCode.value}"

            log.trace { "OIDC /authorize: validated with custom logic: (query encoded) ${authRequest.toQueryString()}, redirecting to ${redirectURI}, state is ${authRequest.state} (state value ${authRequest.state?.value}" }

            // Store the auth code details and user session
            call.sessions.set(UserSession(id = "user-id", state = authRequest.state.value))
            call.respondRedirect(redirectURI)
        } else {
            log.trace { "OIDC /authorize: validating custom logic failed for $authRequest" }
            call.respond(HttpStatusCode.Forbidden, "Custom logic failed")
        }
    }

    post("/token") {
        log.trace { "OIDC /token: Parsing" }
        val httpRequest = convertKtorRequestToNimbusHTTPRequest(call.request)
        val tokenRequest = TokenRequest.parse(httpRequest)

        log.trace { "OIDC /token: Parsed to token request: $tokenRequest, validating client credentials..." }

        validateClientCredentials(tokenRequest.clientAuthentication)

        when (val grant = tokenRequest.authorizationGrant) {
            is AuthorizationCodeGrant -> {
                log.trace { "OIDC /token: Grant is AuthorizationCodeGrant!" }
                validateTokenRequest(tokenRequest)
                val tokens = issueTokens(grant.authorizationCode)
                log.trace { "OIDC /token: Issued tokens: $tokens" }
                tokenStore[grant.authorizationCode.value] = tokens
                log.trace { "OIDC /token: Associate issued tokens with ${grant.authorizationCode.value}" }
                call.respond(tokens)
                log.trace { "OIDC /token: Returned: ${Json.encodeToString(tokens)}" }
            }

            is RefreshTokenGrant -> {
                log.trace { "OIDC /token: Grant is RefreshTokenGrant!" }
                val refreshToken = grant.refreshToken.value
                val tokens = tokenStore.values.find { it.accessToken == refreshToken }
                if (tokens != null && tokens.expirationTime > Clock.System.now()) {
                    val newTokens = issueTokens(AuthorizationCode()) // Issue new tokens
                    log.trace { "OIDC /token (refresh): New issued tokens: $newTokens" }
                    tokenStore[refreshToken] = newTokens
                    log.trace { "OIDC /token: Associate refreshed tokens with $refreshToken" }
                    call.respond(newTokens)
                } else {
                    log.trace { "OIDC /token (refresh): Invalid or expired refresh token" }
                    call.respond(HttpStatusCode.BadRequest, "Invalid or expired refresh token")
                }
            }

            else -> {
                log.trace { "OIDC /token: Unsupported grant type: ${grant.type.value}" }
                call.respond(HttpStatusCode.BadRequest, "Unsupported grant type")
            }
        }
    }

    get("/userinfo") {
        val bearerToken = call.request.headers["Authorization"]?.removePrefix("Bearer ")
        log.trace { "OIDC /userinfo: $bearerToken" }
        if (bearerToken == null) {
            call.respond(HttpStatusCode.Unauthorized, "Missing token")
            return@get
        }

        val tokenInfo = tokenStore.values.find { it.accessToken == bearerToken || it.idToken == bearerToken }
        if (tokenInfo != null && tokenInfo.expirationTime > Clock.System.now()) {
            val claims = getClaimsFromJWT(tokenInfo.idToken)
            val userInfo = mapOf(
                "sub" to (claims?.subject ?: "unknown"),
                "name" to (claims?.getClaim("name") ?: "unknown"),
                "email" to (claims?.getClaim("email") ?: "unknown")
            )
            call.respond(userInfo)
        } else {
            call.respond(HttpStatusCode.Unauthorized, "Invalid or expired token")
        }
    }

    post("/introspect") {
        val tokenRequest = call.receiveParameters()["token"]
        log.trace { "OIDC /introspect: $tokenRequest" }
        if (tokenRequest == null) {
            call.respond(HttpStatusCode.BadRequest, "Missing token")
            return@post
        }

        val tokenInfo = tokenStore.values.find { it.accessToken == tokenRequest || it.idToken == tokenRequest }
        if (tokenInfo != null) {
            val isActive = tokenInfo.expirationTime > Clock.System.now()
            val claims = getClaimsFromJWT(tokenInfo.idToken)
            val introspectionResponse = mapOf(
                "active" to isActive,
                "scope" to "openid profile email",
                "client_id" to "your-client-id",
                "username" to (claims?.subject ?: "unknown"),
                "token_type" to "Bearer",
                "exp" to tokenInfo.expirationTime.epochSeconds,
                "iat" to tokenInfo.issueTime.epochSeconds,
                "sub" to (claims?.subject ?: "unknown")
            )
            call.respond(introspectionResponse)
        } else {
            call.respond(mapOf("active" to false))
        }
    }

    // Token endpoint with removal logic
    post("/revoke") {
        val tokenRequest = call.receiveParameters()["token"]
        log.trace { "Revoking $tokenRequest" }
        if (tokenRequest == null) {
            call.respond(HttpStatusCode.BadRequest, "Missing token")
            return@post
        }

        removeToken(tokenRequest)
        call.respond(HttpStatusCode.OK, "Token revoked")
    }
}

suspend fun performCustomLogic(): Boolean {
    log.trace { "-- Custom logic here --" }
    // Custom backend logic
    return true
}

// TODO FIXME: use authCode
suspend fun issueTokens(authCode: AuthorizationCode): TokenStore {

    log.trace { "Issuing tokens, - but without auth code?" }

    val accessToken = BearerAccessToken(3600) // Token valid for 3600 seconds
    val issueTime = Clock.System.now()
    val expirationTime = issueTime.plus(3600.seconds)

    val idTokenClaims = JWTClaimsSet.Builder().issuer(thisIdp).subject("user-id").audience("your-client-id")
        .issueTime(Date.from(issueTime.toJavaInstant())).expirationTime(Date.from(expirationTime.toJavaInstant())).build()
    val idToken = SignedJWT(JWSHeader(JWSAlgorithm.RS256), idTokenClaims)
    idToken.sign(RSASSASigner(generateRSAKey().toPrivateKey()))

    return TokenStore(
        accessToken = accessToken.value,
        tokenType = accessToken.type.value,
        expiresIn = 3600, // This represents the lifetime of the access token in seconds
        idToken = idToken.serialize(),
        issueTime = issueTime,
        expirationTime = expirationTime
    ).also { log.trace { "Issued: $it" } }
}

suspend fun generateRSAKey(): RSAKey {
    log.trace { "Generating RSA key..." }
    return RSAKeyGenerator(2048).keyID("123").generate()
}

suspend fun validateAuthRequest(authRequest: AuthorizationRequest) {
    log.trace { "Validating auth request: (query encoded) ${authRequest.toQueryString()}" }
    // Validate client ID, redirect URI, and scopes
    val validClientId = "your-client-id"
    val validRedirectURI = "https://your-client.com/callback"
    val validScopes = setOf("openid", "profile", "email")

    require(authRequest.clientID.value == validClientId) { "Invalid client ID" }
    // TODO require(authRequest.redirectionURI.toString() == validRedirectURI) { "Invalid redirect URI" }
    // TODO require(validScopes.all { authRequest.scope.contains(it) }) { "Invalid scopes" }

}

suspend fun validateTokenRequest(tokenRequest: TokenRequest) {
    log.trace { "Validating token request: $tokenRequest" }
    // Validate the token request parameters
    require(tokenRequest.clientAuthentication.clientID.value == "your-client-id") { "Invalid client ID" }
    require(tokenRequest.authorizationGrant.type == GrantType.AUTHORIZATION_CODE) { "Invalid grant type" }
}

suspend fun validateClientCredentials(clientAuthentication: ClientAuthentication) {
    log.trace { "Validating client authentication: $clientAuthentication" }

    val validClientSecret = "your-client-secret"

    when (clientAuthentication) {
        is ClientSecretBasic -> {
            require(clientAuthentication.clientID.value == "your-client-id") { "Invalid client ID" }
            require(clientAuthentication.clientSecret.value == validClientSecret) { "Invalid client secret" }
        }
        is ClientSecretPost -> {
            require(clientAuthentication.clientID.value == "your-client-id") { "Invalid client ID" }
            require(clientAuthentication.clientSecret.value == validClientSecret) { "Invalid client secret" }
        }

        else -> throw IllegalStateException("OAuth2Error.INVALID_CLIENT, client authentication: $clientAuthentication (clientid = ${clientAuthentication.clientID.value}, method: ${clientAuthentication.method.value}, form: ${clientAuthentication.formParameterNames})")
    }
}

fun Routing.handleLogout() {
    get("/logout") {
        log.trace { "Logging out" }
        call.sessions.clear<UserSession>()
        call.respondText("Logged out", ContentType.Text.Plain)
    }
}

fun Routing.authenticateUser() {
    get("/login") {

        val query = call.request.queryParameters.formUrlEncode()

        log.trace { "Displaying login form" }
        // Display login form
        call.respondText(
            """
            <form action="/login" method="post">
                Present credential: <input type="text" value='{"mocked": "credential"}' name="cred"><br>
                Debug query: <input type="text" name="query" value="$query"><br>
                <input type="submit" value="Login">
            </form>
        """, ContentType.Text.Html
        )
    }

    post("/login") {
        log.trace { "Logging in..." }
        val post = call.receiveParameters()
        val cred = post["cred"]
        val query = post["query"]

        if (cred != null) {
            // Set user session

            val state = UUID.randomUUID().toString()

            call.sessions.set(UserSession(id = "some-user-id", state = state))

            val redirect = "/authorize?custom-login-stuff=success&$query&state=$state"

            log.trace { "Redirecting after successful login: $redirect" }
            call.respondRedirect(redirect) // Redirect to home page or requested resource
        } else {
            call.respond(HttpStatusCode.BadRequest, "Missing credentials")
        }
    }
}

val jwk = RSAKeyGenerator(2048).keyID("123").generate()

fun Routing.wellKnownConfiguration() {
    get("/.well-known/openid-configuration") {
        val issuer = thisIdp
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
            "token_endpoint_auth_methods_supported" to listOf("client_secret_basic"),
            "claims_supported" to listOf("sub", "iss", "aud", "iat", "exp", "name", "email")
        ).toJsonObject()
        log.trace { "OIDC /well-known: Well-known is: $wellKnown" }
        call.respond(wellKnown)
    }

    get("/jwks") {
        log.trace { "Handling /jwks..." }
        val jwkSet = JWKSet(jwk)
        call.respondText(jwkSet.toJSONObject().toString(), ContentType.Application.Json)
    }
}


suspend fun convertKtorRequestToNimbusHTTPRequest(request: ApplicationRequest): HTTPRequest {
    log.trace { "Converting ktor to nimbus http request..." }
    val method = HTTPRequest.Method.valueOf(request.httpMethod.value)

    */
/*val method = when (request.httpMethod.value) {
        "GET" -> HTTPRequest.Method.GET
        "POST" -> Method.POST
        "PUT" -> Method.PUT
        "DELETE" -> Method.DELETE
        "OPTIONS" -> Method.OPTIONS
        "HEAD" -> Method.HEAD
        else -> throw IllegalArgumentException("Unsupported HTTP method")
    }*//*


    val url = request.call.url()

    log.trace { "Converting request... Method = $method, url = $url" }

    val httpRequest = HTTPRequest(method, URL(url))

    // Copy headers
    request.headers.forEach { key, values ->
        values.forEach { value ->
            httpRequest.setHeader(key, value)
        }
    }

    // Copy query parameters (if any)
    val queryParams = request.queryParameters.entries()
    if (queryParams.isNotEmpty()) {
        val queryString = queryParams.joinToString("&") { "${it.key}=${it.value.joinToString(",")}" }
        httpRequest.query = queryString
    }

    // Copy body (if any)
    if (request.httpMethod == HttpMethod.Post || request.httpMethod == HttpMethod.Put) {
        //httpRequest.setContent(request.receiveChannel().toByteArray())
        httpRequest.body = request.receiveChannel().toByteArray().decodeToString()
        httpRequest.setContentType(request.contentType().toString())
    }

    log.trace { "Converted ktor to nimbus http request: $httpRequest" }
    return httpRequest
}

*/
fun Any?.toJsonElement(): JsonElement =
    when (this) {
        is JsonElement -> this
        null -> JsonNull
        is String -> JsonPrimitive(this)
        is Boolean -> JsonPrimitive(this)
        is Number -> JsonPrimitive(this)

        /*
        is UByte -> JsonPrimitive(this)
        is UInt -> JsonPrimitive(this)
        is ULong -> JsonPrimitive(this)
        is UShort -> JsonPrimitive(this)
        */

        is Map<*, *> -> JsonObject(map { Pair(it.key.toString(), it.value.toJsonElement()) }.toMap())
        is List<*> -> JsonArray(map { it.toJsonElement() })
        is Array<*> -> JsonArray(map { it.toJsonElement() })
        is Collection<*> -> JsonArray(map { it.toJsonElement() })
        is Enum<*> -> JsonPrimitive(this.toString())
        else -> throw IllegalArgumentException("Unknown type: ${this::class.simpleName}, was: $this")
    }

fun List<*>.toJsonElement(): JsonElement {
    return JsonArray(map { it.toJsonElement() })
}

fun Map<*, *>.toJsonElement(): JsonElement {
    val map: MutableMap<String, JsonElement> = mutableMapOf()
    this.forEach { (key, value) ->
        map[key as String] = value.toJsonElement()
    }
    return JsonObject(map)
}

fun Map<*, *>.toJsonObject() = this.toJsonElement().jsonObject

private fun toHexChar(i: Int): Char {
    val d = i and 0xf
    return if (d < 10) (d + '0'.code).toChar()
    else (d - 10 + 'a'.code).toChar()
}

private val ESCAPE_STRINGS: Array<String?> = arrayOfNulls<String>(93).apply {
    for (c in 0..0x1f) {
        val c1 = toHexChar(c shr 12)
        val c2 = toHexChar(c shr 8)
        val c3 = toHexChar(c shr 4)
        val c4 = toHexChar(c)
        this[c] = "\\u$c1$c2$c3$c4"
    }
    this['"'.code] = "\\\""
    this['\\'.code] = "\\\\"
    this['\t'.code] = "\\t"
    this['\b'.code] = "\\b"
    this['\n'.code] = "\\n"
    this['\r'.code] = "\\r"
    this[0x0c] = "\\f"
}

private fun StringBuilder.printQuoted(value: String) {
    append('"')
    var lastPos = 0
    for (i in value.indices) {
        val c = value[i].code
        if (c < ESCAPE_STRINGS.size && ESCAPE_STRINGS[c] != null) {
            append(value, lastPos, i) // flush prev
            append(ESCAPE_STRINGS[c])
            lastPos = i + 1
        }
    }

    if (lastPos != 0) append(value, lastPos, value.length)
    else append(value)
    append('"')
}

fun Map<String, JsonElement>.printAsJson(): String =
    this.entries.joinToString(
        separator = ",",
        prefix = "{",
        postfix = "}",
        transform = { (k, v) ->
            buildString {
                printQuoted(k)
                append(':')
                append(v)
            }
        }
    )

fun stringToJsonPrimitive(value: String): JsonPrimitive {
    return JsonPrimitive(value)
}


