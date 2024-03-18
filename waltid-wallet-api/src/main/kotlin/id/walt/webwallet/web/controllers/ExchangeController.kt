package id.walt.webwallet.web.controllers

import id.walt.oid4vc.data.dif.PresentationDefinition
import id.walt.oid4vc.requests.CredentialOfferRequest
import id.walt.webwallet.db.models.WalletCredential
import id.walt.webwallet.db.models.WalletOperationHistory
import id.walt.webwallet.seeker.DefaultCredentialTypeSeeker
import id.walt.webwallet.seeker.DefaultDidSeeker
import id.walt.webwallet.service.SSIKit2WalletService
import id.walt.webwallet.service.WalletServiceManager
import id.walt.webwallet.service.credentials.CredentialsService
import id.walt.webwallet.service.dids.DidsService
import id.walt.webwallet.service.events.EventType
import id.walt.webwallet.service.exchange.IssuanceService
import id.walt.webwallet.service.issuers.IssuersService
import id.walt.webwallet.usecase.issuer.IssuerUseCaseImpl
import id.walt.webwallet.web.WebBaseRoutes.webWalletRoute
import io.github.smiley4.ktorswaggerui.dsl.post
import io.github.smiley4.ktorswaggerui.dsl.route
import io.ktor.client.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.util.*
import io.ktor.util.*
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.uuid.UUID
import kotlinx.uuid.generateUUID

fun Application.exchange() = walletRoute {
    route("exchange", {
        tags = listOf("WalletCredential exchange")
    }) {
        post("useOfferRequest", {
            summary = "Claim credential(s) from an issuer"

            request {
                queryParameter<String>("did") { description = "The DID to issue the credential(s) to" }
                queryParameter<Boolean>("requireUserInput") { description = "Whether to claim as pending acceptance" }
                body<String> {
                    description = "The offer request to use"
                }
            }
            response {
                HttpStatusCode.OK to {
                    body<List<WalletCredential>> {
                        description = "List of credentials"
                    }
                }
            }
        }) {
            val wallet = getWalletService()

            val did = call.request.queryParameters["did"] ?: wallet.listDids().firstOrNull()?.did
            ?: throw IllegalArgumentException("No DID to use supplied")
            val requireUserInput = call.request.queryParameters["requireUserInput"].toBoolean()

            val offer = call.receiveText()

            runCatching {
                wallet.useOfferRequest(offer = offer, did = did, requireUserInput = requireUserInput)
                    .also {
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
            }.onFailure {  error ->
                println("wallet exception: $error")
                context.respond(HttpStatusCode.BadRequest, error.localizedMessage) }
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
        }) {
            val wallet = getWalletService()
            val request = call.receiveText()
            val reqParams = Url(request).parameters.toMap()
            val parsedOffer = wallet.resolveCredentialOffer(CredentialOfferRequest.fromHttpParameters(reqParams))
            context.respond(parsedOffer)
        }
    }
}

fun Application.silentExchange() = webWalletRoute {
    route("api", {
        tags = listOf("WalletCredential Exchange")
    }) {
        post("useOfferRequest/{did}", {
            summary = "Silently claim credentials"
            request {
                pathParameter<String>("did") { description = "The DID to issue the credential(s) to" }
                body<String> {
                    description = "The offer request to use"
                }
            }
        }) {
            val did = call.parameters.getOrFail("did")
            val offer = call.receiveText()
            val issuerUseCase = IssuerUseCaseImpl(
                service = IssuersService, http = HttpClient()
            )
            val credentialService = CredentialsService()
            // claim offer
            val credentials = IssuanceService.useOfferRequest(
                offer = offer,
                credentialWallet = SSIKit2WalletService.getCredentialWallet(did),
                clientId = SSIKit2WalletService.testCIClientConfig.clientID
            ).mapNotNull {
                val manifest = WalletCredential.tryParseManifest(it.manifest) ?: JsonObject(emptyMap())
                val credential = WalletCredential.parseDocument(it.document, it.id) ?: JsonObject(emptyMap())
                if (WalletServiceManager.issuerTrustValidationService.validate(
                        did = DefaultDidSeeker().get(manifest),
                        type = DefaultCredentialTypeSeeker().get(credential),
                        egfUri = "test"
                    )
                ) {
                    DidsService.getWalletsForDid(did).map { wallet ->
                        WalletCredential(
                            wallet = wallet,
                            id = it.id,
                            document = it.document,
                            disclosures = it.disclosures,
                            addedOn = Clock.System.now(),
                            manifest = it.manifest,
                            deletedOn = null,
                            pending = issuerUseCase.get(
                                wallet = wallet, name = WalletCredential.parseIssuerDid(credential, manifest) ?: ""
                            ).getOrNull()?.authorized ?: true,
                        ).also { credential ->
                            WalletServiceManager.eventUseCase.log(
                                action = EventType.Credential.Receive,
                                originator = "",
                                tenant = "global",//TODO
                                accountId = UUID.generateUUID(),//TODO: getUserUUID(),
                                walletId = wallet,
                                data = WalletServiceManager.eventUseCase.credentialEventData(
                                    credential = credential, type = it.type
                                ),
                                credentialId = credential.id,
                            )
                        }
                    }
                } else null
            }.flatten()
            // store credentials
            val result = credentials.groupBy {
                it.wallet
            }.flatMap {
                credentialService.add(
                    wallet = it.key, credentials = it.value.toTypedArray()
                )
            }
            context.respond(HttpStatusCode.Accepted, result.size)
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
    val note: String? = null
)