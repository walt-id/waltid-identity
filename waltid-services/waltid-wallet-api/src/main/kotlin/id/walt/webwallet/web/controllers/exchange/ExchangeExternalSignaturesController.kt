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
import id.walt.sdjwt.KeyBindingJwt.Companion.getSdHash
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
import id.walt.webwallet.service.oidc4vc.CredentialFilterUtils
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
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
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

            val req = call.receive<PrepareOID4VPRequest>()
            logger.debug { "Request: $req" }

            if (req.selectedCredentialIdList.isEmpty())
                throw IllegalArgumentException("Unable to prepare oid4vp parameters with no input credential identifiers")

            val walletDID = DidsService.get(walletService.walletId, req.did)
                ?: throw IllegalArgumentException("did ${req.did} not found in wallet")
            logger.debug { "Retrieved wallet DID: $walletDID" }

            val credentialWallet = getCredentialWallet(walletDID.did)

//            val authKeyId = ExchangeUtils.getFirstAuthKeyIdFromDidDocument(walletDID.document).getOrThrow()
//            val key = runBlocking {
//                runCatching {
//                    DidService.resolveToKey(walletDID.did).getOrThrow().let { KeysService.get(it.getKeyId()) }
//                        ?.let { KeyManager.resolveSerializedKey(it.document) }
//                }
//            }.getOrElse {
//                throw IllegalArgumentException("Could not resolve key to sign JWS to generate presentation for vp_token", it)
//            } ?: error("No key was resolved when trying to resolve key to sign JWS to generate presentation for vp_token")
            val authKeyId = walletDID.keyId
            logger.debug { "Resolved authorization keyId: $authKeyId" }

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

            val jwtVcsPresented = CredentialFilterUtils.getJwtVcList(matchedCredentials, req.disclosures)
            println("jwtsPresented: $jwtVcsPresented")
            var tokenParamsList = mutableListOf<TokenParams>()

            //sd_jwt_vc
            val sdJwtVcsPresented = matchedCredentials.filter { it.format == CredentialFormat.sd_jwt_vc }.map {
                // TODO: adopt selective disclosure selection (doesn't work with jwts other than sd_jwt anyway, like above)
//                val documentWithDisclosures = if (req.disclosures?.containsKey(it.id) == true) {
//                    it.document + "~${req.disclosures[it.id]!!.joinToString("~")}"
//                } else {
//                    it.document
//                }
//                val sdJwtString = if (req.disclosures?.containsKey(it.id) == true) {
//                    it.document + "~${req.disclosures[it.id]!!.joinToString("~")}"
//                } else {
//                    "${it.document}"
//                }
                //auto douleuei otan pernoume xima OLA TA DISCLOSURES APO MONOI MAS
//                val credentialOla = it.document + (if (it.disclosures != null) "~${it.disclosures}~" else "~")
                ////
                val credentialOla = it.document + (if (req.disclosures?.containsKey(it.id) == true) "~${req.disclosures[it.id]!!.joinToString("~")}~" else "~")
//                val sdjwtvc = SDJwtVC.parse(documentWithDisclosures)
//                val katiSdJwt = sdjwtvc.present(true).toString()
                val payload = buildJsonObject {
                    put("iat", JsonPrimitive(Clock.System.now().epochSeconds))
                    put("aud", resolvedAuthReq.clientId)
                    put("nonce", resolvedAuthReq.nonce ?: "")
                    put("sd_hash", getSdHash(credentialOla))
                }
                val header = mapOf(
                    "kid" to authKeyId.toJsonElement(),
                    "typ" to "kb+jwt".toJsonElement()
                )
                TokenParams(
                    credentialId = it.id,
                    header = header,
                    payload = payload,
                )


//                SDJwtVC.parse(documentWithDisclosures).present(
//                    true, audience = session.authorizationRequest.clientId, nonce = session.nonce ?: "",
//                    WaltIdJWTCryptoProvider(mapOf(key.getKeyId() to key)), key.getKeyId()
//                )
            }
            println("sdJwtVCsPresented: $sdJwtVcsPresented")
            tokenParamsList.addAll(sdJwtVcsPresented)
            //

            val presentationId = "urn:uuid:" + UUID.generateUUID().toString().lowercase()
            if (jwtVcsPresented.isNotEmpty()) {
                val vpPayload = credentialWallet.getVpJson(
                    jwtVcsPresented,
                    presentationId,
                    resolvedAuthReq.nonce,
                    resolvedAuthReq.clientId,
                )
                val vpHeader = mapOf(
                    "kid" to authKeyId.toJsonElement(),
                    "typ" to "JWT".toJsonElement()
                )
                val vpTokenParams = TokenParams(
                    header = vpHeader,
                    payload = Json.decodeFromString<Map<String, JsonElement>>(vpPayload),
                )
                logger.debug { "Generated vp token params: $vpTokenParams" }
                tokenParamsList.add(vpTokenParams)
            }

            val rootPathVP = "$" + (if (tokenParamsList.size == 2) "[0]" else "")
//            val rootPathMDoc = "$" + (if (presentations.size == 2) "[1]" else "")
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

            val responsePayload = PrepareOID4VPResponse(
                walletDID.did,
                req.presentationRequest,
                resolvedAuthReq,
                presentationSubmission,
                req.selectedCredentialIdList,
                req.disclosures,
                tokenParamsList,
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
            val walletService = getWalletService()
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
            val katiTransformed = req.signedTokens.map { signedToken ->
                if (signedToken.credentialId != null) {
                    val credential = walletService.getCredential(signedToken.credentialId)
                    if (credential.format == CredentialFormat.sd_jwt_vc) {
                        //auto douleuei otan pernoume xima OLA TA DISCLOSURES APO MONOI MAS
//                        credential.document +
//                                (if (credential.disclosures != null) "~${credential.disclosures}~" else "~") +
//                                signedToken.signedToken
                        //
                        credential.document +
                                (if (req.disclosures?.containsKey(credential.id) == true) "~${req.disclosures[credential.id]!!.joinToString("~")}~" else "~") +
                                signedToken.signedToken
                    } else {
                        signedToken.signedToken
                    }
                } else {
                    signedToken.signedToken
                }
            }

            val tokenResponse = if (katiTransformed.size == 1) {
                TokenResponse.success(
                    vpToken = VpTokenParameter.fromJsonElement(katiTransformed.first().toJsonElement()),
                    presentationSubmission = presentationSubmission,
                    idToken = null,
                    state = authReq.state,
                )
            } else {
                TokenResponse.success(
                    vpToken = JsonArray(katiTransformed.map { it.toJsonElement() }).let {
                        VpTokenParameter.fromJsonElement(
                            it
                        )
                    },
                    presentationSubmission = presentationSubmission,
                    idToken = null,
                    state = authReq.state,
                )
            }
            println("kati: $katiTransformed")
            println("token response: $tokenResponse")
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
    val disclosures: Map<String, List<String>>? = null,
    val tokenParams: List<TokenParams>,
//    val vpTokenParams: UnsignedVPTokenParameters,
)

@Serializable
data class UnsignedVPTokenParameters(
    val header: Map<String, JsonElement>,
    val payload: String,
)

@Serializable
data class SubmitOID4VPRequest(
    val did: String,
    val signedTokens: List<SignedToken>,
    val presentationRequest: String,
    val resolvedAuthReq: AuthorizationRequest,
    val presentationSubmission: PresentationSubmission,
    val presentedCredentialIdList: List<String>,
    val disclosures: Map<String, List<String>>? = null,
)

@Serializable
data class PrepareOID4VCIRequest(
    val did: String? = null,
    val offerURL: String,
)

@Serializable
data class PrepareOID4VCIResponse(
    val did: String? = null,
    val offerURL: String,
    val accessToken: String? = null,
    val offeredCredentialsProofRequests: List<IssuanceServiceExternalSignatures.OfferedCredentialProofOfPossessionParameters>,
    val credentialIssuer: String,
)

@Serializable
data class SubmitOID4VCIRequest(
    val did: String? = null,
    val offerURL: String,
    val requireUserInput: Boolean? = false,
    val accessToken: String? = null,
    val offeredCredentialProofsOfPossession: List<IssuanceServiceExternalSignatures.OfferedCredentialProofOfPossession>,
    val credentialIssuer: String,
)

@Serializable
data class TokenParams(
    val credentialId: String? = null,
    val header: Map<String, JsonElement>,
    val payload: Map<String, JsonElement>,
)

@Serializable
data class SignedToken(
    val credentialId: String? = null,
    val signedToken: String,
)