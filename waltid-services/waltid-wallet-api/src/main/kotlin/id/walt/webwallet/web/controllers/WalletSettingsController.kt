@file:OptIn(ExperimentalUuidApi::class)

package id.walt.webwallet.web.controllers

import id.walt.webwallet.service.settings.WalletSetting
import id.walt.webwallet.web.controllers.auth.getWalletService
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.github.smiley4.ktoropenapi.route
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import kotlinx.serialization.json.JsonObject
import kotlin.uuid.ExperimentalUuidApi

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
            runCatching { call.getWalletService().getSettings() }.onSuccess {
                call.respond(HttpStatusCode.OK, it)
            }.onFailure {
                call.respond(HttpStatusCode.BadRequest, it.localizedMessage)
            }
        }
        //put
        post({
            summary = "Update wallet settings"
            request {
                body<JsonObject> {
                    required = true
                    description = "Wallet setting object"
                }
            }
            response {
                HttpStatusCode.Created to { description = "Wallet settings updated successfully" }
                HttpStatusCode.BadRequest to { description = "Error updating wallet settings" }
            }
        }) {
            runCatching {
                val request = call.receive<JsonObject>()
                call.getWalletService().setSettings(request)
            }.onSuccess {
                call.respond(HttpStatusCode.Accepted)
            }.onFailure {
                call.respond(HttpStatusCode.BadRequest, it.localizedMessage)
            }
        }
    }
}
