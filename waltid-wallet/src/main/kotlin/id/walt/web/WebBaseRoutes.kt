package id.walt.web

import io.github.smiley4.ktorswaggerui.dsl.route
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
        authenticate("authenticated-session", "authenticated-bearer") {
            routedWebWalletRoute(block)
        }
    }
}


