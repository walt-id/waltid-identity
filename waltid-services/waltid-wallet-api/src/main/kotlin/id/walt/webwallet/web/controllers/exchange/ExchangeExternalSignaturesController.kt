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
import id.walt.crypto.utils.JwsUtils.decodeJwsOrSdjwt
import id.walt.oid4vc.data.*
import id.walt.oid4vc.data.dif.DescriptorMapping
import id.walt.oid4vc.data.dif.PresentationSubmission
import id.walt.oid4vc.data.dif.VCFormat
import id.walt.oid4vc.errors.AuthorizationError
import id.walt.oid4vc.requests.AuthorizationRequest
import id.walt.oid4vc.requests.CredentialRequest
import id.walt.oid4vc.responses.AuthorizationErrorCode
import id.walt.oid4vc.responses.TokenResponse
import id.walt.oid4vc.util.randomUUID
import id.walt.webwallet.db.models.WalletCredential
import id.walt.webwallet.service.SSIKit2WalletService.Companion.getCredentialWallet
import id.walt.webwallet.service.SSIKit2WalletService.PresentationError
import id.walt.webwallet.service.WalletServiceManager
import id.walt.webwallet.service.WalletServiceManager.eventUseCase
import id.walt.webwallet.service.credentials.CredentialsService
import id.walt.webwallet.service.dids.DidsService
import id.walt.webwallet.service.events.EventDataNotAvailable
import id.walt.webwallet.service.events.EventType
import id.walt.webwallet.service.exchange.CredentialOfferProcessor
import id.walt.webwallet.service.exchange.IssuanceService.CredentialDataResult
import id.walt.webwallet.utils.WalletHttpClients
import id.walt.webwallet.web.controllers.auth.getWalletService
import id.walt.webwallet.web.controllers.walletRoute
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.smiley4.ktorswaggerui.dsl.routing.post
import io.github.smiley4.ktorswaggerui.dsl.routing.route
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.util.*
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import kotlinx.uuid.UUID
import kotlinx.uuid.generateUUID

fun Application.exchangeExternalSignatures() = walletRoute {
    val logger = KotlinLogging.logger { }
    route(
        OpenAPICommons.rootPath,
        OpenAPICommons.route(),
    ) {
        post("external_signatures/presentation/prepare", {
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
            val walletService = getWalletService()

            val req = call.receive<PrepareOID4VPRequest>()
            logger.debug { "Request: $req" }

            if (req.selectedCredentialIdList.isEmpty())
                throw IllegalArgumentException("Unable to prepare oid4vp parameters with no input credential identifiers")

            val walletDID = DidsService.get(walletService.walletId, req.did)
                ?: throw IllegalArgumentException("did ${req.did} not found in wallet")
            logger.debug { "Retrieved wallet DID: $walletDID" }

            val credentialWallet = getCredentialWallet(walletDID.did)

            val authReq = AuthorizationRequest
                .fromHttpParametersAuto(
                    parseQueryString(
                        Url(
                            req.presentationRequest,
                        ).encodedQuery,
                    ).toMap()
                )
            logger.debug { "Auth req: $authReq" }

            logger.debug { "Selected credentials for presentation request: ${req.selectedCredentialIdList}" }

            val resolvedAuthReq = credentialWallet.resolveVPAuthorizationParameters(authReq)
            logger.debug { "Resolved Auth req: $resolvedAuthReq" }

            if (!credentialWallet.validateAuthorizationRequest(resolvedAuthReq)) {
                throw AuthorizationError(
                    resolvedAuthReq,
                    AuthorizationErrorCode.invalid_request,
                    message = "Invalid VP authorization request"
                )
            }
            val matchedCredentials = walletService.getCredentialsByIds(req.selectedCredentialIdList)
            logger.debug { "Matched credentials: $matchedCredentials" }

            val presentationId = "urn:uuid:" + UUID.generateUUID().toString().lowercase()

            val authKeyId = ExchangeUtils.getFirstAuthKeyIdFromDidDocument(walletDID.document)
            logger.debug { "Resolved authorization keyId: $authKeyId" }

            val vpPayload = credentialWallet.getVpJson(
                matchedCredentials.map { it.document },
                presentationId,
                resolvedAuthReq.nonce,
                resolvedAuthReq.clientId,
            )
            logger.debug { "Generated vp token payload: $vpPayload" }

            val vpHeader = mapOf(
                "kid" to authKeyId.toJsonElement(),
                "typ" to "JWT".toJsonElement()
            )
            logger.debug { "Generated vp token headers: $vpHeader" }

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
            logger.debug { "Generated presentation submission: $presentationSubmission" }

            val responsePayload = PrepareOID4VPResponse(
                walletDID.did,
                req.presentationRequest,
                resolvedAuthReq,
                presentationSubmission,
                req.selectedCredentialIdList,
                UnsignedVPTokenParameters(
                    vpHeader,
                    vpPayload,
                ),
            )
            logger.debug { "Response payload: $responsePayload" }

            context.respond(
                HttpStatusCode.OK,
                responsePayload,
            )
        }

        post("external_signatures/presentation/submit", {
            summary = "Submission (second) step of an OID4VP flow with externally provided signatures. " +
                    "The client is expected to provide the signed vp token in the respective input request field."

            request {
                body<SubmitOID4VPRequest> {
                }
            }
            response(OpenAPICommons.usePresentationRequestResponse())
        }) {
            val req = call.receive<SubmitOID4VPRequest>()
            logger.debug { "Request: $req" }

            val authReq = req.resolvedAuthReq
            val authResponseURL = authReq.responseUri
                ?: authReq.redirectUri ?: throw AuthorizationError(
                    authReq,
                    AuthorizationErrorCode.invalid_request,
                    "No response_uri or redirect_uri found on authorization request"
                )
            logger.debug { "Authorization response URL: $authResponseURL" }

            val presentationSubmission = req.presentationSubmission
            val presentedCredentialIdList = req.presentedCredentialIdList

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
            logger.debug { "Authorization response parameters: $formParams" }

            val httpClient = WalletHttpClients.getHttpClient()
            val resp = httpClient.submitForm(
                authResponseURL,
                parameters {
                    formParams.forEach { entry ->
                        entry.value.forEach { append(entry.key, it) }
                    }
                })

            val responseBody = runCatching { resp.bodyAsText() }.getOrNull()
            val isResponseRedirectUrl = responseBody != null && responseBody.take(10).lowercase().let {
                @Suppress("HttpUrlsUsage")
                it.startsWith("http://") || it.startsWith("https://")
            }
            logger.debug { "HTTP Response: $resp, body: $responseBody" }

            val walletService = getWalletService()
            val credentialService = CredentialsService()
            presentedCredentialIdList.forEach {
                credentialService.get(walletService.walletId, it)?.run {
                    eventUseCase.log(
                        action = EventType.Credential.Present,
                        originator = authReq.clientMetadata?.clientName
                            ?: EventDataNotAvailable,
                        tenant = walletService.tenant,
                        accountId = walletService.accountId,
                        walletId = walletService.walletId,
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
                Result.success(if (isResponseRedirectUrl) responseBody else null)
            } else if (resp.status.isSuccess()) {
                Result.success(if (isResponseRedirectUrl) responseBody else null)
            } else {
                if (isResponseRedirectUrl) {
                    Result.failure(
                        PresentationError(
                            message = "Presentation failed - redirecting to error page",
                            redirectUri = responseBody
                        )
                    )
                } else {
                    logger.debug { "Response body: $responseBody" }
                    Result.failure(
                        PresentationError(
                            message =
                            if (responseBody != null) "Presentation failed:\n $responseBody"
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
                logger.debug { "Presentation failed: $err" }

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
        post("external_signatures/offer/prepare", {
            summary = "Preparation (first) step for an OID4VCI flow with externally provided signatures."

            request(OpenAPICommons.useOfferRequestEndpointRequestParams())

            response {
                HttpStatusCode.OK to {
                    description = "Collection of parameters that are necessary to invoke the submit endpoint. " +
                            "The client is expected to, in between, sign a JWT based on the " +
                            "params object that is contained within."
                    body<PrepareOID4VCIResponse> {
                        required = true
                    }
                }
            }
        }) {
            val walletService = getWalletService()
            val offer = call.receiveText()
            logger.debug { "Request: queryParameters = ${call.request.queryParameters} body = $offer" }

            val did = call.request.queryParameters["did"] ?: walletService.listDids().firstOrNull()?.did
            ?: throw IllegalArgumentException("No DID to use supplied and no DID was found in wallet.")
            val requireUserInput = call.request.queryParameters["requireUserInput"].toBoolean()

            //this can't fail due to the above block
            val walletDID = DidsService.get(walletService.walletId, did)!!
            logger.debug { "Retrieved wallet DID: $walletDID" }
            val authKeyId = ExchangeUtils.getFirstAuthKeyIdFromDidDocument(walletDID.document)
            logger.debug { "Resolved authorization keyId: $authKeyId" }

            runCatching {
                WalletServiceManager.externalSignatureClaimStrategy.prepareCredentialClaim(
                    tenantId = walletService.tenant,
                    accountId = walletService.accountId,
                    walletId = walletService.walletId,
                    did = walletDID.did,
                    keyId = authKeyId,
                    offer = offer,
                )
            }.onSuccess { prepareClaimResult ->
                val responsePayload = PrepareOID4VCIResponse(
                    did = walletDID.did,
                    tokenResponse = prepareClaimResult.tokenResponse,
                    offeredCredentials = prepareClaimResult.offeredCredentials,
                    credentialIssuer = prepareClaimResult.resolvedCredentialOffer.credentialIssuer,
                    jwtParams = UnsignedProofOfPossessionParameters(
                        header = prepareClaimResult.jwtParams.header,
                        payload = prepareClaimResult.jwtParams.payload,
                    )
                )
                context.respond(
                    HttpStatusCode.OK,
                    responsePayload,
                )
            }.onFailure { error ->
                context.respond(HttpStatusCode.BadRequest, error.message ?: "Unknown error")
            }
        }

        post("external_signatures/offer/submit", {
            summary = "Submission (second) step for an OID4VCI flow with externally provided signatures."

            request {
                body<SubmitOID4VCIRequest> {

                }
            }

            response(OpenAPICommons.useOfferRequestEndpointResponseParams())
        }) {
            val walletService = getWalletService()
            val req = call.receive<SubmitOID4VCIRequest>()
            logger.debug { "Request: $req" }

            val credentialWallet = getCredentialWallet(req.did)
            val httpClient = WalletHttpClients.getHttpClient()

            logger.debug { "// get issuer metadata" }
            val providerMetadataUri =
                credentialWallet.getCIProviderMetadataUrl(req.credentialIssuer)
            logger.debug { "Getting provider metadata from: $providerMetadataUri" }
            val providerMetadataResult = httpClient.get(providerMetadataUri)
            val providerMetadataBody = providerMetadataResult.bodyAsText()
            logger.debug { "Provider metadata returned: $providerMetadataBody" }

            val providerMetadata =
                providerMetadataResult.body<JsonObject>().let { OpenIDProviderMetadata.fromJSON(it) }
            logger.debug { "providerMetadata: $providerMetadata" }


            val offeredCredentials = req.offeredCredentials
            val credReqs = offeredCredentials.map { offeredCredential ->
                CredentialRequest.forOfferedCredential(
                    offeredCredential = offeredCredential,
                    proof = ProofOfPossession.JWTProofBuilder(
                        req.credentialIssuer,
                        null,
                        null,
                        null,
                        null,
                    ).build(req.signedProofOfDIDPossession)
                )
            }
            logger.debug { "credReqs: $credReqs" }

            require(credReqs.isNotEmpty()) { "No credentials offered" }
            val processedCredentialOffers = CredentialOfferProcessor.process(credReqs, providerMetadata, req.tokenResponse)
            logger.debug { "// parse and verify credential(s)" }
            check(processedCredentialOffers.any { it.credentialResponse.credential != null }) { "No credential was returned from credentialEndpoint: $processedCredentialOffers" }
            val walletCredentialList = processedCredentialOffers.map { processedOffer ->
                val credential = processedOffer.credentialResponse.credential!!.jsonPrimitive.content
                val credentialFormat = processedOffer.credentialResponse.format
                val credentialParts = credential.decodeJwsOrSdjwt()
                logger.debug { "Got credential: $credentialParts" }

                val typ = credentialParts.jwsParts.header["typ"]?.jsonPrimitive?.content?.lowercase()
                    ?: error("Credential does not have `typ`!")

                val vc = credentialParts.jwsParts.payload["vc"]?.jsonObject ?: credentialParts.jwsParts.payload
                val credentialId = vc["id"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: randomUUID()

                val disclosures = credentialParts.sdJwtDisclosures
                logger.debug { "Disclosures (${disclosures.size}): $disclosures" }

                CredentialDataResult(
                    id = credentialId,
                    document = credentialParts.jwsParts.toString(),
                    disclosures = credentialParts.sdJwtDisclosuresString().drop(1), // remove first '~'
                    type = typ,
                    format = credentialFormat ?: error("No credential format")
                )
            }.map {
                WalletCredential(
                    wallet = walletService.walletId,
                    id = it.id,
                    document = it.document,
                    disclosures = it.disclosures,
                    addedOn = Clock.System.now(),
                    manifest = it.manifest,
                    deletedOn = null,
                    pending = false,
                    format = it.format,
                ).also { credential ->
                    eventUseCase.log(
                        action = EventType.Credential.Receive,
                        originator = "", //parsedOfferReq.credentialOffer!!.credentialIssuer,
                        tenant = walletService.tenant,
                        accountId = walletService.accountId,
                        walletId = walletService.walletId,
                        data = eventUseCase.credentialEventData(
                            credential = credential,
                            subject = eventUseCase.subjectData(credential),
                            organization = eventUseCase.issuerData(credential),
                            type = it.type
                        ),
                        credentialId = credential.id,
                    )
                }
            }.also {
                CredentialsService().add(
                    wallet = walletService.walletId, credentials = it.toTypedArray()
                )
            }


            context.respond(HttpStatusCode.OK, walletCredentialList)
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
    val resolvedAuthReq: AuthorizationRequest,
    val presentationSubmission: PresentationSubmission,
    val presentedCredentialIdList: List<String>,
)

@Serializable
data class PrepareOID4VCIResponse(
    val did: String,
    val tokenResponse: TokenResponse,
    val offeredCredentials: List<OfferedCredential>,
    val credentialIssuer: String,
    val jwtParams: UnsignedProofOfPossessionParameters,
)

typealias UnsignedProofOfPossessionParameters = UnsignedVPTokenParameters

@Serializable
data class SubmitOID4VCIRequest(
    val did: String,
    val tokenResponse: TokenResponse,
    val offeredCredentials: List<OfferedCredential>,
    val credentialIssuer: String,
    val signedProofOfDIDPossession: String,
)