package id.walt.web.controllers

import id.walt.web.WebBaseRoutes.authenticatedWebWalletRoute
import io.github.smiley4.ktorswaggerui.dsl.route
import io.ktor.server.application.*
import io.ktor.server.routing.*

fun Application.walletRoute(build: Route.() -> Unit) {
    authenticatedWebWalletRoute {
        route("wallet/{wallet}", {
            request {
                pathParameter<String>("wallet") {
                    required = true
                    allowEmptyValue = false
                    description = "Wallet ID"
                }
            }
            // tags = listOf("wallet")
        }) {
            build.invoke(this)
        }
    }
}

