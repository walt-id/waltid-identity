package id.walt.webwallet.web.controllers.auth.keycloak

import id.walt.webwallet.web.controllers.auth.LoginControllerBase
import id.walt.webwallet.web.model.AccountRequest
import id.walt.webwallet.web.model.KeycloakAccountRequest
import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.ktor.http.*

class KeycloakLoginController : LoginControllerBase(keycloakAuthPath, keycloakAuthTags) {

    override fun apiBuilder(): RouteConfig.() -> Unit = {
        summary = "Keycloak login with [username + password]"
        description = "Login of a user managed by Keycloak."
        request {
            body<AccountRequest> {
                required = true
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
    }
}
