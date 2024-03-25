package id.walt.webwallet.web.controllers

import id.walt.webwallet.web.WebBaseRoutes.webWalletRoute
import io.github.smiley4.ktorswaggerui.dsl.get
import io.github.smiley4.ktorswaggerui.dsl.route
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*

fun Application.health() = webWalletRoute {
    route("healthz", {
        tags = listOf("ServiceHealth")
    }) {
        get({
            summary = "Service health status"
            response {
                HttpStatusCode.OK to {
                    description = "Service health status"
                }
            }
        }) {
            context.respond(HttpStatusCode.OK)
        }
    }
}