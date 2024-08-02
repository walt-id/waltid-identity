package id.walt.webwallet.web.controllers

import id.walt.oid4vc.data.CredentialOffer
import id.walt.oid4vc.data.dif.PresentationDefinition
import id.walt.oid4vc.requests.AuthorizationRequest
import id.walt.oid4vc.requests.CredentialOfferRequest
import id.walt.webwallet.db.models.WalletCredential
import id.walt.webwallet.db.models.WalletOperationHistory
import id.walt.webwallet.service.SSIKit2WalletService
import id.walt.webwallet.service.WalletServiceManager
import id.walt.webwallet.usecase.exchange.FilterData
import id.walt.webwallet.web.WebBaseRoutes.webWalletRoute
import io.github.smiley4.ktorswaggerui.dsl.routing.post
import io.github.smiley4.ktorswaggerui.dsl.routing.route
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.util.*
import io.ktor.util.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

fun Application.redirects() = unprotectedWalletRoute {

    route("exchange1", {
        tags = listOf("Credential exchange")
    }) {



        get("authorization/{authReqSessionId}") {
            val wallet = getWalletServiceUnprotected()

            val authReqSessionId = call.parameters.getOrFail("authReqSessionId")
            val receivedAuthReq = runBlocking { AuthorizationRequest.fromHttpParametersAuto(call.parameters.toMap()) }

            runCatching {
                WalletServiceManager.explicitClaimStrategy.resolveReceivedAuthorizationRequest(
                    tenant = wallet.tenant,
                    account = getUserUUIDByAuthReqSessions(),
                    wallet = wallet.walletId,
                    receivedAuthReq = receivedAuthReq,
                    authReqSessionId = authReqSessionId
                ).also {
                    wallet.addOperationHistory(
                        WalletOperationHistory.new(
                            tenant = wallet.tenant,
                            wallet = wallet,
                            "useOfferRequestAuthAuthorizationRequest",
                            mapOf("authReqSessionId" to authReqSessionId, "state" to receivedAuthReq.state, "authReqQueryString" to receivedAuthReq.toHttpQueryString())
                        )
                    )
                }
            }.onSuccess {
                call.respond(HttpStatusCode.OK, it)
//                call.response.apply {
//                    val url = it.consentPageUri + "?" + "request=${it.request}" + "&" + "type=${it.type}"
//                    status(HttpStatusCode.Found)
//                    header(
//                        HttpHeaders.Location,
//                        URLBuilder("https://walt.id").apply{}.buildString()
//                    )
//                }
            }
        }

        get("callback/{authReqSessionId}") {
            val wallet = getWalletServiceUnprotected()

            val authReqSessionId = call.parameters.getOrFail("authReqSessionId")
            val code = call.request.queryParameters.toMap()["code"]?.firstOrNull() ?: throw IllegalArgumentException("No code to use supplied")
            val state = call.request.queryParameters.toMap()["state"]?.firstOrNull() ?: throw IllegalArgumentException("No state to use supplied")

            runCatching {
                WalletServiceManager.explicitClaimStrategy.handleCallback(
                    tenant = wallet.tenant,
                    account = getUserUUIDByAuthReqSessions(),
                    wallet = wallet.walletId,
                    authReqSessionId = authReqSessionId,
                    code = code,
                    state = state
                ).also {
                    wallet.addOperationHistory(
                        WalletOperationHistory.new(
                            tenant = wallet.tenant,
                            wallet = wallet,
                            "useOfferRequestAuthCallback",
                            mapOf("authReqSessionId" to authReqSessionId, "state" to state)
                        )
                    )
                }
            }.onSuccess {
                call.respond(HttpStatusCode.OK, it)
            }
        }
    }
}

fun Application.exchange() = walletRoute {

    route("exchange", {
        tags = listOf("Credential exchange")
    }) {
        get("useOfferRequestAuth") {
            val wallet = getWalletService()
            val did = call.request.queryParameters["did"] ?: wallet.listDids().firstOrNull()?.did ?: throw IllegalArgumentException("No DID to use supplied")
            val offer = call.request.queryParameters.getOrFail("offer")
            val consentPageUri = call.request.queryParameters.getOrFail("consentPageUri")

            runCatching {
                WalletServiceManager.explicitClaimStrategy.claimAuthorize(
                    tenant = wallet.tenant,
                    account = getUserUUID(),
                    wallet = wallet.walletId,
                    did = did,
                    offer = offer,
                    consentPageUri = consentPageUri
                ).also {
                    wallet.addOperationHistory(
                        WalletOperationHistory.new(
                            tenant = wallet.tenant,
                            wallet = wallet,
                            "useOfferRequestAuth",
                            mapOf("did" to did, "offer" to offer)
                        )
                    )
                }
            }.onSuccess {
                call.response.apply {
                    status(HttpStatusCode.Found)
                    header(
                        HttpHeaders.Location,
                        URLBuilder(it).apply{}.buildString()
                    )
                }
            }
        }

        get("authorization/{authReqSessionId}") {
            val wallet = getWalletService()

            val authReqSessionId = call.parameters.getOrFail("authReqSessionId")
            val receivedAuthReq = runBlocking { AuthorizationRequest.fromHttpParametersAuto(call.parameters.toMap()) }

            runCatching {
                WalletServiceManager.explicitClaimStrategy.resolveReceivedAuthorizationRequest(
                    tenant = wallet.tenant,
                    account = getUserUUID(),
                    wallet = wallet.walletId,
                    receivedAuthReq = receivedAuthReq,
                    authReqSessionId = authReqSessionId
                ).also {
                    wallet.addOperationHistory(
                        WalletOperationHistory.new(
                            tenant = wallet.tenant,
                            wallet = wallet,
                            "useOfferRequestAuthAuthorizationRequest",
                            mapOf("authReqSessionId" to authReqSessionId, "state" to receivedAuthReq.state, "authReqQueryString" to receivedAuthReq.toHttpQueryString())
                        )
                    )
                }
            }.onSuccess {
                call.respond(HttpStatusCode.OK, it)
            }
        }

        get("callback/{authReqSessionId}") {
            val wallet = getWalletService()

            val authReqSessionId = call.parameters.getOrFail("authReqSessionId")
            val code = call.request.queryParameters.toMap()["code"]?.firstOrNull() ?: throw IllegalArgumentException("No code to use supplied")
            val state = call.request.queryParameters.toMap()["state"]?.firstOrNull() ?: throw IllegalArgumentException("No state to use supplied")

            runCatching {
                WalletServiceManager.explicitClaimStrategy.handleCallback(
                    tenant = wallet.tenant,
                    account = getUserUUID(),
                    wallet = wallet.walletId,
                    authReqSessionId = authReqSessionId,
                    code = code,
                    state = state
                ).also {
                    wallet.addOperationHistory(
                        WalletOperationHistory.new(
                            tenant = wallet.tenant,
                            wallet = wallet,
                            "useOfferRequestAuthCallback",
                            mapOf("authReqSessionId" to authReqSessionId, "state" to state)
                        )
                    )
                }
            }.onSuccess {
                call.respond(HttpStatusCode.OK, it)
            }
        }


        get("useIdTokenRequest") {
            val wallet = getWalletService()

            val did = call.request.queryParameters["did"] ?: wallet.listDids().firstOrNull()?.did
            ?: throw IllegalArgumentException("No DID to use supplied")
            val authReq = runBlocking { AuthorizationRequest.fromHttpParametersAuto(call.parameters.toMap()) }

            runCatching {
                WalletServiceManager.explicitClaimStrategy.useIdTokenRequest(
                    tenant = wallet.tenant,
                    account = getUserUUID(),
                    wallet = wallet.walletId,
                    did = did,
                    authReq = authReq
                ).also {
                    wallet.addOperationHistory(
                        WalletOperationHistory.new(
                            tenant = wallet.tenant,
                            wallet = wallet,
                            "useIdTokenRequest",
                            mapOf("did" to did, "idTokenRequest" to authReq.toHttpQueryString())
                        )
                    )
                }
            }.onSuccess {
                call.response.apply {
                    status(HttpStatusCode.Found)
                    header(
                        HttpHeaders.Location,
                        URLBuilder(it).apply{}.buildString()
                    )
                }
            }
        }

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
        })
        {
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
                context.respond(HttpStatusCode.BadRequest, error.localizedMessage)
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
        })
        {
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
                            "redirect" to result.getOrThrow()?.get("redirectUri")
                        ) // change string true to bool
                    )
                )

                when (result.getOrThrow()?.get("isRedirect") ){
                  "true" -> {
                      call.response.apply {
                          status(HttpStatusCode.Found)
                          header(
                              HttpHeaders.Location,
                              URLBuilder(result.getOrThrow()?.get("redirectUri")!!).apply{}.buildString()
                          )
                      }
                  }
                   else -> context.respond(HttpStatusCode.OK, mapOf("redirectUri" to result.getOrThrow()?.get("redirectUri")))
                }
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
