package id.walt.web.controllers

import id.walt.db.models.WalletOperationHistory
import id.walt.oid4vc.data.dif.PresentationDefinition
import id.walt.service.SSIKit2WalletService
import io.github.smiley4.ktorswaggerui.dsl.post
import io.github.smiley4.ktorswaggerui.dsl.route
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

fun Application.exchange() = walletRoute {
    route("exchange", {
        tags = listOf("WalletCredential exchange")
    }) {
        post("useOfferRequest", {
            summary = "Claim credential(s) from an issuer"

            request {
                queryParameter<String>("did") { description = "The DID to issue the credential(s) to" }
                body<String> {
                    description = "The offer request to use"
                }
            }
            response {
                HttpStatusCode.OK to {
                    description = "Successfully claimed credentials"
                }
            }
        }) {
            val wallet = getWalletService()

            val did = call.request.queryParameters["did"]
                ?: wallet.listDids().firstOrNull()?.did
                ?: throw IllegalArgumentException("No DID to use supplied")

            val offer = call.receiveText()

            wallet.useOfferRequest(offer, did)
            wallet.addOperationHistory(
                WalletOperationHistory.new(
                    wallet, "useOfferRequest",
                    mapOf("did" to did, "offer" to offer)
                )
            )

            context.respond(HttpStatusCode.OK)
        }

        post("matchCredentialsForPresentationDefinition", {
            summary = "Returns the credentials stored in the wallet that match the passed presentation definition"

            request {
                body<PresentationDefinition> { description = "Presentation definition to match credentials against" }
            }
            response {
                HttpStatusCode.OK to {
                    body<List<JsonObject>> {
                        description = "Credentials that match the presentation definition"
                    }
                }
            }
        }) {
            val presentationDefinition = PresentationDefinition.fromJSON(context.receive<JsonObject>())

            val wallet = getWalletService()
            val matchedCredentials = wallet.matchCredentialsByPresentationDefinition(presentationDefinition)

            context.respond(matchedCredentials)
        }

        post("usePresentationRequest", {
            summary = "Present credential(s) to a Relying Party"

            request {
                body<UsePresentationRequest>()
            }
            response {
                HttpStatusCode.OK to {
                    description = "Successfully claimed credentials"
                    body<JsonObject> {
                        description = """{"redirectUri": String}"""
                    }
                }
                HttpStatusCode.BadRequest to {
                    description = "Presentation was not accepted"
                    body<JsonObject> {
                        description = """{"redirectUri": String?, "errorMessage": String}"""
                    }
                }
            }
        }) {
            val wallet = getWalletService()

            val req = call.receive<UsePresentationRequest>()
            println("req: $req")

            val request = req.presentationRequest

            val did = req.did
                ?: wallet.listDids().firstOrNull()?.did
                ?: throw IllegalArgumentException("No DID to use supplied")
            val selectedCredentialIds = req.selectedCredentials
            // TODO -> ?: auto matching
            val disclosures = req.disclosures


            val result = wallet.usePresentationRequest(request, did, selectedCredentialIds, disclosures) // TODO add disclosures here

            if (result.isSuccess) {
                wallet.addOperationHistory(
                    WalletOperationHistory.new(
                        wallet, "usePresentationRequest",
                        mapOf(
                            "did" to did,
                            "request" to request,
                            "selected-credentials" to selectedCredentialIds.joinToString(),
                            "success" to "true",
                            "redirect" to result.getOrThrow()
                        ) // change string true to bool
                    )
                )

                context.respond(HttpStatusCode.OK, mapOf("redirectUri" to result.getOrThrow()))
            } else {
                val err = result.exceptionOrNull()
                println("Presentation failed: $err")

                wallet.addOperationHistory(
                    WalletOperationHistory.new(
                        wallet, "usePresentationRequest",
                        mapOf(
                            "did" to did,
                            "request" to request,
                            "success" to "false",
                            //"redirect" to ""
                        ) // change string false to bool
                    )
                )
                when (err) {
                    is SSIKit2WalletService.PresentationError -> {
                        context.respond(
                            HttpStatusCode.BadRequest, mapOf(
                                "redirectUri" to err.redirectUri,
                                "errorMessage" to err.message
                            )
                        )
                    }

                    else -> context.respond(HttpStatusCode.BadRequest, mapOf("errorMessage" to err?.message))
                }
            }
        }
        post("resolvePresentationRequest", {
            summary = "Return resolved / parsed presentation request"

            request {
                body<String> { description = "PresentationRequest to resolve/parse" }
            }
            response {
                HttpStatusCode.OK to {
                    body<String>()
                }
            }
        }) {
            val wallet = getWalletService()
            val request = call.receiveText()
            val parsedRequest = wallet.resolvePresentationRequest(request)
            context.respond(parsedRequest)
        }
    }
}

@Serializable
data class UsePresentationRequest(
    val did: String? = null,
    val presentationRequest: String,

    val selectedCredentials: List<String>, // todo: automatically choose matching
    val disclosures: Map<String, List<String>>? = null,
)
