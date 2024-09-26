package id.walt.webwallet.web.controllers.exchange

import com.nimbusds.jose.jwk.JWK
import com.nimbusds.jose.jwk.KeyUse
import com.nimbusds.jose.util.Base64URL
import id.walt.crypto.keys.KeyGenerationRequest
import id.walt.crypto.keys.KeyManager
import id.walt.crypto.keys.KeyType
import id.walt.crypto.utils.JsonUtils.toJsonElement
import id.walt.oid4vc.data.*
import id.walt.oid4vc.data.dif.PresentationSubmission
import id.walt.oid4vc.errors.AuthorizationError
import id.walt.oid4vc.requests.AuthorizationRequest
import id.walt.oid4vc.responses.AuthorizationErrorCode
import id.walt.oid4vc.responses.TokenResponse
import id.walt.webwallet.service.SSIKit2WalletService.Companion.getCredentialWallet
import id.walt.webwallet.service.SSIKit2WalletService.PresentationError
import id.walt.webwallet.service.WalletServiceManager
import id.walt.webwallet.service.WalletServiceManager.eventUseCase
import id.walt.webwallet.service.credentials.CredentialsService
import id.walt.webwallet.service.dids.DidsService
import id.walt.webwallet.service.events.EventDataNotAvailable
import id.walt.webwallet.service.events.EventType
import id.walt.webwallet.service.exchange.IssuanceServiceExternalSignatures
import id.walt.webwallet.service.exchange.ProofOfPossessionParameters
import id.walt.webwallet.utils.WalletHttpClients
import id.walt.webwallet.web.controllers.auth.getWalletService
import id.walt.webwallet.web.controllers.exchange.models.oid4vci.PrepareOID4VCIRequest
import id.walt.webwallet.web.controllers.exchange.models.oid4vci.PrepareOID4VCIResponse
import id.walt.webwallet.web.controllers.exchange.models.oid4vci.SubmitOID4VCIRequest
import id.walt.webwallet.web.controllers.exchange.models.oid4vp.PrepareOID4VPRequest
import id.walt.webwallet.web.controllers.exchange.models.oid4vp.PrepareOID4VPResponse
import id.walt.webwallet.web.controllers.exchange.models.oid4vp.SubmitOID4VPRequest
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
import kotlinx.serialization.json.*
import kotlinx.uuid.UUID
import kotlinx.uuid.generateUUID

fun Application.exchangeExternalSignatures() = walletRoute {
    val logger = KotlinLogging.logger { }
    route(
        OpenAPICommons.exchangeRootPath,
        OpenAPICommons.exchangeRoute(),
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
            runCatching {
                val req = call.receive<PrepareOID4VPRequest>()
                logger.debug { "Request: $req" }

                if (req.selectedCredentialIdList.isEmpty())
                    throw IllegalArgumentException("Unable to prepare oid4vp parameters with no input credential identifiers")

                val walletDID = DidsService.get(walletService.walletId, req.did)
                    ?: throw IllegalArgumentException("did ${req.did} not found in wallet")
                logger.debug { "Retrieved wallet DID: $walletDID" }

                val credentialWallet = getCredentialWallet(walletDID.did)
                val presentationId = "urn:uuid:" + UUID.generateUUID().toString().lowercase()
                val authKeyId = walletDID.keyId
                logger.debug { "Resolved authorization keyId: $authKeyId" }
                val didAuthKeyId = ExchangeUtils.getFirstAuthKeyIdFromDidDocument(walletDID.document).getOrThrow()
                logger.debug { "Resolved authorization keyId: $didAuthKeyId" }

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

                //NEW CODE
                val w3cJwtVpTokenParams = ExchangeUtils.getW3cJwtVpProofParametersFromWalletCredentials(
                    walletDID.did,
                    didAuthKeyId,
                    presentationId,
                    resolvedAuthReq.clientId,
                    resolvedAuthReq.nonce,
                    matchedCredentials,
                    req.disclosures,
                )
                val ietfVpTokenParams = ExchangeUtils.getIETFJwtVpProofParametersFromWalletCredentials(
                    authKeyId,
                    resolvedAuthReq.clientId,
                    resolvedAuthReq.nonce,
                    matchedCredentials,
                    req.disclosures,
                )

                val (rootPathVP, rootPathMDoc) = if (ietfVpTokenParams != null && w3cJwtVpTokenParams == null) {
                    Pair("$", "$[0]")
                } else if (ietfVpTokenParams != null) {
                    Pair("$[0]", "$[1]")
                } else {
                    Pair("$", "$[0]")
                }
                val presentationSubmission = PresentationSubmission(
                    id = presentationId,
                    definitionId = presentationId,
                    descriptorMap = matchedCredentials.mapIndexed { index, credential ->
                        when (credential.format) {
                            CredentialFormat.sd_jwt_vc -> {
                                credentialWallet.buildDescriptorMappingSDJwtVC(
                                    resolvedAuthReq.presentationDefinition,
                                    index,
                                    credential.document,
                                    "$",
                                )
                            }

                            CredentialFormat.mso_mdoc -> {
                                throw IllegalStateException("mDocs are not supported yet")
                            }

                            else -> {
                                credentialWallet.buildDescriptorMappingJwtVP(
                                    resolvedAuthReq.presentationDefinition,
                                    index,
                                    credential.document,
                                    rootPathVP,
                                )
                            }
                        }
                    }
                )
                logger.debug { "Generated presentation submission: $presentationSubmission" }
                PrepareOID4VPResponse.build(
                    req,
                    presentationSubmission,
                    w3CJwtVpProofParameters = w3cJwtVpTokenParams,
                    ietfSdJwtVpProofParameters = ietfVpTokenParams,
                )

            }.onSuccess { responsePayload ->
                logger.debug { "Response payload: $responsePayload" }
                context.respond(
                    HttpStatusCode.OK,
                    responsePayload,
                )

            }.onFailure { error ->
                logger.debug { "error: $error" }
                context.respond(
                    HttpStatusCode.BadRequest,
                    error.message ?: "Unknown error",
                )
            }
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
            val walletService = getWalletService()

            runCatching {
                val req = call.receive<SubmitOID4VPRequest>()
                logger.debug { "Request: $req" }

                val authReq = AuthorizationRequest
                    .fromHttpParametersAuto(
                        parseQueryString(
                            Url(
                                req.presentationRequest,
                            ).encodedQuery,
                        ).toMap()
                    )
                logger.debug { "Auth req: $authReq" }
                val authResponseURL = authReq.responseUri
                    ?: authReq.redirectUri ?: throw AuthorizationError(
                        authReq,
                        AuthorizationErrorCode.invalid_request,
                        "No response_uri or redirect_uri found on authorization request"
                    )
                logger.debug { "Authorization response URL: $authResponseURL" }
                val presentationSubmission = req.presentationSubmission
                val presentedCredentialIdList = req.presentedCredentialIdList

                val vpTokenProofs = (if (req.ietfSdJwtVpProofs != null) {
                    req.ietfSdJwtVpProofs.map { ietfVpProof ->
                        ietfVpProof.sdJwtVc + ietfVpProof.vpTokenProof
                    }
                } else {
                    listOf("")
                }).plus(req.w3cJwtVpProof ?: "").filter { it.isNotEmpty() }
                logger.debug { "vpTokenProofs: $vpTokenProofs" }

                val tokenResponse = if (vpTokenProofs.size == 1) {
                    TokenResponse.success(
                        vpToken = VpTokenParameter.fromJsonElement(vpTokenProofs.first().toJsonElement()),
                        presentationSubmission = presentationSubmission,
                        idToken = null,
                        state = authReq.state,
                    )
                } else {
                    TokenResponse.success(
                        vpToken = JsonArray(vpTokenProofs.map { it.toJsonElement() }).let {
                            VpTokenParameter.fromJsonElement(
                                it
                            )
                        },
                        presentationSubmission = presentationSubmission,
                        idToken = null,
                        state = authReq.state,
                    )
                }

                logger.debug { "token response: $tokenResponse" }
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

                val responseBody = resp.bodyAsText()
                val isResponseRedirectUrl = responseBody.contains("redirect_uri")
                logger.debug { "HTTP Response: $resp, body: $responseBody" }

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

                if ((resp.status.value == 302 && !resp.headers["location"].toString().contains("error")) ||
                    resp.status.isSuccess()
                ) {
                    responseBody
                } else {
                    //this logic is incorrect....
                    if (isResponseRedirectUrl) {
                        throw PresentationError(
                            message = "Presentation failed - redirecting to error page",
                            redirectUri = responseBody
                        )
                    } else {
                        throw PresentationError(
                            message =
                            if (responseBody.isNotBlank()) "Presentation failed:\n $responseBody"
                            else "Presentation failed",
                            redirectUri = ""
                        )
                    }
                }
            }.onSuccess {
                context.respond(
                    HttpStatusCode.OK,
                    it,
                )
            }.onFailure { error ->
                logger.debug { "error: $error" }
                when (error) {
                    is PresentationError -> {
                        context.respond(
                            HttpStatusCode.BadRequest,
                            mapOf(
                                "redirectUri" to error.redirectUri,
                                "errorMessage" to error.message,
                            ),
                        )
                    }

                    else -> {
                        context.respond(
                            HttpStatusCode.BadRequest,
                            mapOf(
                                "errorMessage" to error.message
                            ),
                        )
                    }
                }
            }
        }
        post("external_signatures/offer/prepare", {
            summary = "Preparation (first) step for an OID4VCI flow with externally provided signatures."

            request {
                body<PrepareOID4VCIRequest> {
                }
            }

            response {
                HttpStatusCode.OK to {
                    description =
                        "Collection of parameters that are necessary to invoke the respective submit endpoint. " +
                                "For each offered credential, the client is expected to compute a signature based on the provided " +
                                "proof of possession parameters."
                    body<PrepareOID4VCIResponse> {
                        required = true
                        example("When proofType == cwt") {
                            value = PrepareOID4VCIResponse(
                                did = "did:web:walt.id",
                                offerURL = "openid-credential-offer://?credential_offer=",
                                offeredCredentialsProofRequests = listOf(
                                    IssuanceServiceExternalSignatures.OfferedCredentialProofOfPossessionParameters(
                                        OfferedCredential(
                                            format = CredentialFormat.mso_mdoc,
                                        ),
                                        ProofOfPossessionParameters(
                                            ProofType.cwt,
                                            "<<JSON-ENCODED BYTE ARRAY OF CBOR MAP>>".toJsonElement(),
                                            "<<JSON-ENCODED BYTE ARRAY OF CBOR MAP>>".toJsonElement(),
                                        )
                                    )
                                ),
                                credentialIssuer = "https://issuer.portal.walt.id"
                            )
                        }
                        example("When proofType == jwt") {
                            value = PrepareOID4VCIResponse(
                                did = "did:web:walt.id",
                                offerURL = "openid-credential-offer://?credential_offer=",
                                offeredCredentialsProofRequests = listOf(
                                    IssuanceServiceExternalSignatures.OfferedCredentialProofOfPossessionParameters(
                                        OfferedCredential(
                                            format = CredentialFormat.jwt_vc_json,
                                        ),
                                        ProofOfPossessionParameters(
                                            ProofType.jwt,
                                            "<<JWT HEADER SECTION>>".toJsonElement(),
                                            "<<JWT CLAIMS SECTION>>".toJsonElement(),
                                        )
                                    )
                                ),
                                credentialIssuer = "https://issuer.portal.walt.id"
                            )
                        }
                    }
                }
            }
        }) {
            val walletService = getWalletService()
            val req = call.receive<PrepareOID4VCIRequest>()
            val offer = req.offerURL
            logger.debug { "Request: $req" }

            runCatching {
                val walletDID = req.did?.let {
                    DidsService.get(walletService.walletId, req.did)
                } ?: walletService.listDids().firstOrNull()
                ?: throw IllegalArgumentException("No DID to use supplied and no DID was found in wallet.")
                logger.debug { "Retrieved wallet DID: $walletDID" }
                val authKeyId = ExchangeUtils.getFirstAuthKeyIdFromDidDocument(walletDID.document).getOrThrow()
                logger.debug { "Resolved did authorization keyId: $authKeyId" }
                WalletServiceManager.externalSignatureClaimStrategy.prepareCredentialClaim(
                    did = walletDID.did,
                    keyId = authKeyId,
                    offerURL = offer,
                ).let { prepareClaimResult ->
                    PrepareOID4VCIResponse(
                        did = walletDID.did,
                        offerURL = req.offerURL,
                        accessToken = prepareClaimResult.accessToken,
                        offeredCredentialsProofRequests = prepareClaimResult.offeredCredentialsProofRequests,
                        credentialIssuer = prepareClaimResult.resolvedCredentialOffer.credentialIssuer,
                    )
                }
            }.onSuccess { responsePayload ->
                context.respond(
                    HttpStatusCode.OK,
                    responsePayload,
                )
            }.onFailure { error ->
                context.respond(
                    HttpStatusCode.BadRequest,
                    error.message ?: "Unknown error",
                )
            }
        }

        post("external_signatures/offer/submit", {
            summary = "Submission (second) step for an OID4VCI flow with externally provided signatures."

            request {
                body<SubmitOID4VCIRequest> {
                    required = true
                    example("When proofType == cwt") {
                        value = SubmitOID4VCIRequest(
                            did = "did:web:walt.id",
                            offerURL = "openid-credential-offer://?credential_offer=",
                            offeredCredentialProofsOfPossession = listOf(
                                IssuanceServiceExternalSignatures.OfferedCredentialProofOfPossession(
                                    OfferedCredential(
                                        format = CredentialFormat.mso_mdoc,
                                    ),
                                    ProofType.cwt,
                                    "<<BASE64URL-ENCODED SIGNED CWT>>",
                                )
                            ),
                            credentialIssuer = "https://issuer.portal.walt.id"
                        )
                    }
                    example("When proofType == jwt") {
                        value = SubmitOID4VCIRequest(
                            did = "did:web:walt.id",
                            offerURL = "openid-credential-offer://?credential_offer=",
                            offeredCredentialProofsOfPossession = listOf(
                                IssuanceServiceExternalSignatures.OfferedCredentialProofOfPossession(
                                    OfferedCredential(
                                        format = CredentialFormat.jwt_vc_json,
                                    ),
                                    ProofType.jwt,
                                    "<<COMPACT-SERIALIZED SIGNED JWT>>",
                                )
                            ),
                            credentialIssuer = "https://issuer.portal.walt.id"
                        )
                    }
                }
            }

            response(OpenAPICommons.useOfferRequestEndpointResponseParams())
        }) {
            val walletService = getWalletService()
            val req = call.receive<SubmitOID4VCIRequest>()
            logger.debug { "Request: $req" }

            runCatching {
                val did = req.did ?: walletService.listDids().firstOrNull()?.did
                ?: throw IllegalArgumentException("No DID to use supplied and no DID was found in wallet.")
                WalletServiceManager
                    .externalSignatureClaimStrategy
                    .submitCredentialClaim(
                        tenantId = walletService.tenant,
                        accountId = walletService.accountId,
                        walletId = walletService.walletId,
                        pending = req.requireUserInput ?: true,
                        did = did,
                        offerURL = req.offerURL,
                        credentialIssuerURL = req.credentialIssuer,
                        accessToken = req.accessToken,
                        offeredCredentialProofsOfPossession = req.offeredCredentialProofsOfPossession,
                    )
            }.onSuccess { walletCredentialList ->
                context.respond(
                    HttpStatusCode.OK,
                    walletCredentialList,
                )
            }.onFailure { error ->
                context.respond(
                    HttpStatusCode.BadRequest,
                    error.message ?: "Unknown error",
                )
            }
        }
    }
}
