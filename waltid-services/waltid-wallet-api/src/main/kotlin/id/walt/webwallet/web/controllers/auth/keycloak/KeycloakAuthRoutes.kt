package id.walt.webwallet.web.controllers.auth.keycloak

import id.walt.webwallet.service.account.KeycloakAccountStrategy
import id.walt.webwallet.web.WebBaseRoutes.webWalletRoute
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.route
import io.ktor.server.application.*
import io.ktor.server.response.*

const val keycloakAuthPath = "auth/keycloak"
val keycloakAuthTags = listOf("Keycloak Authentication")
private val logger = KotlinLogging.logger {}

fun Application.keycloakAuthRoutes() = webWalletRoute {
    route(keycloakAuthPath, { tags = keycloakAuthTags }) {
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
    }
    KeycloakLoginController().routes("login").invoke(this)
    KeycloakRegisterController().routes("create")(this)
    KeycloakLogoutController().routes("logout")(this)
}
