package id.walt.webwallet.web.controllers

import id.walt.webwallet.service.issuers.CredentialDataTransferObject
import id.walt.webwallet.service.issuers.IssuerDataTransferObject
import id.walt.webwallet.service.issuers.IssuersService
import id.walt.webwallet.usecase.issuer.IssuerUseCaseImpl
import io.github.smiley4.ktorswaggerui.dsl.get
import io.github.smiley4.ktorswaggerui.dsl.post
import io.github.smiley4.ktorswaggerui.dsl.put
import io.github.smiley4.ktorswaggerui.dsl.route
import io.ktor.client.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.util.*
import kotlinx.serialization.Serializable

fun Application.issuers() = walletRoute {
    val useCase = IssuerUseCaseImpl(
        service = IssuersService,
        http = HttpClient()
    )
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
            context.respond(useCase.list(getWalletService().walletId))
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
            useCase.add(
                IssuerDataTransferObject(
                    wallet = getWalletService().walletId,
                    name = issuer.name,
                    description = issuer.description,
                    uiEndpoint = issuer.uiEndpoint,
                    configurationEndpoint = issuer.configurationEndpoint,
                )
            ).onSuccess {
                context.respond(HttpStatusCode.Created)
            }.onFailure {
                context.respond(HttpStatusCode.BadRequest, it.localizedMessage)
            }
        }
        route("{issuer}", {
            request {
                pathParameter<String>("issuer") {
                    description = "The issuer name"
                    example = "walt.id"
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
                useCase.get(getWalletService().walletId, call.parameters.getOrFail("issuer")).onSuccess {
                    context.respond(it)
                }.onFailure {
                    context.respondText(it.localizedMessage, ContentType.Text.Plain, HttpStatusCode.NotFound)
                }
            }
            put("authorize", {
                summary = "Authorize issuer to automatically add credentials to the wallet in future"
                response {
                    HttpStatusCode.Accepted to { description = "Authorization succeed" }
                    HttpStatusCode.BadRequest to { description = "Authorization failed" }
                }
            }) {
                useCase.authorize(getWalletService().walletId, call.parameters.getOrFail("issuer")).onSuccess {
                    context.respond(HttpStatusCode.Accepted)
                }.onFailure {
                    context.respond(HttpStatusCode.BadRequest, it.localizedMessage)
                }
            }
        }
        route("{issuer}/credentials", {
            request {
                pathParameter<String>("issuer") {
                    description = "The issuer name"
                    example = "walt.id"
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
                useCase.credentials(getWalletService().walletId, call.parameters.getOrFail("issuer")).onSuccess {
                    context.respond(it)
                }.onFailure {
                    context.respond(HttpStatusCode.BadRequest, it.localizedMessage)
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