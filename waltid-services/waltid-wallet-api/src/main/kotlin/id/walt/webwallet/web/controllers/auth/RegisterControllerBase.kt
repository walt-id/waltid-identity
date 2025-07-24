package id.walt.webwallet.web.controllers.auth

import id.walt.webwallet.service.account.AccountsService
import id.walt.webwallet.web.model.*
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.github.smiley4.ktoropenapi.post
import io.github.smiley4.ktoropenapi.route
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

abstract class RegisterControllerBase(
    private val path: String = defaultAuthPath,
    private val tagList: List<String> = defaultAuthTags,
) : Controller {
    protected val logger = KotlinLogging.logger {}

    override fun routes(name: String): Route.() -> Route = {
        route(path, { tags = tagList }) {
            post(name, apiBuilder()) { execute() }
        }
    }

    override fun apiBuilder(): RouteConfig.() -> Unit = {
        summary = "Register with [email + password] or [wallet address + ecosystem]"
        request {
            body<AccountRequest> {
                required = true
                example("E-mail + password") {
                    value = EmailAccountRequest(
                        name = "Max Mustermann",
                        email = "user@email.com",
                        password = "password"
                    )
                }
                example("Wallet address + ecosystem") {
                    value = AddressAccountRequest(address = "0xABC", ecosystem = "ecosystem")
                }
                example("OIDC") { value = OidcAccountRequest(token = "ey...") }
                example("OIDC Unique Subject") {
                    value = OidcUniqueSubjectRequest(token = "ey...")
                }
                example("Keycloak") { value = KeycloakAccountRequest() }
            }
        }
        response {
            HttpStatusCode.Created to { description = "Registration succeeded " }
            HttpStatusCode.BadRequest to { description = "Registration failed" }
            HttpStatusCode.Conflict to { description = "Account already exists!" }
        }
    }

    override suspend fun RoutingContext.execute() {
        val req = call.receive<AccountRequest>()
        logger.debug { "Creating ${req.javaClass.simpleName} user" }
        AccountsService.register("", req)
            .onSuccess {
                call.response.status(HttpStatusCode.Created)
                call.respond("Registration succeeded ")
            }
            .onFailure {
                throw it
            }
    }
}
