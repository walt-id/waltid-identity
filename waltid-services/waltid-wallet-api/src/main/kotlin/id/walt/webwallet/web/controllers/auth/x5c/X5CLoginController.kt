package id.walt.webwallet.web.controllers.auth.x5c

import id.walt.webwallet.web.controllers.auth.LoginControllerBase
import id.walt.webwallet.web.model.AccountRequest
import io.github.smiley4.ktorswaggerui.dsl.routes.OpenApiRoute
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.util.pipeline.*

class X5CLoginController : LoginControllerBase(x5cAuthPath, x5cAuthTags) {
    override fun apiBuilder(): OpenApiRoute.() -> Unit = {
        summary = "X5C login with X.509 certificates"
        description = "Login of a user using X.509-related JWS header"
        request {
            body<AccountRequest> {
                //todo: create examples
            }
        }
        response {
            HttpStatusCode.OK to { description = "Login successful" }
            HttpStatusCode.Unauthorized to { description = "Unauthorized" }
            HttpStatusCode.BadRequest to { description = "Bad request" }
        }
    }

    override suspend fun PipelineContext<Unit, ApplicationCall>.execute() {
        TODO("Not yet implemented")
    }
}