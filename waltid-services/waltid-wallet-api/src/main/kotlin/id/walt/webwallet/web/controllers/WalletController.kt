package id.walt.webwallet.web.controllers

import id.walt.webwallet.web.WebBaseRoutes.authenticatedWebWalletRoute
import io.github.smiley4.ktoropenapi.route
import id.walt.webwallet.web.WebBaseRoutes.webWalletRoute
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


fun Application.unprotectedWalletRoute(build: Route.() -> Unit) {
    webWalletRoute {
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