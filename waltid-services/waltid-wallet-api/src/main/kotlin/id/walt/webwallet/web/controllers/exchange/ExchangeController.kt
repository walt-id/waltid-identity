package id.walt.webwallet.web.controllers.exchange

import id.walt.oid4vc.OpenID4VCI
import id.walt.oid4vc.data.OpenIDProviderMetadataSerializer
import id.walt.oid4vc.data.dif.PresentationDefinition
import id.walt.oid4vc.requests.CredentialOfferRequest
import id.walt.webwallet.db.models.WalletOperationHistory
import id.walt.webwallet.service.SSIKit2WalletService
import id.walt.webwallet.service.WalletServiceManager
import id.walt.webwallet.web.controllers.auth.getUserUUID
import id.walt.webwallet.web.controllers.auth.getWalletId
import id.walt.webwallet.web.controllers.auth.getWalletService
import id.walt.webwallet.web.controllers.exchange.openapi.ExchangeDocs.getMatchCredentialsForPresentationDefinitionDocs
import id.walt.webwallet.web.controllers.exchange.openapi.ExchangeDocs.getResolveCredentialOfferDocs
import id.walt.webwallet.web.controllers.exchange.openapi.ExchangeDocs.getResolveIssuerOpenIDMetadataDocs
import id.walt.webwallet.web.controllers.exchange.openapi.ExchangeDocs.getResolvePresentationRequestDocs
import id.walt.webwallet.web.controllers.exchange.openapi.ExchangeDocs.getResolveVctUrlDocs
import id.walt.webwallet.web.controllers.exchange.openapi.ExchangeDocs.getUnmatchedCredentialsForPresentationDefinition
import id.walt.webwallet.web.controllers.exchange.openapi.ExchangeDocs.getUseOfferRequestDocs
import id.walt.webwallet.web.controllers.exchange.openapi.ExchangeDocs.getUsePresentationRequestDocs
import id.walt.webwallet.web.controllers.exchange.openapi.ExchangeOpenApiCommons
import id.walt.webwallet.web.controllers.walletRoute
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.github.smiley4.ktoropenapi.route
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.util.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
fun Application.exchange() = walletRoute {
    route(ExchangeOpenApiCommons.EXCHANGE_ROOT_PATH, ExchangeOpenApiCommons.exchangeRoute()) {

        post("useOfferRequest", getUseOfferRequestDocs()) {
            val wallet = call.getWalletService()

            val did = call.request.queryParameters["did"]
                ?: wallet.listDids().run {
                    // use default did if no did is provided in the parameters
                    firstOrNull { it.default }?.did
                    // use first DID if no DID is marked as default
                        ?: firstOrNull()?.did
                }
                ?: throw IllegalArgumentException("No DID to use supplied and no DID was found in wallet.")

            val requireUserInput = call.request.queryParameters["requireUserInput"].toBoolean()
            val pinOrTxCode = call.request.queryParameters["pinOrTxCode"]

            val offer = call.receiveText()

            runCatching {
                WalletServiceManager.explicitClaimStrategy.claim(
                    tenant = wallet.tenant,
                    account = call.getUserUUID(),
                    wallet = wallet.walletId,
                    did = did,
                    offer = offer,
                    pending = requireUserInput,
                    pinOrTxCode = pinOrTxCode,
                ).also {
                    wallet.addOperationHistory(
                        WalletOperationHistory.new(
                            tenant = wallet.tenant,
                            wallet = wallet,
                            operation = "useOfferRequest",
                            data = mapOf("did" to did, "offer" to offer)
                        )
                    )
                }
            }.onSuccess {
                call.respond(HttpStatusCode.OK, it)
            }.onFailure { error ->
                error.printStackTrace()
                call.respond(
                    status = HttpStatusCode.BadRequest,
                    message = error.message ?: "Unknown error"
                )
            }
        }

        post("matchCredentialsForPresentationDefinition", getMatchCredentialsForPresentationDefinitionDocs()) {
            val presentationDefinition = PresentationDefinition.fromJSON(call.receive<JsonObject>())
            val matchedCredentials =
                WalletServiceManager.matchCredentialsForPresentationDefinition(
                    call.getWalletId(),
                    presentationDefinition
                )
            call.respond(matchedCredentials)
        }

        post("unmatchedCredentialsForPresentationDefinition", getUnmatchedCredentialsForPresentationDefinition()) {
            val presentationDefinition = PresentationDefinition.fromJSON(call.receive<JsonObject>())
            val unmatchedCredentialTypes = WalletServiceManager.unmatchedPresentationDefinitionCredentialsUseCase.find(
                call.getWalletId(), presentationDefinition
            )
            call.respond(unmatchedCredentialTypes)
        }

        post("usePresentationRequest", getUsePresentationRequestDocs()) {
            val wallet = call.getWalletService()

            val req = call.receive<UsePresentationRequest>()

            val presentationRequest = req.presentationRequest

            val did = req.did
                ?: wallet.listDids().firstOrNull { it.default }?.did
                ?: throw IllegalArgumentException("No DID to use supplied")

            val selectedCredentialIds = req.selectedCredentials
            // TODO -> ?: auto matching

            val disclosures = req.disclosures

            val result = wallet.usePresentationRequest(
                parameter = PresentationRequestParameter(
                    did = did,
                    request = presentationRequest,
                    selectedCredentials = selectedCredentialIds,
                    disclosures = disclosures,
                    note = req.note,
                )
            ) // TODO add disclosures here

            if (result.isSuccess) {
                wallet.addOperationHistory(
                    operationHistory = WalletOperationHistory.new(
                        tenant = wallet.tenant,
                        wallet = wallet,
                        operation = "usePresentationRequest",
                        data = mapOf(
                            "did" to did,
                            "request" to presentationRequest,
                            "selected-credentials" to selectedCredentialIds.joinToString(),
                            "success" to "true",
                            "redirect" to result.getOrThrow()
                        ) // change string true to bool
                    )
                )

                call.respond(
                    status = HttpStatusCode.OK,
                    message = mapOf("redirectUri" to result.getOrThrow())
                )
            } else {
                val err = result.exceptionOrNull()

                wallet.addOperationHistory(
                    operationHistory = WalletOperationHistory.new(
                        tenant = wallet.tenant,
                        wallet = wallet,
                        operation = "usePresentationRequest",
                        data = mapOf(
                            "did" to did,
                            "request" to presentationRequest,
                            "success" to "false",
                            //"redirect" to ""
                        ) // change string false to bool
                    )
                )

                when (err) {
                    is SSIKit2WalletService.PresentationError -> {
                        call.respond(
                            status = HttpStatusCode.BadRequest,
                            message = mapOf(
                                "redirectUri" to err.redirectUri,
                                "errorMessage" to err.message
                            )
                        )
                    }

                    else -> call.respond(
                        status = HttpStatusCode.BadRequest,
                        message = mapOf("errorMessage" to err?.message)
                    )
                }
            }
        }

        post("resolvePresentationRequest", getResolvePresentationRequestDocs()) {
            val wallet = call.getWalletService()
            val request = call.receiveText()
            val parsedRequest = wallet.resolvePresentationRequest(request)
            call.respond(parsedRequest)
        }

        post("resolveCredentialOffer", getResolveCredentialOfferDocs()) {
            val wallet = call.getWalletService()
            val request = call.receiveText()
            val reqParams = Url(request).parameters.toMap()
            val parsedOffer = wallet.resolveCredentialOffer(CredentialOfferRequest.fromHttpParameters(reqParams))

            val serializedOffer = parsedOffer.toJSONString()

            call.respondText(serializedOffer, ContentType.Application.Json)
        }

        get("resolveVctUrl", getResolveVctUrlDocs()) {
            val vct = call.request.queryParameters["vct"] ?: throw IllegalArgumentException("VCT not set")
            val wallet = call.getWalletService()
            runCatching {
                wallet.resolveVct(vct)
            }.onSuccess {
                call.respond(HttpStatusCode.OK, it.toJSON())
            }.onFailure { error ->
                error.printStackTrace()
                call.respond(HttpStatusCode.BadRequest, error.message ?: "Unknown error")
            }
        }
        get("resolveIssuerOpenIDMetadata", getResolveIssuerOpenIDMetadataDocs()) {
            val issuer = call.request.queryParameters["issuer"] ?: throw BadRequestException("Issuer base url not set")
            val serializedMetadata =
                Json.encodeToString(OpenIDProviderMetadataSerializer, OpenID4VCI.resolveCIProviderMetadata(issuer))
            call.respondText(serializedMetadata, ContentType.Application.Json)
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
