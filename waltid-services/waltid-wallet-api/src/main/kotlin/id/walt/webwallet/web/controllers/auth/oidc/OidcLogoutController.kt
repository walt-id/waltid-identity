package id.walt.webwallet.web.controllers.auth.oidc

import id.walt.webwallet.service.WalletServiceManager.oidcConfig
import id.walt.webwallet.web.controllers.auth.LogoutControllerBase
import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.ktor.server.response.*
import io.ktor.server.routing.*

class OidcLogoutController : LogoutControllerBase() {
    override fun apiBuilder(): RouteConfig.() -> Unit = { description = "Logout via OIDC provider" }
    override suspend fun RoutingContext.execute() {
        call.respondRedirect("${oidcConfig.logoutUrl}?post_logout_redirect_uri=${oidcConfig.publicBaseUrl}&client_id=${oidcConfig.clientId}")
    }
}
