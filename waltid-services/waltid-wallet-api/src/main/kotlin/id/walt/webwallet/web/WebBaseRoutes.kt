package id.walt.webwallet.web

import id.walt.webwallet.web.plugins.authConfigNames
import io.github.smiley4.ktoropenapi.route
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*

object WebBaseRoutes {

    private fun Route.routedWebWalletRoute(block: Route.() -> Unit) =
        route("wallet-api", {

        }) {
            block.invoke(this)
        }

    fun Application.webWalletRoute(block: Route.() -> Unit) = routing {
        routedWebWalletRoute(block)
    }

    fun Application.authenticatedWebWalletRoute(block: Route.() -> Unit) = routing {
        authenticate(*authConfigNames) {
            routedWebWalletRoute(block)
        }
    }
}


