package id.walt.webwallet.web.controllers

import id.walt.web.controllers.getWalletService
import id.walt.webwallet.db.models.WalletCredential
import io.github.smiley4.ktorswaggerui.dsl.get
import io.github.smiley4.ktorswaggerui.dsl.post
import io.github.smiley4.ktorswaggerui.dsl.put
import io.github.smiley4.ktorswaggerui.dsl.route
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import kotlinx.serialization.json.JsonObject

fun Application.credentials() = walletRoute {
    route("credentials", {
        tags = listOf("WalletCredentials")
    }) {
        get({
            summary = "List credentials"
            request {
                queryParameter<List<String>>("category"){
                    description = "the category name"
                    example = "my-category"
                    required = false
                }
            }
            response {
                HttpStatusCode.OK to {
                    description = "Array of (verifiable credentials) JSON documents"
                    body<List<JsonObject>>()
                }
            }
        }) {
            val categories = call.request.queryParameters.getAll("category") ?: emptyList()
            context.respond(getWalletService().listCredentials())
        }

        put({
            summary = "Store credential"
        }) {
            TODO()
        }

        route("{credentialId}", {
            request {
                pathParameter<String>("credentialId") {
                    description = "the credential id (or alias)"
                    example = "urn:uuid:d36986f1-3cc0-4156-b5a4-6d3deab84270"
                }
            }
        }) {
            get({
                summary = "View a credential"
                response {
                    HttpStatusCode.OK to {
                        body<WalletCredential> {
                            description =
                                "WalletCredential in JWT (String starting with 'ey' or JSON_LD (JSON with proof) format"
                        }
                    }
                }
            }) {
                val credentialId = enforceGetParameter("credentialId", call.parameters)
                context.respond(getWalletService().getCredential(credentialId))
            }

            post({
                summary = "Delete a credential"

                response {
                    HttpStatusCode.Accepted to { description = "WalletCredential deleted" }
                    HttpStatusCode.BadRequest to { description = "WalletCredential could not be deleted" }
                }
            }) {
                val credentialId = enforceGetParameter("credentialId", call.parameters)
                val success = getWalletService().deleteCredential(credentialId)
                context.respond(if (success) HttpStatusCode.Accepted else HttpStatusCode.BadRequest)
            }
            route("category/{category}",{
                request {
                    pathParameter<String>("category") {
                        description = "the category name"
                        example = "my-category"
                    }
                }
            }){
                post("add",{
                    summary = "Attach category to a credential"

                    response {
                        HttpStatusCode.Created to { description = "WalletCredential category added" }
                        HttpStatusCode.BadRequest to { description = "WalletCredential category could not be added" }
                    }
                }){
                    val credentialId = enforceGetParameter("credentialId", call.parameters)
                    val category = enforceGetParameter("category", call.parameters)
                    runCatching { getWalletService().attachCategory(credentialId, category) }.onSuccess {
                        if (it) context.respond(HttpStatusCode.Created) else context.respond(HttpStatusCode.BadRequest)
                    }.onFailure { context.respond(HttpStatusCode.BadRequest, it.localizedMessage) }
                }
                post("delete",{
                    summary = "Detach category from credential"

                    response {
                        HttpStatusCode.Accepted to { description = "WalletCredential category deleted" }
                        HttpStatusCode.BadRequest to { description = "WalletCredential category could not be deleted" }
                    }
                }){
                    val credentialId = enforceGetParameter("credentialId", call.parameters)
                    val category = enforceGetParameter("category", call.parameters)
                    runCatching { getWalletService().attachCategory(credentialId, category) }.onSuccess {
                        if (it) context.respond(HttpStatusCode.Accepted) else context.respond(HttpStatusCode.BadRequest)
                    }.onFailure { context.respond(HttpStatusCode.BadRequest, it.localizedMessage) }
                }
            }
        }
    }
}

internal fun enforceGetParameter(name: String, parameters: Parameters): String =
    parameters[name] ?: throw IllegalArgumentException("No $name provided")