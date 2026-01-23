@file:OptIn(ExperimentalUuidApi::class, ExperimentalTime::class)

package id.walt.webwallet.web.controllers.auth

import id.walt.commons.web.UnauthorizedException
import id.walt.ktorauthnz.KtorAuthnzManager
import id.walt.ktorauthnz.accounts.identifiers.methods.AccountIdentifier
import id.walt.ktorauthnz.accounts.identifiers.methods.EmailIdentifier
import id.walt.ktorauthnz.auth.ExternallyProvidedJWTCannotResolveToAuthenticatedSession
import id.walt.ktorauthnz.auth.getAuthenticatedAccount
import id.walt.ktorauthnz.auth.getAuthenticatedSession
import id.walt.ktorauthnz.auth.getEffectiveRequestAuthToken
import id.walt.ktorauthnz.methods.OIDC
import id.walt.ktorauthnz.methods.sessiondata.OidcSessionAuthenticatedData
import id.walt.ktorauthnz.methods.storeddata.AuthMethodStoredData
import id.walt.ktorauthnz.methods.storeddata.EmailPassStoredData
import id.walt.ktorauthnz.sessions.SessionManager
import id.walt.ktorauthnz.sessions.SessionTokenCookieHandler
import id.walt.webwallet.db.models.Account
import id.walt.webwallet.db.models.Accounts
import id.walt.webwallet.service.account.AccountsService
import id.walt.webwallet.web.plugins.KTOR_AUTHNZ_CONFIG_NAME
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.github.smiley4.ktoropenapi.route
import io.klogging.logger
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.util.*
import io.ktor.util.date.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.toJavaInstant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private val logger = logger("AuthnzFrontendController")

private suspend fun ktorAuthnzCreateAccount(
    method: String,
    identifier: AccountIdentifier,
    storedData: AuthMethodStoredData? = null,
    name: String,
    email: String?
): Uuid {
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


    KtorAuthnzManager.accountStore.addAccountIdentifierToAccount(createdAccountId.toString(), identifier)

    if (storedData != null) {
        KtorAuthnzManager.accountStore.addAccountStoredData(createdAccountId.toString(), method, storedData)
    }

    AccountsService.initializeUserAccount(tenant = "", name = name, registeredUserId = createdAccountId)

    return createdAccountId
}

@Serializable
data class RegisterCall(
    val name: String,
    val email: String,
    val password: String,
    val type: String
)

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


                get("oidc-callback-frontend") {
                    // Going from ktor-authnz to Web wallet

                    // just check if already registered here
                    val session = getAuthenticatedSession() // should work

                    val oidcData = session.getSessionData<OidcSessionAuthenticatedData>(OIDC)
                    requireNotNull(oidcData) { "Missing OIDC Authenticated Data" }

                    val oidcIdentifier = oidcData.oidcIdentifier

                    val accountId = oidcIdentifier.resolveIfExists()

                    if (accountId == null) {
                        // Have to create account (first login with this OIDC issuer/subject combination)
                        val createdAccountId = ktorAuthnzCreateAccount(
                            method = "email",
                            identifier = oidcIdentifier,
                            name = oidcIdentifier.subject,
                            email = null
                        )
                        session.accountId = createdAccountId.toString()
                        SessionManager.updateSession(session)
                    }

                    val newUrl = call.url { this.path("/") }
                    call.respondRedirect(newUrl)
                }


            }

            // Unauthenticated endpoint follow here:

            post("login", {
                description = "Fake login for frontend"
            }) {
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

            post("register", {
               request { body<RegisterCall>()  }
            }) {
                logger.trace { "Fake register called" }
                val registerData = call.receive<RegisterCall>()
                val name = registerData.name
                val email = registerData.email
                val password = registerData.password
                val type = registerData.type

                require(type == "email") { "Only implemented for email login" }

                val createdAccountId = ktorAuthnzCreateAccount(
                    method = "email",
                    identifier = EmailIdentifier(email),
                    storedData = EmailPassStoredData(password = password),
                    name = name,
                    email = email
                )

                call.respond(
                    buildJsonObject {
                        put("id", JsonPrimitive(createdAccountId.toString()))
                    }
                )
            }

            post("logout") {
                /* Requires an authenticate block:
                 runCatching {
                    val session = getAuthenticatedSession()
                    session.run { call.logoutAndDeleteCookie() }
                }*/

                // After here is just for backup...

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
