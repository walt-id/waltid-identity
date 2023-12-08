package id.walt.web.controllers

import id.walt.service.issuers.CredentialDataTransferObject
import id.walt.service.issuers.IssuerCredentialsDataTransferObject
import id.walt.service.issuers.IssuerDataTransferObject
import id.walt.service.issuers.IssuersService
import io.github.smiley4.ktorswaggerui.dsl.get
import io.github.smiley4.ktorswaggerui.dsl.route
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*

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
                    getWalletService().getIssuer(
                        call.parameters["issuer"] ?: throw IllegalArgumentException("No issuer name provided.")
                    )
                }.onSuccess {
                    context.respond(it)
                }.onFailure {
                    context.respondText(it.localizedMessage, ContentType.Text.Plain, HttpStatusCode.NotFound)
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
                runCatching {
                    val issuer = getWalletService().getIssuer(
                        call.parameters["issuer"] ?: throw IllegalArgumentException("No issuer name provided.")
                    )
                    IssuerCredentialsDataTransferObject(
                        issuer = issuer, credentials = IssuersService.fetchCredentials(issuer.configurationEndpoint)
                    )
                }.onSuccess {
                    context.respond(it)
                }.onFailure {
                    context.respondText(it.localizedMessage, ContentType.Text.Plain, HttpStatusCode.InternalServerError)
                }
            }
        }
    }
}
