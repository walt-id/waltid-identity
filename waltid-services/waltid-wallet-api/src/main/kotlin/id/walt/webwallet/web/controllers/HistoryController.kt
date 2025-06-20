@file:OptIn(ExperimentalUuidApi::class)

package id.walt.webwallet.web.controllers

import id.walt.webwallet.db.models.WalletOperationHistory
import id.walt.webwallet.web.controllers.auth.getWalletService
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.route
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.uuid.ExperimentalUuidApi

fun Application.history() = walletRoute {
    route("history", {
        tags = listOf("History")
    }) {
        get({
            summary = "Show operation history"
            response {
                HttpStatusCode.OK to {
                    description = "Operation history"
                    body<List<WalletOperationHistory>>()
                }
            }
        }) {
            val wallet = call.getWalletService()
            call.respond(transaction {
                wallet.getHistory()
            })
        }
    }
}
