@file:OptIn(ExperimentalUuidApi::class, ExperimentalTime::class)

package id.walt.webwallet.web.controllers.auth

import id.walt.commons.web.UnauthorizedException
import id.walt.ktorauthnz.KtorAuthnzManager
import id.walt.ktorauthnz.accounts.identifiers.methods.EmailIdentifier
import id.walt.ktorauthnz.auth.ExternallyProvidedJWTCannotResolveToAuthenticatedSession
import id.walt.ktorauthnz.auth.getAuthenticatedAccount
import id.walt.ktorauthnz.auth.getAuthenticatedSession
import id.walt.ktorauthnz.auth.getEffectiveRequestAuthToken
import id.walt.ktorauthnz.methods.storeddata.EmailPassStoredData
import id.walt.ktorauthnz.sessions.SessionTokenCookieHandler
import id.walt.webwallet.db.models.Account
import id.walt.webwallet.db.models.Accounts
import id.walt.webwallet.service.account.AccountsService
import id.walt.webwallet.web.plugins.KTOR_AUTHNZ_CONFIG_NAME
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.route
import io.klogging.logger
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.date.*
import kotlinx.serialization.json.*
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.toJavaInstant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private val logger = logger("AuthnzFrontendController")

@OptIn(ExternallyProvidedJWTCannotResolveToAuthenticatedSession::class)
fun Application.ktorAuthnzFrontendRoutes() {
    routing {
        route("/wallet-api/auth", {
            tags("Frontend authentication")
        }) {
            authenticate(KTOR_AUTHNZ_CONFIG_NAME) {
                get("user-info", {
                    summary = "Return user ID if logged in"
                    response {
                        HttpStatusCode.OK to {
                            description = "User account"
                            body<Account>()
                        }
                    }
                }) {
                    call.respond(call.getAuthenticatedAccount())
                }
                get("session", { summary = "Return session ID if logged in" }) {
                    val token = getAuthenticatedSession().token ?: throw UnauthorizedException("Invalid session")
                    call.respond(mapOf("token" to mapOf("accessToken" to token)))
                }
            }

            post("login") {
                val providedToken = call.receiveText()
                logger.trace { "Provided token: $providedToken" }

                val (account, token) = if (providedToken.isNotEmpty()) {
                    val token =
                        Json.decodeFromString<JsonObject>(providedToken)["token"]?.jsonPrimitive?.content ?: error("Missing token")
                    val session = KtorAuthnzManager.tokenHandler.resolveTokenToSession(token)
                    val account = session.accountId
                    val sessionToken = session.token
                    account to sessionToken

                } else {
                    val authenticatedAccount = call.getAuthenticatedAccount()
                    val authenticatedSessionToken = getAuthenticatedSession().token
                    authenticatedAccount to authenticatedSessionToken
                }

                call.respond(
                    buildJsonObject {
                        put("id", account)
                        put("token", token)
                    }
                )
            }

            post("register") {
                logger.trace { "Fake register called" }
                val registerData = call.receive<JsonObject>()
                val name = registerData["name"]?.jsonPrimitive?.content ?: throw IllegalArgumentException("Missing name")
                val email = registerData["email"]?.jsonPrimitive?.content ?: throw IllegalArgumentException("Missing email")
                val password = registerData["password"]?.jsonPrimitive?.content ?: throw IllegalArgumentException("Missing password")
                val type = registerData["type"]?.jsonPrimitive?.content ?: throw IllegalArgumentException("Missing type")

                require(type == "email") { "Only implemented for email login" }

                val createdAccountId = transaction {
                    Accounts.insert {
                        it[Accounts.tenant] = ""
                        it[id] = Uuid.random()
                        it[Accounts.name] = name
                        it[Accounts.email] = email
                        it[Accounts.password] = "!AUTHNZ ACCOUNT"
                        it[createdOn] = Clock.System.now().toJavaInstant()
                    }[Accounts.id]
                }


                KtorAuthnzManager.accountStore.addAccountIdentifierToAccount(createdAccountId.toString(), EmailIdentifier(email))
                KtorAuthnzManager.accountStore.addAccountStoredData(
                    createdAccountId.toString(),
                    "email",
                    EmailPassStoredData(password = password)
                )

                AccountsService.initializeUserAccount(tenant = "", name = name, registeredUserId = createdAccountId)


                call.respond(
                    buildJsonObject {
                        put("id", JsonPrimitive(createdAccountId.toString()))
                    }
                )
            }

            post("logout") {

                val token = call.getEffectiveRequestAuthToken()
                if (token != null) {
                    // Invalidate token
                    runCatching { KtorAuthnzManager.tokenHandler.getTokenSessionId(token) }.onSuccess { sessionId ->
                        KtorAuthnzManager.sessionStore.dropSession(sessionId)
                    }
                    KtorAuthnzManager.tokenHandler.dropToken(token)
                }

                // Delete cookies
                SessionTokenCookieHandler.run { call.deleteCookie() }
                call.response.cookies.append("auth.token", "", CookieEncoding.URI_ENCODING, 0L, GMTDate())

                call.respond(HttpStatusCode.OK)
            }
        }
    }
}
