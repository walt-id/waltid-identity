package id.walt.webwallet.web.controllers

import id.walt.oid4vc.requests.AuthorizationRequest
import id.walt.webwallet.service.SSIKit2WalletService.Companion.getCredentialWallet
import id.walt.webwallet.service.SessionAttributes
import id.walt.webwallet.service.dids.DidsService
import id.walt.webwallet.web.controllers.auth.getWalletService
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.smiley4.ktorswaggerui.dsl.routing.post
import io.github.smiley4.ktorswaggerui.dsl.routing.route
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.util.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlin.time.Duration.Companion.seconds

fun Application.exchangeExternalSignatures() = walletRoute {
    route("exchange/external_signatures", {
        tags = listOf("Credential exchange")
    }) {
        post("prepare_oid4vp", {
            summary = "Do first step of oid4vp with externally provided signatures"

            request {
                body<PrepareOID4VPRequest> {
                    description = "Kati gia to description tou request"
                }
            }
            response {
                HttpStatusCode.OK to {
                    body<PrepareOID4VPResponse> {
                        description = "Kati gia to description tou response"
                    }
                }
            }
        }) {
            val logger = KotlinLogging.logger { }
            val wallet = getWalletService()

            val req = call.receive<PrepareOID4VPRequest>()
            println("Request: $req")
            val walletDID = DidsService.get(wallet.walletId, req.did)
                ?: throw IllegalArgumentException("did ${req.did} not found in wallet")

            println("Retrieved DID: $walletDID")

            val credentialWallet = getCredentialWallet(walletDID.did)

            val authReq =
                AuthorizationRequest.fromHttpParametersAuto(parseQueryString(Url(req.presentationRequest).encodedQuery).toMap())
            logger.debug { "Auth req: $authReq" }

            logger.debug { "Using presentation request, selected credentials: ${req.selectedCredentialIdList}" }

            SessionAttributes.HACK_outsideMappedSelectedCredentialsPerSession[authReq.state + authReq.presentationDefinition] =
                req.selectedCredentialIdList

            val presentationSession =
                credentialWallet.initializeAuthorization(authReq, 60.seconds, req.selectedCredentialIdList.toSet())
            logger.debug { "Initialized authorization (VPPresentationSession): $presentationSession" }

            logger.debug { "Resolved presentation definition: ${presentationSession.authorizationRequest!!.presentationDefinition!!.toJSONString()}" }

            context.respond(
                HttpStatusCode.OK,
                PrepareOID4VPResponse(
                    walletDID.did,
                    UnsignedVPTokenParameters(
                        emptyMap<String, JsonElement>(),
                        emptyMap<String, JsonElement>(),
                    ),
                ),
            )
        }
    }
}

@Serializable
data class PrepareOID4VPRequest(
    val did: String,
    val presentationRequest: String,
    val selectedCredentialIdList: List<String>,
)

@Serializable
data class PrepareOID4VPResponse(
    val did: String,
    val vpTokenParams: UnsignedVPTokenParameters,
)

@Serializable
data class UnsignedVPTokenParameters(
    val header: Map<String, JsonElement>,
    val payload: Map<String, JsonElement>,
)