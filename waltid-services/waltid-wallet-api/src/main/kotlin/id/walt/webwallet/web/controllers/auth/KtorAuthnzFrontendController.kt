package id.walt.webwallet.web.controllers.auth

import id.walt.commons.web.UnauthorizedException
import id.walt.ktorauthnz.KtorAuthnzManager
import id.walt.ktorauthnz.auth.getAuthenticatedAccount
import id.walt.ktorauthnz.auth.getAuthenticatedSession
import id.walt.webwallet.db.models.Account
import id.walt.webwallet.web.plugins.KTOR_AUTHNZ_CONFIG_NAME
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.route
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.date.*
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
                println("providedToken: $providedToken")

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

            post("logout") {
                call.response.cookies.append("ktor-authnz-auth", "", CookieEncoding.URI_ENCODING, 0L, GMTDate())
                call.response.cookies.append("auth.token", "", CookieEncoding.URI_ENCODING, 0L, GMTDate())

                call.respond(HttpStatusCode.OK)
            }
        }
    }
}
