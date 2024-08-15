package id.walt.webwallet.web.controllers.auth.x5c

import id.walt.webwallet.web.controllers.auth.RegisterControllerBase
import id.walt.webwallet.web.model.AccountRequest
import io.github.smiley4.ktorswaggerui.dsl.routes.OpenApiRoute
import io.ktor.http.*

class X5CRegisterController : RegisterControllerBase(x5cAuthPath, x5cAuthTags) {
    override fun apiBuilder(): OpenApiRoute.() -> Unit = {
        summary = "X5C registration with X.509 certificates"
        description = "Creates a user using X.509-related JWS header."
        request {
            body<AccountRequest> {
                //todo: create examples
            }
        }
        response {
            HttpStatusCode.Created to { description = "Registration succeeded " }
            HttpStatusCode.BadRequest to { description = "Registration failed" }
        }
    }
}