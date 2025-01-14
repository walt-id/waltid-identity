package id.walt.webwallet.web.controllers.auth

import id.walt.commons.web.UnauthorizedException
import id.walt.ktorauthnz.KtorAuthnzManager
import id.walt.ktorauthnz.auth.getAuthenticatedAccount
import id.walt.ktorauthnz.auth.getAuthenticatedSession
import id.walt.webwallet.db.models.Account
import id.walt.webwallet.web.plugins.KTOR_AUTHNZ_CONFIG_NAME
import io.github.smiley4.ktorswaggerui.dsl.routing.get
import io.github.smiley4.ktorswaggerui.dsl.routing.route
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.*

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
                            body<Account>()
                        }
                    }
                }) {
                    call.respond(getAuthenticatedAccount())
                }
                get("session", { summary = "Return session ID if logged in" }) {
                    val token = getAuthenticatedSession().token ?: throw UnauthorizedException("Invalid session")
                    call.respond(mapOf("token" to mapOf("accessToken" to token)))
                }
            }

            post("login") { // also in authenticate {} block as it just relays authnz auth
                //call.sessions.set(LoginTokenSession(token))

                val providedToken = call.receiveText()
                println("providedToken: $providedToken")

                val (account, token) = if (providedToken.isNotEmpty()) {
                    val token = Json.decodeFromString<JsonObject>(providedToken)["token"]?.jsonPrimitive?.content ?: error("Missing token")
                    val session = KtorAuthnzManager.tokenHandler.resolveTokenToSession(token)
                    val account = session.accountId
                    val sessionToken = session.token
                    account to sessionToken

                } else {
                    val authenticatedAccount = getAuthenticatedAccount()
                    val authenticatedSessionToken = getAuthenticatedSession().token
                    authenticatedAccount to authenticatedSessionToken
                }

                context.respond(
                    buildJsonObject {
                        put("id", account)
                        put("token", token)
                    }
                )
            }
        }
    }
}
