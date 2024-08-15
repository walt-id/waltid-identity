package id.walt.webwallet.web.controllers.auth

import id.walt.webwallet.web.model.AccountRequest
import id.walt.webwallet.web.model.AddressAccountRequest
import id.walt.webwallet.web.model.EmailAccountRequest
import id.walt.webwallet.web.model.OidcAccountRequest
import io.github.smiley4.ktorswaggerui.dsl.routes.OpenApiRoute
import io.github.smiley4.ktorswaggerui.dsl.routing.post
import io.github.smiley4.ktorswaggerui.dsl.routing.route
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.routing.*
import io.ktor.util.pipeline.*

abstract class LoginControllerBase(
    private val path: String = defaultAuthPath,
    private val tagList: List<String> = defaultAuthTags,
) : Controller {

    override fun routes(
        name: String,
    ): Route.() -> Route = {
        route(path, { tags = tagList }) {
            rateLimit(RateLimitName(name)) {
                post(name, apiBuilder()) { execute() }
            }
        }
    }

    override fun apiBuilder(): OpenApiRoute.() -> Unit = {
        summary = "Login with [email + password] or [wallet address + ecosystem] or [oidc session]"
        request {
            body<AccountRequest> {
                example("E-mail + password") {
                    value = EmailAccountRequest(
                        email = "user@email.com", password = "password"
                    )
                }
                example("Wallet address + ecosystem") {
                    value = AddressAccountRequest(
                        address = "0xABC", ecosystem = "ecosystem"
                    )
                }
                example("OIDC") { value = OidcAccountRequest(token = "ey...") }
            }
        }
        response {
            HttpStatusCode.OK to { description = "Login successful" }
            HttpStatusCode.Unauthorized to { description = "Login failed" }
            HttpStatusCode.BadRequest to { description = "Login failed" }
        }
    }

    override suspend fun PipelineContext<Unit, ApplicationCall>.execute() {
        doLogin()
    }
}