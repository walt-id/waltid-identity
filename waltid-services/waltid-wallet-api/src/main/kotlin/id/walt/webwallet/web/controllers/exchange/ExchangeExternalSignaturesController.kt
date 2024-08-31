package id.walt.webwallet.web.controllers.exchange

import com.nimbusds.jose.jwk.JWK
import com.nimbusds.jose.jwk.KeyUse
import com.nimbusds.jose.util.Base64URL
import id.walt.crypto.keys.KeyGenerationRequest
import id.walt.crypto.keys.KeyManager
import id.walt.crypto.keys.KeyType
import id.walt.crypto.utils.Base64Utils.base64UrlToBase64
import id.walt.crypto.utils.JsonUtils.toJsonElement
import id.walt.crypto.utils.JwsUtils.decodeJws
import id.walt.oid4vc.data.ResponseMode
import id.walt.oid4vc.data.VpTokenParameter
import id.walt.oid4vc.data.dif.DescriptorMapping
import id.walt.oid4vc.data.dif.PresentationSubmission
import id.walt.oid4vc.data.dif.VCFormat
import id.walt.oid4vc.errors.AuthorizationError
import id.walt.oid4vc.requests.AuthorizationRequest
import id.walt.oid4vc.responses.AuthorizationErrorCode
import id.walt.oid4vc.responses.TokenResponse
import id.walt.oid4vc.util.randomUUID
import id.walt.webwallet.service.SSIKit2WalletService.Companion.getCredentialWallet
import id.walt.webwallet.service.SSIKit2WalletService.PresentationError
import id.walt.webwallet.service.WalletServiceManager.eventUseCase
import id.walt.webwallet.service.credentials.CredentialsService
import id.walt.webwallet.service.dids.DidsService
import id.walt.webwallet.service.events.EventDataNotAvailable
import id.walt.webwallet.service.events.EventType
import id.walt.webwallet.service.oidc4vc.VPresentationSession
import id.walt.webwallet.utils.WalletHttpClients
import id.walt.webwallet.web.controllers.auth.getWalletService
import id.walt.webwallet.web.controllers.walletRoute
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.smiley4.ktorswaggerui.dsl.routing.post
import io.github.smiley4.ktorswaggerui.dsl.routing.route
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.util.*
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import kotlinx.uuid.UUID
import kotlinx.uuid.generateUUID

fun Application.exchangeExternalSignatures() = walletRoute {
    route(
        OpenAPICommons.rootPath,
        OpenAPICommons.route(),
    ) {
        post("external_signatures/oid4vp/prepare", {
            summary = "Preparation (first) step for an OID4VP flow with externally provided signatures."

            request {
                body<PrepareOID4VPRequest> {
                    required = true
                    example("default") {
                        value = PrepareOID4VPRequest(
                            "did:web:walt.id",
                            "oid4vp://authorize?response_type=...",
                            listOf(
                                "56d2449b-c40e-4091-8edf-5fb4920b08a3",
                                "a9df4e9c-3982-4ed2-999d-5b08603381c7",
                            ),
                        )
                    }
                }
            }
            response {
                HttpStatusCode.OK to {
                    description = "Collection of parameters that are necessary to invoke the submit endpoint. " +
                            "The client is expected to, in between, sign the vp token based on the " +
                            "vpTokenParams object that is contained within."
                    body<PrepareOID4VPResponse> {
                        required = true
                    }
                }
            }
        }) {
            val logger = KotlinLogging.logger { }
            val wallet = getWalletService()

            val req = call.receive<PrepareOID4VPRequest>()
            println("Request: $req")
            if (req.selectedCredentialIdList.isEmpty())
                throw IllegalArgumentException("Unable to prepare oid4vp parameters with no input credential identifiers")
            val walletDID = DidsService.get(wallet.walletId, req.did)
                ?: throw IllegalArgumentException("did ${req.did} not found in wallet")

            println("Retrieved DID: $walletDID")

            val credentialWallet = getCredentialWallet(walletDID.did)

            val authReq =
                AuthorizationRequest.fromHttpParametersAuto(parseQueryString(Url(req.presentationRequest).encodedQuery).toMap())
            logger.debug { "Auth req: $authReq" }

            logger.debug { "Using presentation request, selected credentials: ${req.selectedCredentialIdList}" }

            val resolvedAuthReq = credentialWallet.resolveVPAuthorizationParameters(authReq)
            logger.debug { "Resolved Auth req: $resolvedAuthReq" }

            val vpSession = VPresentationSession(
                randomUUID(),
                resolvedAuthReq,
                Instant.DISTANT_FUTURE,
                req.selectedCredentialIdList.toSet(),
            )

            if (!credentialWallet.validateAuthorizationRequest(resolvedAuthReq)) {
                throw AuthorizationError(
                    resolvedAuthReq,
                    AuthorizationErrorCode.invalid_request,
                    message = "Invalid VP authorization request"
                )
            }
            val matchedCredentials = wallet.getCredentialsByIds(req.selectedCredentialIdList)
            logger.debug { "Matched credentials: $matchedCredentials" }

            val presentationId = "urn:uuid:" + UUID.generateUUID().toString().lowercase()

            val authKeyId =
                Json.decodeFromString<JsonObject>(walletDID.document)["authentication"]!!.jsonArray.first().let {
                    if (it is JsonObject) {
                        it.jsonObject["id"]!!.jsonPrimitive.content
                    } else {
                        it.jsonPrimitive.content
                    }
                }

            val vpPayload = credentialWallet.getVpJson(
                matchedCredentials.map { it.document },
                presentationId,
                resolvedAuthReq.nonce,
                resolvedAuthReq.clientId,
            )

            val vpHeader = mapOf(
                "kid" to authKeyId.toJsonElement(),
                "typ" to "JWT".toJsonElement()
            )

            val rootPathVP = "$"


            val presentationSubmission = PresentationSubmission(
                id = presentationId,
                definitionId = presentationId,
                descriptorMap = matchedCredentials.mapIndexed { index, credential ->
                    val vcJws = credential.document.base64UrlToBase64().decodeJws()
                    val type =
                        vcJws.payload["vc"]?.jsonObject?.get("type")?.jsonArray?.last()?.jsonPrimitive?.contentOrNull
                            ?: "VerifiableCredential"
                    val descriptorId = resolvedAuthReq.presentationDefinition?.inputDescriptors?.find {
                        (it.name ?: it.id) == type
                    }?.id
                    DescriptorMapping(
                        id = descriptorId,
                        format = VCFormat.jwt_vp,
                        path = rootPathVP,
                        pathNested = DescriptorMapping(
                            id = descriptorId,
                            format = VCFormat.jwt_vc_json,
                            path = "$rootPathVP.verifiableCredential[$index]",
                        ),
                    )
                }
            )

            context.respond(
                HttpStatusCode.OK,
                PrepareOID4VPResponse(
                    walletDID.did,
                    req.presentationRequest,
                    vpSession.id,
                    resolvedAuthReq,
                    presentationSubmission,
                    req.selectedCredentialIdList,
                    UnsignedVPTokenParameters(
                        vpHeader,
                        vpPayload,
                    ),
                ),
            )
        }

        post("external_signatures/oid4vp/submit", {
            summary = "Submission (second) step of an OID4VP flow with externally provided signatures. " +
                    "The client is expected to provide the signed vp token in the respective input request field."

            request {
                body<SubmitOID4VPRequest> {
                }
            }
            response(OpenAPICommons.usePresentationRequestResponse())
        }) {
            val logger = KotlinLogging.logger { }
            val req = call.receive<SubmitOID4VPRequest>()
            println("Request: $req")
            val wallet = getWalletService()

            val authReq = req.resolvedAuthReq
            val presentationSubmission = req.presentationSubmission
            val presentedCredentialIdList = req.presentedCredentialIdList
            val did = req.did

            //TODO():: Use the did here to verify the signature on the vp token just to make sure...

            val tokenResponse = TokenResponse.success(
                vpToken = VpTokenParameter.fromJsonElement(req.signedVP.toJsonElement()),
                presentationSubmission = presentationSubmission,
                idToken = null,
                state = authReq.state,
            )

            val formParams =
                if (authReq.responseMode == ResponseMode.direct_post_jwt) {
                    val encKey =
                        authReq.clientMetadata?.jwks?.get("keys")?.jsonArray?.first { jwk ->
                            JWK.parse(jwk.toString()).keyUse?.equals(KeyUse.ENCRYPTION) ?: false
                        }?.jsonObject ?: throw Exception("No ephemeral reader key found")
                    val ephemeralWalletKey =
                        runBlocking { KeyManager.createKey(KeyGenerationRequest(keyType = KeyType.secp256r1)) }
                    tokenResponse.toDirecPostJWTParameters(
                        encKey,
                        alg = authReq.clientMetadata!!.authorizationEncryptedResponseAlg!!,
                        enc = authReq.clientMetadata!!.authorizationEncryptedResponseEnc!!,
                        mapOf(
                            "epk" to runBlocking { ephemeralWalletKey.getPublicKey().exportJWKObject() },
                            "apu" to JsonPrimitive(Base64URL.encode(authReq.nonce).toString()),
                            "apv" to JsonPrimitive(
                                Base64URL.encode(authReq.nonce!!).toString()
                            )
                        )
                    )
                } else tokenResponse.toHttpParameters()

            val httpClient = WalletHttpClients.defaultMethod()

            val resp = httpClient.submitForm(
                authReq.responseUri
                    ?: authReq.redirectUri ?: throw AuthorizationError(
                        authReq,
                        AuthorizationErrorCode.invalid_request,
                        "No response_uri or redirect_uri found on authorization request"
                    ),
                parameters {
                    formParams.forEach { entry ->
                        entry.value.forEach { append(entry.key, it) }
                    }
                })
            val httpResponseBody = runCatching { resp.bodyAsText() }.getOrNull()
            val isResponseRedirectUrl = httpResponseBody != null && httpResponseBody.take(10).lowercase().let {
                @Suppress("HttpUrlsUsage")
                it.startsWith("http://") || it.startsWith("https://")
            }
            logger.debug { "HTTP Response: $resp, body: $httpResponseBody" }
            val credentialService = CredentialsService()
            presentedCredentialIdList.forEach {
                credentialService.get(wallet.walletId, it)?.run {
                    eventUseCase.log(
                        action = EventType.Credential.Present,
                        originator = authReq.clientMetadata?.clientName
                            ?: EventDataNotAvailable,
                        tenant = wallet.tenant,
                        accountId = wallet.accountId,
                        walletId = wallet.walletId,
                        data = eventUseCase.credentialEventData(
                            credential = this,
                            subject = eventUseCase.subjectData(this),
                            organization = eventUseCase.verifierData(authReq),
                            type = null
                        ),
                        credentialId = this.id,
                    )
                }
            }

            val result = if (resp.status.value == 302 && !resp.headers["location"].toString().contains("error")) {
                Result.success(if (isResponseRedirectUrl) httpResponseBody else null)
            } else if (resp.status.isSuccess()) {
                Result.success(if (isResponseRedirectUrl) httpResponseBody else null)
            } else {
                if (isResponseRedirectUrl) {
                    Result.failure(
                        PresentationError(
                            message = "Presentation failed - redirecting to error page",
                            redirectUri = httpResponseBody
                        )
                    )
                } else {
                    logger.debug { "Response body: $httpResponseBody" }
                    Result.failure(
                        PresentationError(
                            message =
                            if (httpResponseBody != null) "Presentation failed:\n $httpResponseBody"
                            else "Presentation failed",
                            redirectUri = ""
                        )
                    )
                }
            }

            if (result.isSuccess) {
//                wallet.addOperationHistory(
//                    WalletOperationHistory.new(
//                        tenant = wallet.tenant,
//                        wallet = wallet,
//                        "external_signatures",
//                        mapOf(
//                            "did" to req.prepareOid4vpResponse.did,
//                            "request" to request,
//                            "selected-credentials" to selectedCredentialIds.joinToString(),
//                            "success" to "true",
//                            "redirect" to result.getOrThrow()
//                        ) // change string true to bool
//                    )
//                )

                context.respond(HttpStatusCode.OK, mapOf("redirectUri" to result.getOrThrow()))
            } else {
                val err = result.exceptionOrNull()
                println("Presentation failed: $err")

//                wallet.addOperationHistory(
//                    WalletOperationHistory.new(
//                        tenant = wallet.tenant,
//                        wallet = wallet,
//                        "usePresentationRequest",
//                        mapOf(
//                            "did" to did,
//                            "request" to request,
//                            "success" to "false",
//                            //"redirect" to ""
//                        ) // change string false to bool
//                    )
//                )
                when (err) {
                    is PresentationError -> {
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
    }
}

@Serializable
data class PrepareOID4VPRequest(
    val did: String,
    val presentationRequest: String,
    val selectedCredentialIdList: List<String>,
    val disclosures: Map<String, List<String>>? = null,
)

@Serializable
data class PrepareOID4VPResponse(
    val did: String,
    val presentationRequest: String,
    val sessionId: String,
    val resolvedAuthReq: AuthorizationRequest,
    val presentationSubmission: PresentationSubmission,
    val presentedCredentialIdList: List<String>,
    val vpTokenParams: UnsignedVPTokenParameters,
)

@Serializable
data class UnsignedVPTokenParameters(
    val header: Map<String, JsonElement>,
    val payload: String,
)

@Serializable
data class SubmitOID4VPRequest(
    val did: String,
    val signedVP: String,
    val presentationRequest: String,
    val sessionId: String,
    val resolvedAuthReq: AuthorizationRequest,
    val presentationSubmission: PresentationSubmission,
    val presentedCredentialIdList: List<String>,
)