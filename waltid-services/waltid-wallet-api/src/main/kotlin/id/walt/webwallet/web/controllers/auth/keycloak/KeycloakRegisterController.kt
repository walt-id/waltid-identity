package id.walt.webwallet.web.controllers.auth.keycloak

import id.walt.webwallet.web.controllers.auth.RegisterControllerBase
import id.walt.webwallet.web.model.AccountRequest
import id.walt.webwallet.web.model.KeycloakAccountRequest
import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.ktor.http.*

class KeycloakRegisterController : RegisterControllerBase(keycloakAuthPath, keycloakAuthTags) {
    override fun apiBuilder(): RouteConfig.() -> Unit = {
        summary = "Keycloak registration with [username + email + password]"
        description = "Creates a user in the configured Keycloak instance."
        request {
            body<AccountRequest> {
                required = true
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
    }
}
