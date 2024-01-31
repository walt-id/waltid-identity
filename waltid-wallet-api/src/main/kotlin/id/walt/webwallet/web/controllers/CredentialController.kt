package id.walt.webwallet.web.controllers

import id.walt.web.controllers.getWalletService
import id.walt.webwallet.db.models.WalletCredential
import id.walt.webwallet.service.WalletService
import id.walt.webwallet.service.credentials.CredentialFilterObject
import io.github.smiley4.ktorswaggerui.dsl.get
import io.github.smiley4.ktorswaggerui.dsl.post
import io.github.smiley4.ktorswaggerui.dsl.put
import io.github.smiley4.ktorswaggerui.dsl.route
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.util.*
import kotlinx.serialization.json.JsonObject

fun Application.credentials() = walletRoute {
    route("credentials", {
        tags = listOf("WalletCredentials")
    }) {
        get({
            summary = "List credentials"
            request {
                queryParameter<List<String>>("category"){
                    description = "list of category names"
                    required = false
                }
                queryParameter<Boolean>("showDeleted"){
                    description = "include the deleted credentials in the query result"
                    example = false
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
            val categories = call.request.queryParameters.getAll("category")
            val showDeleted = call.request.queryParameters["showDeleted"].toBoolean()
            context.respond(getWalletService().listCredentials(CredentialFilterObject(categories, showDeleted)))
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
                val credentialId = call.parameters.getOrFail("credentialId")
                context.respond(getWalletService().getCredential(credentialId))
            }
            route("delete"){
                post("hard", {
                    summary = "Permanently delete a credential"

                    response {
                        HttpStatusCode.Accepted to { description = "WalletCredential deleted permanently" }
                        HttpStatusCode.BadRequest to { description = "WalletCredential could not be deleted" }
                    }
                }) {
                    context.respond(
                        if (deleteCredential(
                                getWalletService(),
                                call.parameters,
                                true
                            )
                        ) HttpStatusCode.Accepted else HttpStatusCode.BadRequest
                    )
                }
                post("soft", {
                    summary = "Temporarily delete a credential"

                    response {
                        HttpStatusCode.Accepted to { description = "WalletCredential deleted temporarily" }
                        HttpStatusCode.BadRequest to { description = "WalletCredential could not be deleted" }
                    }
                }){
                    context.respond(
                        if (deleteCredential(
                                getWalletService(),
                                call.parameters,
                                false
                            )
                        ) HttpStatusCode.Accepted else HttpStatusCode.BadRequest
                    )
                }
            }
            post("restore", {
                summary = "Attempt to restore a soft delete credential"
                response {
                    HttpStatusCode.OK to {
                        body<WalletCredential> {
                            description =
                                "WalletCredential in JWT (String starting with 'ey' or JSON_LD (JSON with proof) format"
                        }
                    }
                    HttpStatusCode.BadRequest to { description = "WalletCredential could not be restored" }
                }
            }){
                val credentialId = call.parameters.getOrFail("credentialId")
                runCatching { getWalletService().restoreCredential(credentialId) }.onSuccess {
                    context.respond(HttpStatusCode.OK, it)
                }.onFailure {
                    context.respond(HttpStatusCode.BadRequest, it.localizedMessage)
                }
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
                    val credentialId = call.parameters.getOrFail("credentialId")
                    val category = call.parameters.getOrFail("category")
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
                    val credentialId = call.parameters.getOrFail("credentialId")
                    val category = call.parameters.getOrFail("category")
                    runCatching { getWalletService().detachCategory(credentialId, category) }.onSuccess {
                        if (it) context.respond(HttpStatusCode.Accepted) else context.respond(HttpStatusCode.BadRequest)
                    }.onFailure { context.respond(HttpStatusCode.BadRequest, it.localizedMessage) }
                }
            }
        }
    }
}

internal suspend fun deleteCredential(service: WalletService, parameters: Parameters, permanent: Boolean): Boolean {
    val credentialId = parameters.getOrFail("credentialId")
    return service.deleteCredential(credentialId, permanent)
}