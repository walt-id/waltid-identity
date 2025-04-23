package id.walt.webwallet.web.controllers

import id.walt.webwallet.service.WalletServiceManager
import id.walt.webwallet.web.WebBaseRoutes.webWalletRoute
import io.github.smiley4.ktoropenapi.post
import io.github.smiley4.ktoropenapi.route
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.util.*

fun Application.silentExchange() = webWalletRoute {
    route("api", {
        tags = listOf("Silent Exchange")
    }) {
        post("useOfferRequest/{did}", {
            summary = "Silently claim credentials"
            request {
                pathParameter<String>("did") { description = "The DID to issue the credential(s) to" }
                body<String> {
                    description = "The offer request to use"
                }
            }
        }) {
            val did = call.parameters.getOrFail("did")
            val offer = call.receiveText()

            runCatching {
                WalletServiceManager.silentClaimStrategy.claim(did, offer)
            }.onSuccess {
                call.respond(HttpStatusCode.Accepted, it.size)
            }.onFailure {
                call.respond(HttpStatusCode.BadRequest, it.localizedMessage)
            }
        }
    }
}
