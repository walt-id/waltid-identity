package id.walt.webwallet.web.controllers

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.JWSObject
import com.nimbusds.jose.Payload
import com.nimbusds.jose.crypto.MACSigner
import com.nimbusds.jose.crypto.MACVerifier
import id.walt.commons.config.ConfigManager
import id.walt.commons.featureflag.FeatureManager
import id.walt.commons.web.ForbiddenException
import id.walt.commons.web.UnauthorizedException
import id.walt.commons.web.WebException
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.JsonUtils.toJsonElement
import id.walt.oid4vc.definitions.JWTClaims
import id.walt.webwallet.FeatureCatalog
import id.walt.webwallet.config.AuthConfig
import id.walt.webwallet.db.models.Account
import id.walt.webwallet.db.models.AccountWalletMappings
import id.walt.webwallet.db.models.AccountWalletPermissions
import id.walt.webwallet.service.WalletServiceManager
import id.walt.webwallet.service.WalletServiceManager.oidcConfig
import id.walt.webwallet.service.account.AccountsService
import id.walt.webwallet.service.account.KeycloakAccountStrategy
import id.walt.webwallet.web.InsufficientPermissionsException
import id.walt.webwallet.web.WebBaseRoutes.webWalletRoute
import id.walt.webwallet.web.model.*
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.smiley4.ktorswaggerui.dsl.routing.get
import io.github.smiley4.ktorswaggerui.dsl.routing.post
import io.github.smiley4.ktorswaggerui.dsl.routing.route
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.sessions.*
import io.ktor.util.pipeline.*
import kotlinx.datetime.Clock
import kotlinx.datetime.toJavaInstant
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import kotlinx.uuid.UUID
import kotlinx.uuid.generateUUID
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.temporal.ChronoUnit

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

                if (FeatureManager.isFeatureEnabled(FeatureCatalog.oidcAuthenticationFeature)) {
                    authenticate("auth-oauth-jwt") {
                        get("oidc-session", { description = "Configure OIDC session" }) {
                            val principal: OAuthAccessTokenResponse.OAuth2 =
                                call.principal() ?: error("No OAuth principal")

                            call.sessions.set(OidcTokenSession(principal.accessToken))

                            call.respondRedirect("/login?oidc_login=true")
                        }
                    }
                }
            }

            get("oidc-token", { description = "Returns OIDC token" }) {
                val oidcSession = call.sessions.get<OidcTokenSession>() ?: error("No OIDC session")

                call.respond(oidcSession.token)
            }

            rateLimit(RateLimitName("login")) {

                post(
                    "login",
                    {
                        summary =
                            "Login with [email + password] or [wallet address + ecosystem] or [oidc session]"
                        request {
                            body<AccountRequest> {
                                example("E-mail + password") {
                                    value = EmailAccountRequest(
                                        email = "user@email.com",
                                        password = "password"
                                    )
                                }
                                example("Wallet address + ecosystem") {
                                    value = AddressAccountRequest(
                                        address = "0xABC",
                                        ecosystem = "ecosystem"
                                    )
                                }
                                example("OIDC") { value = OidcAccountRequest(token = "ey...") }
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
            }

            post(
                "create",
                {
                    summary = "Register with [email + password] or [wallet address + ecosystem]"
                    request {
                        body<AccountRequest> {
                            example("E-mail + password") {
                                value = EmailAccountRequest(
                                    name = "Max Mustermann",
                                    email = "user@email.com",
                                    password = "password"
                                )
                            }
                            example("Wallet address + ecosystem") {
                                value = AddressAccountRequest(address = "0xABC", ecosystem = "ecosystem")
                            }
                            example("OIDC") { value = OidcAccountRequest(token = "ey...") }
                            example("OIDC Unique Subject") {
                                value = OidcUniqueSubjectRequest(token = "ey...")
                            }
                            example("Keycloak") { value = KeycloakAccountRequest() }
                        }
                    }
                    response {
                        HttpStatusCode.Created to { description = "Registration succeeded " }
                        HttpStatusCode.BadRequest to { description = "Registration failed" }
                        HttpStatusCode.Conflict to { description = "Account already exists!" }
                    }
                }) {
                val jsonObject = call.receive<JsonObject>()
                val type = jsonObject["type"]?.jsonPrimitive?.contentOrNull
                if (type.isNullOrEmpty()) {
                    throw BadRequestException("No account type provided")
                }
                val accountRequest = loginRequestJson.decodeFromJsonElement<AccountRequest>(jsonObject)
                AccountsService.register("", accountRequest)
                    .onSuccess {
                        call.response.status(HttpStatusCode.Created)
                        call.respond("Registration succeeded ")
                    }
                    .onFailure {
                        throw it
                    }
            }

            authenticate("auth-session", "auth-bearer", "auth-bearer-alternative") {
                get("user-info", {
                    summary = "Return user ID if logged in"
                    response {
                        HttpStatusCode.OK to {
                            body<Account>()
                        }
                    }
                }) {
                    getUsersSessionToken()?.run {
                        val jwsObject = JWSObject.parse(this)
                        val uuid =
                            Json.parseToJsonElement(jwsObject.payload.toString()).jsonObject["sub"]?.jsonPrimitive?.content.toString()
                        call.respond(AccountsService.get(UUID(uuid)))
                    } ?: call.respond(HttpStatusCode.BadRequest)
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
                call.respondRedirect(
                    "${oidcConfig.logoutUrl}?post_logout_redirect_uri=${oidcConfig.publicBaseUrl}&client_id=${oidcConfig.clientId}"
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
                val accessToken = KeycloakAccountStrategy.getAccessToken()
                call.respond(accessToken)
            }

            // Create Keycloak User
            post(
                "create",
                {
                    summary = "Keycloak registration with [username + email + password]"
                    description = "Creates a user in the configured Keycloak instance."
                    request {
                        body<AccountRequest> {
                            example("username + email + password") {
                                value = KeycloakAccountRequest(
                                    username = "Max_Mustermann",
                                    email = "user@email.com",
                                    password = "password",
                                    token = "eyJhb..."
                                )
                            }
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
            rateLimit(RateLimitName("login")) {
                post("login", {
                    summary = "Keycloak login with [username + password]"
                    description = "Login of a user managed by Keycloak."
                    request {
                        body<AccountRequest> {
                            example("Keycloak username + password") {
                                value = KeycloakAccountRequest(
                                    username = "Max_Mustermann",
                                    password = "password"
                                )
                            }
                            example("Keycloak username + Access Token ") {
                                value = KeycloakAccountRequest(
                                    username = "Max_Mustermann",
                                    token = "eyJhb..."
                                )
                            }

                            example("Keycloak user Access Token ") {
                                value = KeycloakAccountRequest(
                                    token = "eyJhb..."
                                )
                            }
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
                            example("keycloakUserId + token") {
                                value = KeycloakLogoutRequest(
                                    keycloakUserId = "3d09 ...",
                                    token = "eyJhb ..."
                                )
                            }
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
    return if (key == null) {
        val verifier = MACVerifier(AuthKeys.tokenKey)
        runCatching { jwsObject.verify(verifier) }
            .mapCatching { valid ->
                if (valid) Json.parseToJsonElement(jwsObject.payload.toString()).jsonObject["sub"]?.jsonPrimitive?.content.toString() else throw IllegalArgumentException(
                    "Token is not valid."
                )
            }
    } else {
        val verified = JWKKey.importJWK(AuthKeys.tokenKey.decodeToString()).getOrThrow().verifyJws(token)
        runCatching { verified }
            .mapCatching {
                if (verified.isSuccess) {
                    Json.parseToJsonElement(jwsObject.payload.toString()).jsonObject["sub"]?.jsonPrimitive?.content.toString()
                } else throw IllegalArgumentException("Token is not valid.")
            }
    }
}

data class LoginRequestError(override val message: String) : WebException(
    message = message,
    status = HttpStatusCode.BadRequest
) {
    constructor(throwable: Throwable) : this(
        when (throwable) {
            is BadRequestException -> "Error processing request: ${throwable.localizedMessage ?: "Unknown reason"}"
            is SerializationException -> "Failed to parse JSON string: ${throwable.localizedMessage ?: "Unknown reason"}"
            is IllegalStateException -> "Invalid request: ${throwable.localizedMessage ?: "Unknown reason"}"
            else -> "Unexpected error: ${throwable.localizedMessage ?: "Unknown reason"}"
        }
    )
}

suspend fun ApplicationCall.getLoginRequest() = runCatching {
    val jsonObject = receive<JsonObject>()
    val accountType = jsonObject["type"]?.jsonPrimitive?.contentOrNull
    if (accountType.isNullOrEmpty()) {
        throw BadRequestException(
            if (jsonObject.containsKey("type")) {
                "Account type '${jsonObject["type"]}' is not recognized"
            } else {
                "No account type provided"
            }
        )
    }
    val json = Json { ignoreUnknownKeys = true }
    json.decodeFromJsonElement<AccountRequest>(jsonObject)
}.getOrElse { throw LoginRequestError(it) }


suspend fun PipelineContext<Unit, ApplicationCall>.doLogin() {
    val reqBody = call.getLoginRequest()
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
    }.onFailure {
        throw BadRequestException(it.localizedMessage)
    }
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

private fun createHS256Token(tokenPayload: String) =
    JWSObject(JWSHeader(JWSAlgorithm.HS256), Payload(tokenPayload)).apply {
        sign(MACSigner(AuthKeys.tokenKey))
    }.serialize()

private suspend fun createRsaToken(key: JWKKey, tokenPayload: String) =
    mapOf(JWTClaims.Header.keyID to key.getPublicKey().getKeyId().toJsonElement(), JWTClaims.Header.type to "JWT".toJsonElement()).let {
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
