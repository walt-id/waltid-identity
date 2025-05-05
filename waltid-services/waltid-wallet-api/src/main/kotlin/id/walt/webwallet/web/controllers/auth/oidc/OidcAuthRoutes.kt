package id.walt.webwallet.web.controllers.auth.oidc

import id.walt.webwallet.web.WebBaseRoutes.webWalletRoute
import id.walt.webwallet.web.controllers.auth.OidcTokenSession
import id.walt.webwallet.web.controllers.auth.defaultAuthPath
import id.walt.webwallet.web.controllers.auth.defaultAuthTags
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.route
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.sessions.*

fun Application.oidcAuthRoutes() = webWalletRoute {
    route(defaultAuthPath, { tags = defaultAuthTags }) {
        authenticate("auth-oauth") {
            get(
                "oidc-login",
                {
                    description = "Redirect to OIDC provider for login"
                    response { HttpStatusCode.Found }
                }) {
                call.respondRedirect("oidc-session")
            }
        }
        authenticate("auth-oauth-jwt") {
            get("oidc-session", { description = "Configure OIDC session" }) {
                val principal: OAuthAccessTokenResponse.OAuth2 =
                    call.principal() ?: error("No OAuth principal")

                call.sessions.set(OidcTokenSession(principal.accessToken))

                call.respondRedirect("/login?oidc_login=true")
            }
        }

        get("oidc-token", { description = "Returns OIDC token" }) {
            val oidcSession = call.sessions.get<OidcTokenSession>() ?: error("No OIDC session")

            call.respond(oidcSession.token)
        }
    }
    OidcLogoutController().routes("logout-oidc")
}
