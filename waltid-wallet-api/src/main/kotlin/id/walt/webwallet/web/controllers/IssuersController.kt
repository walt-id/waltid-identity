package id.walt.web.controllers

import id.walt.webwallet.service.issuers.CredentialDataTransferObject
import id.walt.webwallet.service.issuers.IssuerCredentialsDataTransferObject
import id.walt.webwallet.service.issuers.IssuerDataTransferObject
import id.walt.webwallet.service.issuers.IssuersService
import id.walt.webwallet.web.controllers.getWalletService
import id.walt.webwallet.web.controllers.walletRoute
import io.github.smiley4.ktorswaggerui.dsl.get
import io.github.smiley4.ktorswaggerui.dsl.post
import io.github.smiley4.ktorswaggerui.dsl.route
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.util.*

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
            context.respond(getWalletService().listIssuers())
        }
        post("add", {
            summary = "Add issuer to wallet"
            request {
                body<IssuerDataTransferObject> {
                    description = "Issuer data"
                    required = true
                }
            }
            response {
                HttpStatusCode.Created to { description = "Issuer added successfully" }
                HttpStatusCode.BadRequest to { description = "Failed to add issuer to wallet" }
            }
        }) {
            runCatching {
                getWalletService().addIssuer(call.receive<IssuerDataTransferObject>())
            }.onSuccess {
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
                runCatching {
                    getWalletService().getIssuer(call.parameters.getOrFail("issuer"))
                }.onSuccess {
                    context.respond(it)
                }.onFailure {
                    context.respondText(it.localizedMessage, ContentType.Text.Plain, HttpStatusCode.NotFound)
                }
            }
            post("authorize",{
                summary = "Authorize issuer to automatically add credentials to the wallet in future"
                response {
                    HttpStatusCode.Accepted to { description = "Authorization succeed" }
                    HttpStatusCode.BadRequest to { description = "Authorization failed" }
                }
            }){
                runCatching {
                    getWalletService().authorizeIssuer(call.parameters.getOrFail("issuer"))
                }.onSuccess {
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
                val issuer = getWalletService().getIssuer(call.parameters["issuer"] ?: error("No issuer name provided."))
                runCatching {
                    IssuerCredentialsDataTransferObject(
                        issuer = issuer, credentials = IssuersService.fetchCredentials(issuer.configurationEndpoint)
                    )
                }.onSuccess {
                    context.respond(it)
                }.onFailure { err ->
                    throw IllegalArgumentException(
                        "Could not fetch issuer configuration from issuer ${issuer.name} at ${issuer.configurationEndpoint}: ${err.message}",
                        err
                    )
                }
            }
        }
    }
}
