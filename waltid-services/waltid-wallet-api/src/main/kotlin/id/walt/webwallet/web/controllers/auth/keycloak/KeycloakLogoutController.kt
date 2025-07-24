package id.walt.webwallet.web.controllers.auth.keycloak

import id.walt.webwallet.service.account.KeycloakAccountStrategy
import id.walt.webwallet.web.controllers.auth.LogoutControllerBase
import id.walt.webwallet.web.model.KeycloakLogoutRequest
import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json

class KeycloakLogoutController : LogoutControllerBase(keycloakAuthPath, keycloakAuthTags) {
    override fun apiBuilder(): RouteConfig.() -> Unit = {
        summary = "Logout via Keycloak provider."
        description =
            "Terminates Keycloak and wallet session by the user identified by the Keycloak user ID."
        request {
            body<KeycloakLogoutRequest> {
                required = true
                example("keycloakUserId + token") {
                    value = KeycloakLogoutRequest(
                        keycloakUserId = "3d09 ...",
                        token = "eyJhb ..."
                    )
                }
            }
        }
        response { HttpStatusCode.OK to { description = "Keycloak HTTP status code." } }
    }

    override suspend fun RoutingContext.execute() {
        clearUserSession()
        logger.debug { "Clearing Keycloak user session" }
        val req = Json.decodeFromString<KeycloakLogoutRequest>(call.receive())
        call.respond("Keycloak responded with: ${KeycloakAccountStrategy.logout(req)}")
    }
}
