package id.walt.webwallet.web.controllers

import id.walt.webwallet.db.models.WalletCredential
import id.walt.webwallet.service.report.CredentialReportRequestParameter
import io.github.smiley4.ktorswaggerui.dsl.get
import io.github.smiley4.ktorswaggerui.dsl.route
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*

fun Application.reports() = walletRoute {
    route("reports", {
        tags = listOf("WalletReports")
    }) {
        route("frequent", {
            summary = "List most frequently used"
            request {
                queryParameter<Int>("limit") {
                    description = "The max number of items to return"
                    required = false
                }
            }
        }) {
            get("credentials", {
                summary = "Credentials"
                response {
                    HttpStatusCode.OK to {
                        body<List<WalletCredential>> {
                            description = "The list of the most frequently used credentials"
                        }
                    }
                }
            }) {
                val limit = call.request.queryParameters["limit"]?.toIntOrNull()
                context.respond(
                    getWalletService().getFrequentCredentials(
                        CredentialReportRequestParameter(
                            walletId = getWalletId(), limit = limit
                        )
                    )
                )
            }
        }
    }
}
