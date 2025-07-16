@file:OptIn(ExperimentalUuidApi::class)

package id.walt.webwallet.web.controllers

import id.walt.webwallet.db.models.WalletCredential
import id.walt.webwallet.service.report.CredentialReportRequestParameter
import id.walt.webwallet.web.controllers.auth.getWalletId
import id.walt.webwallet.web.controllers.auth.getWalletService
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.route
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import kotlin.uuid.ExperimentalUuidApi

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
                        description = "The list of the most frequently used credentials"
                        body<List<WalletCredential>> {}
                    }
                }
            }) {
                val limit = call.request.queryParameters["limit"]?.toIntOrNull()
                call.respond(
                    call.getWalletService().getFrequentCredentials(
                        CredentialReportRequestParameter(
                            walletId = call.getWalletId(), limit = limit
                        )
                    )
                )
            }
        }
    }
}
