package id.walt.webwallet.web.controllers

import id.walt.webwallet.service.settings.WalletSetting
import io.github.smiley4.ktorswaggerui.dsl.get
import io.github.smiley4.ktorswaggerui.dsl.post
import io.github.smiley4.ktorswaggerui.dsl.route
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import kotlinx.serialization.json.JsonObject

fun Application.settings() = walletRoute {
    route("settings", {
        tags = listOf("Settings")
    }) {
        get({
            summary = "Wallet settings"
            response {
                HttpStatusCode.OK to {
                    description = "Wallet settings object"
                    body<WalletSetting>()
                }
                HttpStatusCode.BadRequest to { description = "Error fetching wallet settings" }
            }
        }) {
            runCatching { getWalletService().getSettings() }.onSuccess {
                context.respond(HttpStatusCode.OK, it)
            }.onFailure {
                context.respond(HttpStatusCode.BadRequest, it.localizedMessage)
            }
        }
        //put
        post({
            summary = "Update wallet settings"
            request {
                body<JsonObject> { description = "Wallet setting object" }
            }
            response {
                HttpStatusCode.Created to { description = "Wallet settings updated successfully" }
                HttpStatusCode.BadRequest to { description = "Error updating wallet settings" }
            }
        }) {
            runCatching {
                val request = call.receive<JsonObject>()
                getWalletService().setSettings(request)
            }.onSuccess {
                context.respond(HttpStatusCode.Accepted)
            }.onFailure {
                context.respond(HttpStatusCode.BadRequest, it.localizedMessage)
            }
        }
    }
}