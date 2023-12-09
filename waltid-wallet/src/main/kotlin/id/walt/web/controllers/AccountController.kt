package id.walt.web.controllers

import id.walt.db.models.AccountWalletListing
import id.walt.service.account.AccountsService
import id.walt.web.WebBaseRoutes.authenticatedWebWalletRoute
import io.github.smiley4.ktorswaggerui.dsl.get
import io.github.smiley4.ktorswaggerui.dsl.route
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*

fun Application.accounts() {
    authenticatedWebWalletRoute {
        route("wallet") {
            route("accounts", {
                tags = listOf("Accounts")
            }) {
                get("wallets", {
                    summary = "Get wallets associated with account"
                    response { HttpStatusCode.OK to { body<AccountWalletListing>() } }
                }) {
                    val user = getUserUUID()
                    context.respond(AccountsService.getAccountWalletMappings(user))
                }
            }
        }
    }
}
