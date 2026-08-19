package id.walt.commons.config.list

import io.github.smiley4.ktoropenapi.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.util.getOrFail

fun Route.registerTransactionDataProfileDiscoveryRoutes() {
    get("transaction-data-profiles", {
        tags = listOf("Transaction Data")
        summary = "List available transaction data type profiles"
        response { HttpStatusCode.OK to { body<List<TransactionDataProfile>>() } }
    }) {
        call.respond(TransactionDataProfileService.list())
    }

    get("transaction-data-profiles/{type}", {
        tags = listOf("Transaction Data")
        summary = "Get a transaction data type profile"
        request { pathParameter<String>("type") { description = "Transaction data type identifier" } }
        response {
            HttpStatusCode.OK to { body<TransactionDataProfile>() }
            HttpStatusCode.NotFound to { description = "Profile not found" }
        }
    }) {
        call.respond(TransactionDataProfileService.get(call.parameters.getOrFail("type")))
    }
}
