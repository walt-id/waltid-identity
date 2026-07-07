package id.walt.webwallet.web.controllers.auth.oidc

import id.walt.webwallet.service.WalletServiceManager.oidcConfig
import id.walt.webwallet.web.controllers.auth.LogoutControllerBase
import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.github.smiley4.ktoropenapi.get
import io.ktor.server.response.*
import io.ktor.server.routing.*

class OidcLogoutController : LogoutControllerBase() {
    override fun apiBuilder(): RouteConfig.() -> Unit = { description = "Logout via OIDC provider" }

    override fun routes(name: String): Route.() -> Route = {
        get(name, apiBuilder()) { execute() }
    }

    override suspend fun RoutingContext.execute() {
        clearUserSession()
        call.respondRedirect("${oidcConfig.logoutUrl}?post_logout_redirect_uri=${oidcConfig.publicBaseUrl}&client_id=${oidcConfig.clientId}")
    }
}
