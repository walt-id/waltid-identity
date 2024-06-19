package id.walt.webwallet.web.controllers

import id.walt.webwallet.db.models.WalletOperationHistory
import io.github.smiley4.ktorswaggerui.dsl.routing.get
import io.github.smiley4.ktorswaggerui.dsl.routing.route
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import org.jetbrains.exposed.sql.transactions.transaction

fun Application.history() = walletRoute {
    route("history", {
        tags = listOf("History")
    }) {
        get({
            summary = "Show operation history"
            response {
                HttpStatusCode.OK to {
                    body<List<WalletOperationHistory>>()
                }
            }
        }) {
            val wallet = getWalletService()
            context.respond(transaction {
                wallet.getHistory()
            })
        }
    }
}
