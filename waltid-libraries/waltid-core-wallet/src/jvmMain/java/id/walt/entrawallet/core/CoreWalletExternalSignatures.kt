package id.walt.entrawallet.core

/*
import id.walt.wallet.core.service.SSIKit2WalletService
import id.walt.webwallet.web.controllers.exchange.models.oid4vci.PrepareOID4VCIRequest
import id.walt.webwallet.web.controllers.exchange.models.oid4vci.PrepareOID4VCIResponse
import id.walt.webwallet.web.controllers.exchange.models.oid4vci.SubmitOID4VCIRequest
import id.walt.webwallet.web.controllers.exchange.models.oid4vp.PrepareOID4VPRequest
import id.walt.webwallet.web.controllers.exchange.models.oid4vp.PrepareOID4VPResponse
import id.walt.webwallet.web.controllers.exchange.models.oid4vp.SubmitOID4VPRequest
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.JsonObject

object CoreWalletExternalSignatures {

    val logger = KotlinLogging.logger()

    */
/**
     * Preparation (first) step for an OID4VP flow with externally provided signatures.
     * @param req Credential (W3C Verifiable Credential / W3C SD-JWT Verifiable Credential / IETF SD-JWT Verifiable Credential)
     *
     * @return Collection of parameters that are necessary to invoke the submit endpoint.
     * The client is expected to, in between, sign the vp token based on the vpTokenParams object that is contained within.
     *//*

    fun externalSignaturePresentationPrepare(req: PrepareOID4VPRequest): PrepareOID4VPResponse {
        val walletService = getWalletService()

        logger.debug { "Request: $req" }

        if (req.selectedCredentialIdList.isEmpty())
            throw IllegalArgumentException("Unable to prepare oid4vp parameters with no input credential identifiers")

        val walletDID = DidsService.get(walletService.walletId, req.did)
            ?: throw IllegalArgumentException("did ${req.did} not found in wallet")
        logger.debug { "Retrieved wallet DID: $walletDID" }

        val authReq = AuthorizationRequest
            .fromHttpParametersAuto(
                parseQueryString(
                    Url(
                        req.presentationRequest,
                    ).encodedQuery,
                ).toMap()
            )
        logger.debug { "Auth req: $authReq" }
        authReq.responseUri ?: authReq.redirectUri ?: throw AuthorizationError(
            authReq,
            AuthorizationErrorCode.invalid_request,
            "No response_uri or redirect_uri found on authorization request"
        )

        logger.debug { "Selected credentials for presentation request: ${req.selectedCredentialIdList}" }

        val credentialWallet = getCredentialWallet(walletDID.did)
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

        val presentationId = "urn:uuid:" + Uuid.random().toString().lowercase()
        val keyId = walletDID.keyId
        logger.debug { "keyId: $keyId" }
        val didFirstAuthKeyId = ExchangeUtils.getFirstAuthKeyIdFromDidDocument(walletDID.document)
        logger.debug { "Resolved first did authentication keyId: $didFirstAuthKeyId" }

        //this is not really correct, we need to actually compute the proof parameters
        //based on the Verifier's client metadata, but...
        val w3cJwtVpTokenParams = ExchangeUtils.getW3cJwtVpProofParametersFromWalletCredentials(
            walletDID.did,
            didFirstAuthKeyId,
            presentationId,
            resolvedAuthReq.clientId,
            resolvedAuthReq.nonce,
            matchedCredentials,
            req.disclosures,
        )
        val ietfVpTokenParams = ExchangeUtils.getIETFJwtVpProofParametersFromWalletCredentials(
            keyId,
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

    }

    */
/**
     * Submission (second) step of an OID4VP flow with externally provided signatures.
     * The client is expected to provide the signed vp token in the respective input request field.
     *
     * @param req W3C Verifiable Credential / W3C SD-JWT Verifiable Credential / IETF SD-JWT Verifiable Credential
     * @return Presentation result
     *//*

    fun externalSignaturePresentationSubmit(req: SubmitOID4VPRequest): JsonObject {
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
            val presentedCredentialIdList = req.selectedCredentialIdList

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
                credentialService.get(walletService.walletId, it)
            }

            if ((resp.status.value == 302 && !resp.headers["location"].toString().contains("error")) ||
                resp.status.isSuccess()
            ) {
                responseBody
            } else {
                //this logic is incorrect....
                if (isResponseRedirectUrl) {
                    throw SSIKit2WalletService.PresentationError(
                        message = "Presentation failed - redirecting to error page",
                        redirectUri = responseBody
                    )
                } else {
                    throw SSIKit2WalletService.PresentationError(
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

    */
/**
     * Preparation (first) step for an OID4VCI flow with externally provided signatures.
     *
     * @param req W3C Verifiable Credential / IETF SD-JWT Verifiable Credential / mDoc Verifiable Credential
     *
     * @return Collection of parameters that are necessary to invoke the respective submit endpoint.
     * For each offered credential, the client is expected to compute a signature based on the provided
     * proof of possession parameters.
     *//*

    fun externalSignaturesOfferPrepare(req: PrepareOID4VCIRequest): PrepareOID4VCIResponse {
        val walletService = getWalletService()
        val offer = req.offerURL
        logger.debug { "Request: $req" }

        val walletDID = req.did?.let {
            DidsService.get(walletService.walletId, req.did)
        } ?: walletService.listDids().firstOrNull()
        ?: throw IllegalArgumentException("No DID to use supplied and no DID was found in wallet.")
        logger.debug { "Retrieved wallet DID: $walletDID" }
        val didFirstAuthKeyId = ExchangeUtils.getFirstAuthKeyIdFromDidDocument(walletDID.document)
        logger.debug { "Resolved first did authentication keyId: $didFirstAuthKeyId" }
        val authPublicKey = KeysService.get(walletService.walletId, walletDID.keyId)?.let {
            runCatching {
                KeyManager.resolveSerializedKey(it.document).getPublicKey()
            }.fold(
                onSuccess = {
                    it.getPublicKey()
                },
                onFailure = { error ->
                    throw IllegalStateException(
                        "Failed to parse wallet's ${walletService.walletId} " +
                                "serialized key ${walletDID.keyId}: ${error.message}"
                    )
                }
            )
        }
            ?: throw NotFoundException("Unable to retrieve/find key ${walletDID.keyId} from wallet ${walletService.walletId}")
        logger.debug { "Retrieved auth public key: $authPublicKey" }
        WalletServiceManager.externalSignatureClaimStrategy.prepareCredentialClaim(
            did = walletDID.did,
            didAuthKeyId = didFirstAuthKeyId,
            publicKey = authPublicKey,
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
    }

    */
/**
     * Submission (second) step for an OID4VCI flow with externally provided signatures.
     *
     * @return List of credentials
     *//*

    fun externalSignaturesOfferSubmit(req: SubmitOID4VCIRequest):  {
        val walletService = getWalletService()

        val req = call.receive<SubmitOID4VCIRequest>()
        logger.debug { "Request: $req" }

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
    }

}
*/
