package id.walt.webwallet.web.controllers

import id.walt.commons.config.ConfigManager
import id.walt.verifier.openid.transactiondata.TransactionDataProfile
import id.walt.verifier.openid.transactiondata.TransactionDataProfilesConfig
import id.walt.webwallet.web.WebBaseRoutes.webWalletRoute
import io.github.smiley4.ktoropenapi.get
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*

fun Application.transactionDataProfiles() {
    webWalletRoute {
        get("transaction-data-profiles", {
            tags = listOf("Transaction Data")
            summary = "List available transaction data type profiles"
            response { HttpStatusCode.OK to { body<List<TransactionDataProfile>>() } }
        }) {
            val config = ConfigManager.getConfig<TransactionDataProfilesConfig>()
            call.respond(config.transactionDataProfiles)
        }
    }
}
