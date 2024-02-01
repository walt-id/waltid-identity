package id.walt.webwallet.web.controllers

import id.walt.web.controllers.getWalletService
import id.walt.webwallet.db.models.WalletCredential
import io.github.smiley4.ktorswaggerui.dsl.get
import io.github.smiley4.ktorswaggerui.dsl.route
import io.ktor.http.*
import io.ktor.server.application.*

fun Application.reports() = walletRoute {
    route("reports", {
        tags = listOf("WalletReports")
    }){
        route("frequent", {
            summary = "List most frequent"
            request {
                queryParameter<Int>("limit") {
                    description = "The max number of items to return"
                    required = false
                }
            }
        }){
            get({
                summary = "View a credential"
                response {
                    HttpStatusCode.OK to {
                        body<List<WalletCredential>> {
                            description = "The list of the most frequently used credentials"
                        }
                    }
                }
            }) {
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: -1
                getWalletService()
                //todo
            }
        }
    }
}