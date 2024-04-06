package id.walt.webwallet.web.controllers

import id.walt.webwallet.service.credentials.RejectionReasonService
import id.walt.webwallet.web.WebBaseRoutes.authenticatedWebWalletRoute
import io.github.smiley4.ktorswaggerui.dsl.get
import io.github.smiley4.ktorswaggerui.dsl.route
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*

fun Application.reasons() = authenticatedWebWalletRoute {
    route("reasons", {
        tags = listOf("Rejection Reasons")
    }) {
        get({
            summary = "Get the list of available reasons"
            response {
                HttpStatusCode.OK to {
                    body<List<String>> {
                        description = "The list of available reasons"
                    }
                }
            }
        }) {
            val service = RejectionReasonService()
            context.respond(service.list())
        }
    }
}