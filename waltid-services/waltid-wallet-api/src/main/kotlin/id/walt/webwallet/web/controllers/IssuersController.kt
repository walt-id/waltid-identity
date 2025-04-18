@file:OptIn(ExperimentalUuidApi::class)

package id.walt.webwallet.web.controllers

import id.walt.webwallet.service.WalletServiceManager
import id.walt.webwallet.service.issuers.CredentialDataTransferObject
import id.walt.webwallet.service.issuers.IssuerDataTransferObject
import id.walt.webwallet.web.controllers.auth.getWalletService
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.github.smiley4.ktoropenapi.put
import io.github.smiley4.ktoropenapi.route
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.util.*
import kotlinx.serialization.Serializable
import kotlin.uuid.ExperimentalUuidApi

fun Application.issuers() = walletRoute {
    route("issuers", {
        tags = listOf("Issuers")
    }) {
        get({
            summary = "List the configured issuers"
            response {
                HttpStatusCode.OK to {
                    description = "Array of issuer objects"
                    body<List<IssuerDataTransferObject>>()
                }
            }
        }) {
            call.respond(WalletServiceManager.issuerUseCase.list(call.getWalletService().walletId))
        }
        post("add", {
            summary = "Add issuer to wallet"
            request {
                body<IssuerParameter> {
                    description = "Issuer data"
                    required = true
                }
            }
            response {
                HttpStatusCode.Created to { description = "Issuer added successfully" }
                HttpStatusCode.BadRequest to { description = "Failed to add issuer to wallet" }
            }
        }) {
            val issuer = call.receive<IssuerParameter>()
            WalletServiceManager.issuerUseCase.add(
                IssuerDataTransferObject(
                    wallet = call.getWalletService().walletId,
                    did = issuer.name,
                    description = issuer.description,
                    uiEndpoint = issuer.uiEndpoint,
                    configurationEndpoint = issuer.configurationEndpoint,
                )
            ).onSuccess {
                call.respond(HttpStatusCode.Created)
            }.onFailure {
                call.respond(HttpStatusCode.BadRequest, it.localizedMessage)
            }
        }
        route("{issuer}", {
            request {
                pathParameter<String>("issuer") {
                    description = "The issuer name"
                    example("walt.id") { value = "walt.id" }
                }
            }
        }) {
            get({
                summary = "Fetch issuer data"

                response {
                    HttpStatusCode.OK to {
                        description = "Issuer data object"
                        body<IssuerDataTransferObject>()
                    }
                    HttpStatusCode.NotFound to {
                        description = "Error message"
                        body<String>()
                    }
                }
            }) {
                WalletServiceManager.issuerUseCase.get(call.getWalletService().walletId, call.parameters.getOrFail("issuer")).onSuccess {
                    call.respond(it)
                }.onFailure {
                    call.respondText(it.localizedMessage, ContentType.Text.Plain, HttpStatusCode.NotFound)
                }
            }
            put("authorize", {
                summary = "Authorize issuer to automatically add credentials to the wallet in future"
                response {
                    HttpStatusCode.Accepted to { description = "Authorization succeed" }
                    HttpStatusCode.BadRequest to { description = "Authorization failed" }
                }
            }) {
                WalletServiceManager.issuerUseCase.authorize(call.getWalletService().walletId, call.parameters.getOrFail("issuer")).onSuccess {
                    call.respond(HttpStatusCode.Accepted)
                }.onFailure {
                    call.respond(HttpStatusCode.BadRequest, it.localizedMessage)
                }
            }
        }
        route("{issuer}/credentials", {
            request {
                pathParameter<String>("issuer") {
                    description = "The issuer name"
                    example("walt.id") { value = "walt.id" }
                }
            }
        }) {
            get({
                summary = "Show supported credentials for the given issuer"

                response {
                    HttpStatusCode.OK to {
                        description = "Array of issuer credential objects"
                        body<List<CredentialDataTransferObject>>()
                    }
                    HttpStatusCode.InternalServerError to {
                        description = "Error message"
                        body<String>()
                    }
                }
            }) {
                WalletServiceManager.issuerUseCase.credentials(call.getWalletService().walletId, call.parameters.getOrFail("issuer")).onSuccess {
                    call.respond(it)
                }.onFailure {
                    call.respond(HttpStatusCode.BadRequest, it.localizedMessage)
                }
            }
        }
    }
}

@Serializable
internal data class IssuerParameter(
    val name: String,
    val description: String? = "no description",
    val uiEndpoint: String = "",
    val configurationEndpoint: String = "",
)
