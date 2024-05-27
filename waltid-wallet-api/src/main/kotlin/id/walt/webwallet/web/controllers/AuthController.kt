package id.walt.webwallet.web.controllers

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.JWSObject
import com.nimbusds.jose.Payload
import com.nimbusds.jose.crypto.MACSigner
import com.nimbusds.jose.crypto.MACVerifier
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.JsonUtils.toJsonElement
import id.walt.oid4vc.definitions.JWTClaims
import id.walt.webwallet.config.AuthConfig
import id.walt.webwallet.config.ConfigManager
import id.walt.webwallet.config.WebConfig
import id.walt.webwallet.db.models.AccountWalletMappings
import id.walt.webwallet.db.models.AccountWalletPermissions
import id.walt.webwallet.service.OidcLoginService
import id.walt.webwallet.service.WalletServiceManager
import id.walt.webwallet.service.WalletServiceManager.oidcConfig
import id.walt.webwallet.service.account.AccountsService
import id.walt.webwallet.service.account.KeycloakAccountStrategy
import id.walt.webwallet.web.ForbiddenException
import id.walt.webwallet.web.InsufficientPermissionsException
import id.walt.webwallet.web.UnauthorizedException
import id.walt.webwallet.web.WebBaseRoutes.webWalletRoute
import id.walt.webwallet.web.model.*
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.smiley4.ktorswaggerui.dsl.get
import io.github.smiley4.ktorswaggerui.dsl.post
import io.github.smiley4.ktorswaggerui.dsl.route
import io.ktor.client.*
import io.ktor.http.*
import io.ktor.http.auth.*
import io.ktor.http.parsing.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.sessions.*
import io.ktor.util.pipeline.*
import kotlinx.datetime.Clock
import kotlinx.datetime.toJavaInstant
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import kotlinx.uuid.UUID
import kotlinx.uuid.generateUUID
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.temporal.ChronoUnit
import kotlin.collections.set
import kotlin.time.Duration.Companion.days

private val logger = KotlinLogging.logger {}

@Suppress("ArrayInDataClass")
data class ByteLoginRequest(val username: String, val password: ByteArray) {
    constructor(
        loginRequest: EmailAccountRequest,
    ) : this(loginRequest.email, loginRequest.password.toByteArray())

    override fun toString() = "[LOGIN REQUEST FOR: $username]"
}

data class LoginTokenSession(val token: String) : Principal

data class OidcTokenSession(val token: String) : Principal

object AuthKeys {
    private val config = ConfigManager.getConfig<AuthConfig>()
    val encryptionKey: ByteArray = config.encryptionKey.encodeToByteArray()
    val signKey: ByteArray = config.signKey.encodeToByteArray()

    val tokenKey: ByteArray = config.tokenKey.encodeToByteArray()
    val issTokenClaim: String = config.issTokenClaim
    val audTokenClaim: String? = config.audTokenClaim
    val tokenLifetime: Long = config.tokenLifetime.toLongOrNull() ?: 1

}

fun Application.configureSecurity() {
    val webConfig = ConfigManager.getConfig<WebConfig>()
    install(Sessions) {
        cookie<LoginTokenSession>("login") {
            // cookie.encoding = CookieEncoding.BASE64_ENCODING

            // cookie.httpOnly = true
            cookie.httpOnly = false // FIXME
            // TODO cookie.secure = true
            cookie.maxAge = 1.days
            cookie.extensions["SameSite"] = "Strict"
            transform(SessionTransportTransformerEncrypt(AuthKeys.encryptionKey, AuthKeys.signKey))
        }
        cookie<OidcTokenSession>("oidc-login") {
            // cookie.encoding = CookieEncoding.BASE64_ENCODING

            // cookie.httpOnly = true
            cookie.httpOnly = false // FIXME
            // TODO cookie.secure = true
            cookie.maxAge = 1.days
            cookie.extensions["SameSite"] = "Strict"
            transform(SessionTransportTransformerEncrypt(AuthKeys.encryptionKey, AuthKeys.signKey))
        }
    }

    install(Authentication) {
        oauth("auth-oauth") {
            client = HttpClient()
            providerLookup = {
                OAuthServerSettings.OAuth2ServerSettings(
                    name = oidcConfig.providerName,
                    authorizeUrl = oidcConfig.authorizeUrl,
                    accessTokenUrl = oidcConfig.accessTokenUrl,
                    clientId = oidcConfig.clientId,
                    clientSecret = oidcConfig.clientSecret,
                    accessTokenRequiresBasicAuth = false,
                    requestMethod = HttpMethod.Post,
                    defaultScopes = oidcConfig.oidcScopes
                )
            }
            urlProvider = { "${webConfig.publicBaseUrl}/wallet-api/auth/oidc-session" }
        }

        jwt("auth-oauth-jwt") {
            realm = OidcLoginService.oidcRealm
            // verifier(jwkProvider, oidcRealm)
            verifier(OidcLoginService.jwkProvider)

            validate { credential -> JWTPrincipal(credential.payload) }
            challenge { _, _ ->
                call.respond(HttpStatusCode.Unauthorized, "Token is not valid or has expired")
            }
        }

        bearer("auth-bearer") {
            authenticate { tokenCredential ->
                val verificationResult = verifyToken(tokenCredential.token)
                verificationResult.getOrNull()?.let { UserIdPrincipal(it) }
            }
        }

        bearer("auth-bearer-alternative") {
            authHeader { call ->
                call.request.header("waltid-authorization")?.let {
                    try {
                        parseAuthorizationHeader(it)
                    } catch (cause: ParseException) {
                        throw BadRequestException("Invalid auth header", cause)
                    }
                }
            }
            authenticate { tokenCredential ->
                val verificationResult = verifyToken(tokenCredential.token)
                verificationResult.getOrNull()?.let { UserIdPrincipal(it) }
            }
        }

        session<LoginTokenSession>("auth-session") {
            validate { session ->
                val verificationResult = verifyToken(session.token)
                val userId = verificationResult.getOrNull()

                if (userId != null) {
                    UserIdPrincipal(userId)
                } else {
                    sessions.clear("login")
                    null
                }
            }

            challenge {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    JsonObject(mapOf("message" to JsonPrimitive("Login Required")))
                )
            }
        }
    }
}

fun Application.auth() {
    webWalletRoute {
        route("auth", { tags = listOf("Authentication") }) {
            authenticate("auth-oauth") {
                get(
                    "oidc-login",
                    {
                        description = "Redirect to OIDC provider for login"
                        response { HttpStatusCode.Found }
                    }) {
                    call.respondRedirect("oidc-session")
                }

                authenticate("auth-oauth-jwt") {
                    get("oidc-session", { description = "Configure OIDC session" }) {
                        val principal: OAuthAccessTokenResponse.OAuth2 =
                            call.principal() ?: error("No OAuth principal")

                        call.sessions.set(OidcTokenSession(principal.accessToken))

                        call.respondRedirect("/login?oidc_login=true")
                    }
                }
            }

            get("oidc-token", { description = "Returns OIDC token" }) {
                val oidcSession = call.sessions.get<OidcTokenSession>() ?: error("No OIDC session")

                call.respond(oidcSession.token)
            }

            post(
                "login",
                {
                    summary =
                        "Login with [email + password] or [wallet address + ecosystem] or [oidc session]"
                    request {
                        body<EmailAccountRequest> {
                            example(
                                "E-mail + password",
                                buildJsonObject {
                                    put("type", JsonPrimitive("email"))
                                    put("email", JsonPrimitive("user@email.com"))
                                    put("password", JsonPrimitive("password"))
                                }
                                    .toString())
                            example(
                                "Wallet address + ecosystem",
                                buildJsonObject {
                                    put("type", JsonPrimitive("address"))
                                    put("address", JsonPrimitive("0xABC"))
                                    put("ecosystem", JsonPrimitive("ecosystem"))
                                }
                                    .toString())
                            example(
                                "OIDC token",
                                buildJsonObject {
                                    put("type", JsonPrimitive("oidc"))
                                    put("token", JsonPrimitive("oidc token"))
                                }
                                    .toString())
                        }
                    }
                    response {
                        HttpStatusCode.OK to { description = "Login successful" }
                        HttpStatusCode.Unauthorized to { description = "Login failed" }
                        HttpStatusCode.BadRequest to { description = "Login failed" }
                    }
                }) {
                doLogin()
            }

            post(
                "create",
                {
                    summary = "Register with [email + password] or [wallet address + ecosystem]"
                    request {
                        body<EmailAccountRequest> {

                            fun AccountRequest.encodeExample(type: String) =
                                JsonObject(loginRequestJson.encodeToJsonElement(this).jsonObject.toMutableMap()
                                    .apply {
                                        this["type"] = JsonPrimitive(type)
                                    })
                                    .toString()

                            example(
                                "E-mail + password",
                                EmailAccountRequest(
                                    name = "Max Mustermann",
                                    email = "user@email.com",
                                    password = "password"
                                ).encodeExample("email")
                            )
                            example(
                                "Wallet address + ecosystem",
                                AddressAccountRequest(address = "0xABC", ecosystem = "ecosystem").encodeExample("address")
                            )
                            example("OIDC", OidcAccountRequest(token = "ey...").encodeExample("oidc"))
                            example("OIDC Unique Subject", OidcUniqueSubjectRequest(token = "ey...").encodeExample("oidc-unique-subject"))
                            example("Keycloak", KeycloakAccountRequest().encodeExample("keycloak"))
                        }
                    }
                    response {
                        HttpStatusCode.Created to { description = "Registration succeeded " }
                        HttpStatusCode.BadRequest to { description = "Registration failed" }
                    }
                }) {
                val req = loginRequestJson.decodeFromString<AccountRequest>(call.receive())
                AccountsService.register("", req)
                    .onSuccess {
                        call.response.status(HttpStatusCode.Created)
                        call.respond("Registration succeeded ")
                    }
                    .onFailure { call.respond(HttpStatusCode.BadRequest, it.localizedMessage) }
            }

            authenticate("auth-session", "auth-bearer", "auth-bearer-alternative") {
                get("user-info", { summary = "Return user ID if logged in" }) {
                    getUsersSessionToken()?.run {
                        val jwsObject = JWSObject.parse(this)
                        val uuid =
                            Json.parseToJsonElement(jwsObject.payload.toString()).jsonObject["sub"]?.jsonPrimitive?.content.toString()
                        call.respond(AccountsService.get(UUID(uuid)))
                    }
                        ?: call.respond(HttpStatusCode.BadRequest)
                }
                get("session", { summary = "Return session ID if logged in" }) {
                    val token = getUsersSessionToken() ?: throw UnauthorizedException("Invalid session")
                    call.respond(mapOf("token" to mapOf("accessToken" to token)))
                }
            }

            post(
                "logout",
                {
                    summary = "Logout (delete session)"
                    response { HttpStatusCode.OK to { description = "Logged out." } }
                }) {
                clearUserSession()

                call.respond(HttpStatusCode.OK)
            }

            get("logout-oidc", { description = "Logout via OIDC provider" }) {
                val webConfig = ConfigManager.getConfig<WebConfig>()
                call.respondRedirect(
                    "${oidcConfig.logoutUrl}?post_logout_redirect_uri=${webConfig.publicBaseUrl}&client_id=${oidcConfig.clientId}"
                )
            }
        }
        route("auth/keycloak", { tags = listOf("Keycloak Authentication") }) {

            // Generates Keycloak access token
            get(
                "token",
                {
                    summary = "Returns Keycloak access token"
                    description =
                        "Returns a access token to be used for all further operations towards Keycloak. Required Keycloak configuration in oidc.conf."
                }) {
                logger.debug { "Fetching Keycloak access token" }
                val access_token = KeycloakAccountStrategy.getAccessToken()
                call.respond(access_token)
            }

            // Create Keycloak User
            post(
                "create",
                {
                    summary = "Keycloak registration with [username + email + password]"
                    description = "Creates a user in the configured Keycloak instance."
                    request {
                        body<KeycloakAccountRequest> {
                            example(
                                "username + email + password",
                                buildJsonObject {
                                    put("username", JsonPrimitive("Max_Mustermann"))
                                    put("email", JsonPrimitive("user@email.com"))
                                    put("password", JsonPrimitive("password"))
                                    put("token", JsonPrimitive("eyJhb..."))
                                    put("type", JsonPrimitive("keycloak"))
                                }
                                    .toString())
                        }
                    }
                    response {
                        HttpStatusCode.Created to { description = "Registration succeeded " }
                        HttpStatusCode.BadRequest to { description = "Registration failed" }
                    }
                }) {
                val req = loginRequestJson.decodeFromString<AccountRequest>(call.receive())

                logger.debug { "Creating Keycloak user" }

                AccountsService.register("", req)
                    .onSuccess {
                        call.response.status(HttpStatusCode.Created)
                        call.respond("Registration succeeded ")
                    }
                    .onFailure { call.respond(HttpStatusCode.BadRequest, it.localizedMessage) }
            }

            // Login a Keycloak user
            post("login", {
                summary = "Keycloak login with [username + password]"
                description = "Login of a user managed by Keycloak."
                request {
                    body<KeycloakAccountRequest> {
                        example(
                            "Keycloak username + password",
                            buildJsonObject {
                                put("type", JsonPrimitive("keycloak"))
                                put("username", JsonPrimitive("Max_Mustermann"))
                                put("password", JsonPrimitive("password"))
                            }
                                .toString())
                        example(
                            "Keycloak username + Access Token ",
                            buildJsonObject {
                                put("type", JsonPrimitive("keycloak"))
                                put("username", JsonPrimitive("Max_Mustermann"))
                                put("token", JsonPrimitive("eyJhb..."))
                            }
                                .toString())

                        example(
                            "Keycloak user Access Token ",
                            buildJsonObject {
                                put("type", JsonPrimitive("keycloak"))
                                put("token", JsonPrimitive("eyJhb..."))
                            }
                                .toString())
                    }
                }

                response {
                    HttpStatusCode.OK to { description = "Login successful" }
                    HttpStatusCode.Unauthorized to { description = "Unauthorized" }
                    HttpStatusCode.BadRequest to { description = "Bad request" }
                }
            }) {
                doLogin()
            }

            // Terminate Keycloak session
            post(
                "logout",
                {
                    summary = "Logout via Keycloak provider."
                    description =
                        "Terminates Keycloak and wallet session by the user identified by the Keycloak user ID."
                    request {
                        body<KeycloakLogoutRequest> {
                            example(
                                "keycloakUserId + token",
                                buildJsonObject {
                                    put("keycloakUserId", JsonPrimitive("3d09 ..."))
                                    put("token", JsonPrimitive("eyJhb ..."))
                                }
                                    .toString())
                        }
                    }
                    response { HttpStatusCode.OK to { description = "Keycloak HTTP status code." } }
                }) {
                clearUserSession()

                logger.debug { "Clearing Keycloak user session" }

                val req = Json.decodeFromString<KeycloakLogoutRequest>(call.receive())

                call.respond("Keycloak responded with: ${KeycloakAccountStrategy.logout(req)}")
            }
        }
    }
}

/**
 * @param token JWS token provided by user
 * @return user/account ID if token is valid
 */
suspend fun verifyToken(token: String): Result<String> {
    val jwsObject = JWSObject.parse(token)

    val key = JWKKey.importJWK(AuthKeys.tokenKey.decodeToString()).getOrNull()
    if (key == null) {
        // if (AuthKeys.tokenSignAlg == "HS256") {
        val verifier = MACVerifier(AuthKeys.tokenKey)
        return runCatching { jwsObject.verify(verifier) }
            .mapCatching { valid ->
                if (valid) Json.parseToJsonElement(jwsObject.payload.toString()).jsonObject["sub"]?.jsonPrimitive?.content.toString() else throw IllegalArgumentException(
                    "Token is not valid."
                )
            }
    } else {
        // if (AuthKeys.tokenSignAlg == "RS256"){
        val verified = JWKKey.importJWK(AuthKeys.tokenKey.decodeToString()).getOrThrow().verifyJws(token)
        return runCatching { verified }
            .mapCatching {
                if (verified.isSuccess) {
                    Json.parseToJsonElement(jwsObject.payload.toString()).jsonObject["sub"]?.jsonPrimitive?.content.toString()
                } else throw IllegalArgumentException("Token is not valid.")
            }
    }

}


suspend fun PipelineContext<Unit, ApplicationCall>.doLogin() {
    val reqBody = loginRequestJson.decodeFromString<AccountRequest>(call.receive())
    AccountsService.authenticate("", reqBody).onSuccess {
        val now = Clock.System.now().toJavaInstant()
        val tokenPayload = Json.encodeToString(
            AuthTokenPayload(
                jti = UUID.generateUUID().toString(),
                sub = it.id.toString(),
                iss = AuthKeys.issTokenClaim,
                aud = AuthKeys.audTokenClaim.takeIf { !it.isNullOrEmpty() }
                    ?: let { call.request.headers["Origin"] ?: "n/a" },
                iat = now.epochSecond,
                nbf = now.epochSecond,
                exp = now.plus(AuthKeys.tokenLifetime, ChronoUnit.DAYS).epochSecond,
            )
        )

        val token = JWKKey.importJWK(AuthKeys.tokenKey.decodeToString()).getOrNull()?.let {
            createRsaToken(it, tokenPayload)
        } ?: createHS256Token(tokenPayload)
        call.sessions.set(LoginTokenSession(token))
        call.response.status(HttpStatusCode.OK)
        call.respond(
            Json.encodeToJsonElement(it).jsonObject.minus("type").plus(Pair("token", token.toJsonElement()))
        )
    }.onFailure { call.respond(HttpStatusCode.BadRequest, it.localizedMessage) }
}

private fun PipelineContext<Unit, ApplicationCall>.clearUserSession() {
    call.sessions.get<LoginTokenSession>()?.let {
        logger.debug { "Clearing login token session" }
        call.sessions.clear<LoginTokenSession>()
    }

    call.sessions.get<OidcTokenSession>()?.let {
        logger.debug { "Clearing OIDC token token session" }
        call.sessions.clear<OidcTokenSession>()
    }
}

fun PipelineContext<Unit, ApplicationCall>.getUserId() =
    call.principal<UserIdPrincipal>("auth-session")
        ?: call.principal<UserIdPrincipal>("auth-bearer")
        ?: call.principal<UserIdPrincipal>("auth-bearer-alternative")
        ?: call.principal<UserIdPrincipal>() // bearer is registered with no name for some reason
        ?: throw UnauthorizedException("Could not find user authorization within request.")

fun PipelineContext<Unit, ApplicationCall>.getUserUUID() =
    runCatching { UUID(getUserId().name) }
        .getOrElse { throw IllegalArgumentException("Invalid user id: $it") }

fun PipelineContext<Unit, ApplicationCall>.getWalletId() =
    runCatching {
        UUID(call.parameters["wallet"] ?: throw IllegalArgumentException("No wallet ID provided"))
    }.getOrElse { throw IllegalArgumentException("Invalid wallet ID provided: ${it.message}") }
        .also {
            ensurePermissionsForWallet(AccountWalletPermissions.READ_ONLY, walletId = it)
        }

fun PipelineContext<Unit, ApplicationCall>.getWalletService(walletId: UUID) =
    WalletServiceManager.getWalletService("", getUserUUID(), walletId) // FIXME -> TENANT HERE

fun PipelineContext<Unit, ApplicationCall>.getWalletService() =
    WalletServiceManager.getWalletService("", getUserUUID(), getWalletId()) // FIXME -> TENANT HERE

fun PipelineContext<Unit, ApplicationCall>.getUsersSessionToken(): String? =
    call.sessions.get(LoginTokenSession::class)?.token
        ?: call.request.authorization()?.removePrefix("Bearer ")

fun PipelineContext<Unit, ApplicationCall>.ensurePermissionsForWallet(
    required: AccountWalletPermissions,

    userId: UUID = getUserUUID(),
    walletId: UUID = getWalletId(),
): Boolean {


    val permissions = transaction {
        (AccountWalletMappings.selectAll()
            .where {
                (AccountWalletMappings.tenant eq "") and // FIXME -> TENANT HERE
                        (AccountWalletMappings.accountId eq userId) and
                        (AccountWalletMappings.wallet eq walletId)
            }
            .firstOrNull()
            ?: throw ForbiddenException("This account does not have access to the specified wallet."))[
            AccountWalletMappings.permissions]
    }

    if (permissions.power >= required.power) {
        return true
    } else {
        throw InsufficientPermissionsException(minimumRequired = required, current = permissions)
    }
}

private fun createHS256Token(tokenPayload: String) = JWSObject(JWSHeader(JWSAlgorithm.HS256), Payload(tokenPayload)).apply {
    sign(MACSigner(AuthKeys.tokenKey))
}.serialize()

private suspend fun createRsaToken(key: JWKKey, tokenPayload: String) =
    mapOf(JWTClaims.Header.keyID to key.getPublicKey().getKeyId(), JWTClaims.Header.type to "JWT").let {
        key.signJws(tokenPayload.toByteArray(), it)
    }


@Serializable
private data class AuthTokenPayload<T>(
    val nbf: Long,
    val exp: Long,
    val iat: Long,
    val jti: String,
    val iss: String,
    val aud: String,
    val sub: T,
)
