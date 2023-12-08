package id.walt.web.controllers

import id.walt.db.models.WalletCredential
import io.github.smiley4.ktorswaggerui.dsl.delete
import io.github.smiley4.ktorswaggerui.dsl.get
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
            response {
                HttpStatusCode.OK to {
                    description = "Array of (verifiable credentials) JSON documents"
                    body<List<JsonObject>>()
                }
            }
        }) {
            context.respond(getWalletService().listCredentials().map { it.parsedDocument })
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
                val credentialId =
                    call.parameters["credentialId"] ?: throw IllegalArgumentException("No credentialId provided")

                context.respond(getWalletService().getCredential(credentialId))
            }

            delete({
                summary = "Delete a credential"

                response {
                    HttpStatusCode.Accepted to { description = "WalletCredential deleted" }
                    HttpStatusCode.BadRequest to { description = "WalletCredential could not be deleted" }
                }
            }) {
                val credentialId =
                    call.parameters["credentialId"] ?: throw IllegalArgumentException("No credentialId provided")

                val success = getWalletService().deleteCredential(credentialId)

                context.respond(if (success) HttpStatusCode.Accepted else HttpStatusCode.BadRequest)
            }
        }
    }
}
