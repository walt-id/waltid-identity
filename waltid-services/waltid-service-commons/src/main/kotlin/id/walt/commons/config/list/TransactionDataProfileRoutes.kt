package id.walt.commons.config.list

import io.github.smiley4.ktoropenapi.delete
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.github.smiley4.ktoropenapi.put
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.util.getOrFail

fun Route.registerTransactionDataProfileCrudRoutes() {
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

    post("transaction-data-profiles", {
        tags = listOf("Transaction Data")
        summary = "Create a transaction data type profile"
        request { body<TransactionDataProfile>() }
        response {
            HttpStatusCode.Created to { body<TransactionDataProfile>() }
            HttpStatusCode.Conflict to { description = "Profile type already exists" }
        }
    }) {
        val created = TransactionDataProfileService.create(call.receive())
        call.respond(HttpStatusCode.Created, created)
    }

    put("transaction-data-profiles/{type}", {
        tags = listOf("Transaction Data")
        summary = "Replace a transaction data type profile"
        request {
            pathParameter<String>("type") { description = "Transaction data type identifier" }
            body<TransactionDataProfile>()
        }
        response {
            HttpStatusCode.OK to { body<TransactionDataProfile>() }
            HttpStatusCode.NotFound to { description = "Profile not found" }
        }
    }) {
        val type = call.parameters.getOrFail("type")
        call.respond(TransactionDataProfileService.replace(type, call.receive()))
    }

    delete("transaction-data-profiles/{type}", {
        tags = listOf("Transaction Data")
        summary = "Delete a transaction data type profile"
        request { pathParameter<String>("type") { description = "Transaction data type identifier" } }
        response {
            HttpStatusCode.NoContent to { description = "Profile deleted" }
            HttpStatusCode.NotFound to { description = "Profile not found" }
        }
    }) {
        TransactionDataProfileService.delete(call.parameters.getOrFail("type"))
        call.respond(HttpStatusCode.NoContent)
    }
}
