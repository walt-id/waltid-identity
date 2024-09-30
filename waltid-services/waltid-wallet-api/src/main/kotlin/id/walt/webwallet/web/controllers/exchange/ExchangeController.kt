package id.walt.webwallet.web.controllers.exchange

import id.walt.oid4vc.data.CredentialOffer
import id.walt.oid4vc.data.dif.PresentationDefinition
import id.walt.oid4vc.requests.CredentialOfferRequest
import id.walt.sdjwt.SDJWTVCTypeMetadata
import id.walt.webwallet.db.models.WalletOperationHistory
import id.walt.webwallet.service.SSIKit2WalletService
import id.walt.webwallet.service.WalletServiceManager
import id.walt.webwallet.usecase.exchange.FilterData
import id.walt.webwallet.web.controllers.auth.getUserUUID
import id.walt.webwallet.web.controllers.auth.getWalletId
import id.walt.webwallet.web.controllers.auth.getWalletService
import id.walt.webwallet.web.controllers.exchange.openapi.ExchangeOpenApiCommons
import io.github.smiley4.ktorswaggerui.dsl.routing.get
import id.walt.webwallet.web.controllers.walletRoute
import io.github.smiley4.ktorswaggerui.dsl.routing.post
import io.github.smiley4.ktorswaggerui.dsl.routing.route
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.util.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
fun Application.exchange() = walletRoute {
    route(ExchangeOpenApiCommons.EXCHANGE_ROOT_PATH, ExchangeOpenApiCommons.exchangeRoute()) {
        post("useOfferRequest", {
            summary = "Claim credential(s) from an issuer"

            request {
                queryParameter<String>("did") { description = "The DID to issue the credential(s) to" }
                queryParameter<Boolean>("requireUserInput") { description = "Whether to claim as pending acceptance" }
                body<String> {
                    description = "The offer request to use"
                }
            }

            response(ExchangeOpenApiCommons.useOfferRequestEndpointResponseParams())
        }) {
            val wallet = getWalletService()

            val did = call.request.queryParameters["did"] ?: wallet.listDids().firstOrNull()?.did
            ?: throw IllegalArgumentException("No DID to use supplied and no DID was found in wallet.")
            val requireUserInput = call.request.queryParameters["requireUserInput"].toBoolean()

            val offer = call.receiveText()

            runCatching {
                WalletServiceManager.explicitClaimStrategy.claim(
                    tenant = wallet.tenant,
                    account = getUserUUID(),
                    wallet = wallet.walletId,
                    did = did,
                    offer = offer,
                    pending = requireUserInput
                ).also {
                    wallet.addOperationHistory(
                        WalletOperationHistory.new(
                            tenant = wallet.tenant,
                            wallet = wallet,
                            "useOfferRequest",
                            mapOf("did" to did, "offer" to offer)
                        )
                    )
                }
            }.onSuccess {
                context.respond(HttpStatusCode.OK, it)
            }.onFailure { error ->
                error.printStackTrace()
                context.respond(HttpStatusCode.BadRequest, error.message ?: "Unknown error")
            }
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
            val matchedCredentials = WalletServiceManager.matchPresentationDefinitionCredentialsUseCase.match(
                getWalletId(), presentationDefinition
            )
            context.respond(matchedCredentials)
        }
        post("unmatchedCredentialsForPresentationDefinition", {
            summary =
                "Returns the credentials that are required by the presentation definition but not found in the wallet"

            request {
                body<PresentationDefinition> { description = "Presentation definition" }
            }
            response {
                HttpStatusCode.OK to {
                    body<List<FilterData>> {
                        description = "Filters that failed to fulfill the presentation definition"
                    }
                }
            }
        }) {
            val presentationDefinition = PresentationDefinition.fromJSON(context.receive<JsonObject>())
            val unmatchedCredentialTypes = WalletServiceManager.unmatchedPresentationDefinitionCredentialsUseCase.find(
                getWalletId(), presentationDefinition
            )
            context.respond(unmatchedCredentialTypes)
        }

        post("usePresentationRequest", {
            summary = "Present credential(s) to a Relying Party"

            request {
                body<UsePresentationRequest>()
            }
            response(ExchangeOpenApiCommons.usePresentationRequestResponse())
        }) {
            val wallet = getWalletService()

            val req = call.receive<UsePresentationRequest>()
            println("req: $req")

            val request = req.presentationRequest

            val did = req.did
                ?: wallet.listDids().firstOrNull { it.default }?.did
                ?: throw IllegalArgumentException("No DID to use supplied")
            val selectedCredentialIds = req.selectedCredentials
            // TODO -> ?: auto matching
            val disclosures = req.disclosures


            val result = wallet.usePresentationRequest(
                PresentationRequestParameter(
                    did = did,
                    request = request,
                    selectedCredentials = selectedCredentialIds,
                    disclosures = disclosures,
                    note = req.note,
                )
            ) // TODO add disclosures here

            if (result.isSuccess) {
                wallet.addOperationHistory(
                    WalletOperationHistory.new(
                        tenant = wallet.tenant,
                        wallet = wallet,
                        "usePresentationRequest",
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
                        tenant = wallet.tenant,
                        wallet = wallet,
                        "usePresentationRequest",
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
        post("resolveCredentialOffer", {
            summary = "Return resolved / parsed credential offer"

            request {
                body<String> { description = "Credential offer request to resolve/parse" }
            }
            response {
                HttpStatusCode.OK to {
                    body<CredentialOffer> {
                        description = "Resolved credential offer"
                    }
                }
            }
        }) {
            val wallet = getWalletService()
            val request = call.receiveText()
            val reqParams = Url(request).parameters.toMap()
            val parsedOffer = wallet.resolveCredentialOffer(CredentialOfferRequest.fromHttpParameters(reqParams))
            context.respond(parsedOffer)
        }
        get("resolveVctUrl", {
            summary = "Receive an verifiable credential type (VCT) URL and return resolved vct object as described in IETF SD-JWT VC"
            request {
                queryParameter<String>("vct") {
                    description = "The value of the vct in URL format"
                    example("Example") { value = "https://example.com/mycustomvct" }
                    required = true
                }
            }
            response {
                HttpStatusCode.OK to {
                    description = "Resolved VCT"
                    body<SDJWTVCTypeMetadata>()
                }
            }
        }) {
            val vct = call.request.queryParameters["vct"] ?: throw IllegalArgumentException("VCT not set")
            val wallet = getWalletService()
            runCatching {
                wallet.resolveVct(vct)
            }.onSuccess {
                context.respond(HttpStatusCode.OK, it.toJSON())
            }.onFailure { error ->
                error.printStackTrace()
                context.respond(HttpStatusCode.BadRequest, error.message ?: "Unknown error")
            }
        }
    }
}

@Serializable
data class UsePresentationRequest(
    val did: String? = null,
    val presentationRequest: String,

    val selectedCredentials: List<String>, // todo: automatically choose matching
    val disclosures: Map<String, List<String>>? = null,
    val note: String? = null,
)

data class PresentationRequestParameter(
    val did: String,
    val request: String,
    val selectedCredentials: List<String>,
    val disclosures: Map<String, List<String>>? = null,
    val note: String? = null,
)
