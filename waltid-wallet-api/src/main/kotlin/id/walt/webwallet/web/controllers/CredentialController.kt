package id.walt.webwallet.web.controllers

import id.walt.webwallet.db.models.WalletCredential
import id.walt.webwallet.service.WalletServiceManager
import id.walt.webwallet.service.credentials.CredentialFilterObject
import id.walt.webwallet.web.parameter.CredentialRequestParameter
import id.walt.webwallet.web.parameter.NoteRequestParameter
import io.github.smiley4.ktorswaggerui.dsl.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
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
                queryParameter<List<String>>("category") {
                    description = "list of category names"
                    required = false
                }
                queryParameter<Boolean>("showDeleted") {
                    description = "include the deleted credentials in the query result"
                    example = false
                    required = false
                }
                queryParameter<Boolean>("showPending") {
                    description = "include the pending credentials in the query result"
                    example = false
                    required = false
                }
                queryParameter<String>("sortBy") {
                    description = "The property to sort by"
                    example = "addedOn"
                    required = false
                }
                queryParameter<Boolean>("descending") {
                    description = "Sort descending"
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
            val showDeleted = call.request.queryParameters["showDeleted"]?.toBooleanStrictOrNull()
            val showPending = call.request.queryParameters["showPending"]?.toBooleanStrictOrNull()
            val sortBy = call.request.queryParameters["sortBy"] ?: "addedOn"
            val descending = call.request.queryParameters["descending"].toBoolean()
            context.respond(
                getWalletService().listCredentials(
                    CredentialFilterObject(
                        categories = categories,
                        showDeleted = showDeleted,
                        showPending = showPending,
                        sortBy = sortBy,
                        sorDescending = descending
                    )
                )
            )
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
            delete({
                summary = "Delete a credential"
                request {
                    queryParameter<Boolean>("permanent") {
                        description = "Permanently delete the credential"
                        example = false
                        required = false
                    }
                }
                response {
                    HttpStatusCode.Accepted to { description = "WalletCredential deleted" }
                    HttpStatusCode.BadRequest to { description = "WalletCredential could not be deleted" }
                }
            }) {
                val credentialId = call.parameters.getOrFail("credentialId")
                val permanent = call.request.queryParameters["permanent"].toBoolean()
                context.respond(
                    if (getWalletService().deleteCredential(
                            credentialId, permanent
                        )
                    ) HttpStatusCode.Accepted else HttpStatusCode.BadRequest
                )
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
            }) {
                val credentialId = call.parameters.getOrFail("credentialId")
                runCatching { getWalletService().restoreCredential(credentialId) }.onSuccess {
                    context.respond(HttpStatusCode.OK, it)
                }.onFailure {
                    context.respond(HttpStatusCode.BadRequest, it.localizedMessage)
                }
            }
            post("accept", {
                summary = "Accept credential"
                response {
                    HttpStatusCode.Accepted to { description = "Credential accepted successfully" }
                    HttpStatusCode.BadRequest to { description = "Credential acceptance failed" }
                }
            }) {
                val credentialId = call.parameters.getOrFail("credentialId")
                runCatching { getWalletService().acceptCredential(CredentialRequestParameter(credentialId)) }.onSuccess {
                    if (it) context.respond(HttpStatusCode.Accepted) else context.respond(HttpStatusCode.BadRequest)
                }.onFailure {
                    context.respond(HttpStatusCode.BadRequest, it.localizedMessage)
                }
            }
            post("reject", {
                summary = "Reject credential"
                request {
                    body<NoteRequestParameter> {
                        description = "Request data"
                        required = false
                        example("Note", NoteRequestParameter("note"))
                    }
                }
                response {
                    HttpStatusCode.Accepted to { description = "Credential rejected successfully" }
                    HttpStatusCode.BadRequest to { description = "Credential rejection failed" }
                }
            }) {
                val credentialId = call.parameters.getOrFail("credentialId")
                val requestParameter = call.receiveNullable<NoteRequestParameter>()
                runCatching {
                    getWalletService().rejectCredential(
                        CredentialRequestParameter(
                            credentialId = credentialId, parameter = requestParameter
                        )
                    )
                }.onSuccess {
                    if (it) context.respond(HttpStatusCode.Accepted) else context.respond(HttpStatusCode.BadRequest)
                }.onFailure {
                    context.respond(HttpStatusCode.BadRequest, it.localizedMessage)
                }
            }
            get("status", {
                summary = "Get credential status"
                response {
                    HttpStatusCode.OK to { body<String> { description = "Credential status" } }
                    HttpStatusCode.BadRequest to {
                        description =
                            "Credential status could not be established or an error occured"
                    }
                }
            }) {
                runCatching {
                    val credentialId = call.parameters.getOrFail("credentialId")
                    WalletServiceManager.credentialStatusUseCase.get(getWalletId(), credentialId)
                }.onSuccess {
                    context.respond(it)
                }.onFailure {
                    context.respond(HttpStatusCode.BadRequest, it.localizedMessage)
                }
            }
            route("category", {
                request {
                    body<List<String>> {
                        description = "The list of category names"
                        required = true
                    }
                }
            }) {
                put({
                    summary = "Attach category to a credential"

                    response {
                        HttpStatusCode.Created to { description = "WalletCredential category added" }
                        HttpStatusCode.BadRequest to { description = "WalletCredential category could not be added" }
                    }
                }) {
                    val credentialId = call.parameters.getOrFail("credentialId")
                    val categories = call.receive<List<String>>()
                    runCatching { getWalletService().attachCategory(credentialId, categories) }.onSuccess {
                        if (it) context.respond(HttpStatusCode.Created) else context.respond(HttpStatusCode.BadRequest)
                    }.onFailure { context.respond(HttpStatusCode.BadRequest, it.localizedMessage) }
                }
                delete({
                    summary = "Detach category from credential"

                    response {
                        HttpStatusCode.Accepted to { description = "WalletCredential category deleted" }
                        HttpStatusCode.BadRequest to { description = "WalletCredential category could not be deleted" }
                    }
                }) {
                    val credentialId = call.parameters.getOrFail("credentialId")
                    val categories = call.receive<List<String>>()
                    runCatching { getWalletService().detachCategory(credentialId, categories) }.onSuccess {
                        if (it) context.respond(HttpStatusCode.Accepted) else context.respond(HttpStatusCode.BadRequest)
                    }.onFailure { context.respond(HttpStatusCode.BadRequest, it.localizedMessage) }
                }
            }
        }
    }
}
