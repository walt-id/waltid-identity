package id.walt.webwallet.web.controllers.auth.x5c

import id.walt.webwallet.web.controllers.auth.LoginControllerBase
import id.walt.webwallet.web.model.AccountRequest
import id.walt.webwallet.web.model.X5CAccountRequest
import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.ktor.http.*

class X5CLoginController : LoginControllerBase(x5cAuthPath, x5cAuthTags) {
    override fun apiBuilder(): RouteConfig.() -> Unit = {
        summary = "X5C login with X.509 certificates"
        description = "Login of a user using X.509-related JWS header"
        request {
            body<AccountRequest> {
                example("x5c Trusted CA Certificate Chain") {
                    value = X5CAccountRequest(token = "ey...")
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
