@file:OptIn(ExperimentalUuidApi::class)

package id.walt.webwallet.web.controllers

import id.walt.webwallet.db.models.WalletCredential
import id.walt.webwallet.service.WalletServiceManager
import id.walt.webwallet.service.credentials.CredentialFilterObject
import id.walt.webwallet.usecase.credential.CredentialStatusResult
import id.walt.webwallet.web.controllers.auth.getWalletId
import id.walt.webwallet.web.controllers.auth.getWalletService
import id.walt.webwallet.web.parameter.CredentialRequestParameter
import id.walt.webwallet.web.parameter.NoteRequestParameter
import io.github.smiley4.ktoropenapi.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.util.*
import kotlin.uuid.ExperimentalUuidApi

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
                    required = false
                }
                queryParameter<Boolean>("showPending") {
                    description = "include the pending credentials in the query result"
                    required = false
                }
                queryParameter<String>("sortBy") {
                    description = "The property to sort by"
                    example("Example") { value = "addedOn" }
                    required = false
                }
                queryParameter<Boolean>("descending") {
                    description = "Sort descending"
                    required = false
                }
            }
            response {
                HttpStatusCode.OK to {
                    description = "Array of (verifiable credentials) JSON documents"
                    body<List<WalletCredential>>()
                }
            }
        }) {
            val categories = call.request.queryParameters.getAll("category")
            val showDeleted = call.request.queryParameters["showDeleted"]?.toBooleanStrictOrNull()
            val showPending = call.request.queryParameters["showPending"]?.toBooleanStrictOrNull()
            val sortBy = call.request.queryParameters["sortBy"] ?: "addedOn"
            val descending = call.request.queryParameters["descending"].toBoolean()
            call.respond(
                call.getWalletService().listCredentials(
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
                    example("Example") { value = "urn:uuid:d36986f1-3cc0-4156-b5a4-6d3deab84270" }
                }
            }
        }) {
            get({
                summary = "View a credential"
                response {
                    HttpStatusCode.OK to {
                        description = "WalletCredential in JWT (String starting with 'ey' or JSON_LD (JSON with proof) format"
                        body<WalletCredential> {}
                    }
                }
            }) {
                val credentialId = call.parameters.getOrFail("credentialId")
                call.respond(call.getWalletService().getCredential(credentialId))
            }
            delete({
                summary = "Delete a credential"
                request {
                    queryParameter<Boolean>("permanent") {
                        description = "Permanently delete the credential"
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
                call.respond(
                    if (call.getWalletService().deleteCredential(
                            credentialId, permanent
                        )
                    ) HttpStatusCode.Accepted else HttpStatusCode.BadRequest
                )
            }
            post("restore", {
                summary = "Attempt to restore a soft delete credential"
                response {
                    HttpStatusCode.OK to {
                        description = "WalletCredential in JWT (String starting with 'ey' or JSON_LD (JSON with proof) format"
                        body<WalletCredential> {}
                    }
                    HttpStatusCode.BadRequest to { description = "WalletCredential could not be restored" }
                }
            }) {
                val credentialId = call.parameters.getOrFail("credentialId")
                runCatching { call.getWalletService().restoreCredential(credentialId) }.onSuccess {
                    call.respond(HttpStatusCode.OK, it)
                }.onFailure {
                    throw it
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
                runCatching { call.getWalletService().acceptCredential(CredentialRequestParameter(credentialId)) }.onSuccess {
                    if (it) call.respond(HttpStatusCode.Accepted) else call.respond(HttpStatusCode.BadRequest)
                }.onFailure {
                    throw it
                }
            }
            post("reject", {
                summary = "Reject credential"
                request {
                    body<NoteRequestParameter> {
                        description = "Request data"
                        required = false
                        example("Note") { value = NoteRequestParameter("note") }
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
                    call.getWalletService().rejectCredential(
                        CredentialRequestParameter(
                            credentialId = credentialId, parameter = requestParameter
                        )
                    )
                }.onSuccess {
                    if (it) call.respond(HttpStatusCode.Accepted) else call.respond(HttpStatusCode.BadRequest)
                }.onFailure {
                    call.respond(HttpStatusCode.BadRequest, it.localizedMessage)
                }
            }
            get("status", {
                summary = "Get credential status"
                response {
                    HttpStatusCode.OK to {
                        description = "List of credential statuses"
                        body<List<CredentialStatusResult>>()
                    }
                    HttpStatusCode.BadRequest to {
                        description =
                            "Credential status could not be established or an error occured"
                    }
                }
            }) {
                runCatching {
                    val credentialId = call.parameters.getOrFail("credentialId")
                    WalletServiceManager.credentialStatusUseCase.get(call.getWalletId(), credentialId)
                }.onSuccess {
                    call.respond(it)
                }.onFailure {
                    call.respond(HttpStatusCode.BadRequest, it.localizedMessage)
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
                    runCatching { call.getWalletService().attachCategory(credentialId, categories) }.onSuccess {
                        if (it) call.respond(HttpStatusCode.Created) else call.respond(HttpStatusCode.BadRequest)
                    }.onFailure { call.respond(HttpStatusCode.BadRequest, it.localizedMessage) }
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
                    runCatching { call.getWalletService().detachCategory(credentialId, categories) }.onSuccess {
                        if (it) call.respond(HttpStatusCode.Accepted) else call.respond(HttpStatusCode.BadRequest)
                    }.onFailure { call.respond(HttpStatusCode.BadRequest, it.localizedMessage) }
                }
            }
        }
    }
}
