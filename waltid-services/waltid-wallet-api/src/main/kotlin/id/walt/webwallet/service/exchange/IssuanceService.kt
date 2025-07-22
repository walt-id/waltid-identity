package id.walt.webwallet.service.exchange

import id.walt.did.dids.DidService
import id.walt.oid4vc.OpenID4VCI
import id.walt.oid4vc.data.CredentialFormat
import id.walt.oid4vc.data.CredentialOffer
import id.walt.oid4vc.data.GrantType
import id.walt.oid4vc.data.OpenIDProviderMetadata
import id.walt.oid4vc.providers.TokenTarget
import id.walt.oid4vc.requests.AuthorizationRequest
import id.walt.oid4vc.requests.CredentialRequest
import id.walt.oid4vc.requests.EntraIssuanceRequest
import id.walt.oid4vc.requests.TokenRequest
import id.walt.oid4vc.responses.CredentialResponse
import id.walt.oid4vc.responses.EntraIssuanceCompletionCode
import id.walt.oid4vc.responses.EntraIssuanceCompletionErrorDetails
import id.walt.oid4vc.responses.EntraIssuanceCompletionResponse
import id.walt.webwallet.manifest.extractor.EntraManifestExtractor
import id.walt.webwallet.service.oidc4vc.TestCredentialWallet
import io.klogging.logger
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object IssuanceService : IssuanceServiceBase() {

    override val logger = logger<IssuanceService>()

    suspend fun useOfferRequest(
        offer: String,
        credentialWallet: TestCredentialWallet,
        pinOrTxCode: String? = null,
    ) = let {
        logger.debug { "// -------- WALLET ----------" }
        logger.debug { "// as WALLET: receive credential offer, either being called via deeplink or by scanning QR code" }
        logger.debug { "// parse credential URI" }

        // entra or openid4vc credential offer
        val isEntra = EntraIssuanceRequest.isEntraIssuanceRequestUri(offer)

        val processedCredentialOffers = if (isEntra) {
            processMSEntraIssuanceRequest(
                credentialOffer = offer,
                credentialWallet = credentialWallet
            )
        } else {
            processCredentialOffer(
                credentialOffer = OpenID4VCI.parseAndResolveCredentialOfferRequestUrl(offer),
                credentialWallet = credentialWallet,
                pinOrTxCode = pinOrTxCode,
            )
        }

        // === original ===
        logger.debug { "// parse and verify credential(s)" }
        check(processedCredentialOffers.any { it.credentialResponse.credential != null }) { "No credential was returned from credentialEndpoint: $processedCredentialOffers" }

        // ??multiple credentials manifests
        val manifest =
            isEntra.takeIf { it }?.let { EntraManifestExtractor().extract(offer) }

        processedCredentialOffers.map {
            getCredentialData(
                processedOffer = it,
                manifest = manifest
            )
        }
    }

    private suspend fun processCredentialOffer(
        credentialOffer: CredentialOffer,
        credentialWallet: TestCredentialWallet,
        pinOrTxCode: String? = null,
    ): List<ProcessedCredentialOffer> {

        logger.debug { "credentialOffer: $credentialOffer" }

        val providerMetadata = OpenID4VCI.resolveCIProviderMetadata(credentialOffer)

        logger.debug { "providerMetadata: $providerMetadata" }

        logger.debug { "// resolve offered credentials" }
        val offeredCredentials = OpenID4VCI.resolveOfferedCredentials(
            credentialOffer = credentialOffer,
            providerMetadata = providerMetadata
        )
        logger.debug { "offeredCredentials: $offeredCredentials" }

        require(offeredCredentials.isNotEmpty()) { "Resolved an empty list of offered credentials" }

        logger.debug { "// fetch access token using pre-authorized code (skipping authorization step)" }

        val grant = credentialOffer.grants[GrantType.pre_authorized_code.value]!!
        val preAuthorizedCode = grant.preAuthorizedCode!!
        val tokenReq = when {
            grant.userPinRequired != null && grant.userPinRequired != true -> TokenRequest.PreAuthorizedCode(
                preAuthorizedCode
            )

            providerMetadata is OpenIDProviderMetadata.Draft11 -> TokenRequest.PreAuthorizedCode(
                preAuthorizedCode = preAuthorizedCode,
                userPIN = pinOrTxCode
            )

            else -> TokenRequest.PreAuthorizedCode(
                preAuthorizedCode = preAuthorizedCode,
                txCode = pinOrTxCode
            )
        }

        logger.debug { "token request : $tokenReq" }

        val tokenResp = OpenID4VCI.sendTokenRequest(
            providerMetadata = providerMetadata,
            tokenRequest = tokenReq
        )
        logger.debug { ">>> Token response is: $tokenResp" }

        OpenID4VCI.validateTokenResponse(tokenResp)

        //we know for a fact that there is an access token in the response
        //due to the validation call above
        val accessToken = tokenResp.accessToken!!

        logger.debug { "// receive credential" }
        val nonce = tokenResp.cNonce

        logger.debug { "Using issuer URL: ${credentialOffer.credentialIssuer}" }
        val credReqs = offeredCredentials.map { offeredCredential ->
            logger.info("Offered credential format: ${offeredCredential.format.name}")
            logger.info(
                "Offered credential cryptographic binding methods: ${
                    offeredCredential.cryptographicBindingMethodsSupported?.joinToString(
                        ", "
                    ) ?: ""
                }"
            )
            CredentialRequest.forOfferedCredential(
                offeredCredential = offeredCredential,
                proof = ProofOfPossessionFactory.new(
                    useKeyProof = isKeyProofRequiredForOfferedCredential(offeredCredential),
                    credentialWallet = credentialWallet,
                    offeredCredential = offeredCredential,
                    credentialOffer = credentialOffer,
                    nonce = nonce
                )
            )
        }
        logger.debug { "credReqs: $credReqs" }

        require(credReqs.isNotEmpty()) { "No credentials offered" }
        return CredentialOfferProcessor.process(
            credentialRequests = credReqs,
            providerMetadata = providerMetadata,
            accessToken = accessToken
        )
    }

    private suspend fun processMSEntraIssuanceRequest(
        credentialOffer: String,
        credentialWallet: TestCredentialWallet,
        pin: String? = null,
    ): List<ProcessedCredentialOffer> {

        val reqParams = parseOfferParams(credentialOffer)

        val entraIssuanceRequest = EntraIssuanceRequest.fromAuthorizationRequest(
            AuthorizationRequest.fromHttpParametersAuto(
                reqParams
            )
        )

        // *) Load key:
        // val walletKey = getKeyByDid(credentialWallet.did)
        val walletKey = DidService.resolveToKey(credentialWallet.did).getOrThrow()

        // *) Create response JWT token, signed by key for holder DID
        val responseObject = entraIssuanceRequest.getResponseObject(
            walletKey.getThumbprint(),
            credentialWallet.did,
            walletKey.getPublicKey().exportJWK(),
            pin
        )

        val responseToken = credentialWallet.signToken(TokenTarget.TOKEN, responseObject, keyId = credentialWallet.did)

        // *) POST response JWT token to return address found in manifest
        val resp = http.post(entraIssuanceRequest.issuerReturnAddress) {
            contentType(ContentType.Text.Plain)
            setBody(responseToken)
        }
        val responseBody = resp.bodyAsText()
        logger.debug { "Resp: $resp" }
        logger.debug { responseBody }
        val vc =
            runCatching { Json.parseToJsonElement(responseBody).jsonObject["vc"]!!.jsonPrimitive.content }.getOrElse {
                msEntraSendIssuanceCompletionCB(
                    entraIssuanceRequest,
                    EntraIssuanceCompletionCode.issuance_failed,
                    EntraIssuanceCompletionErrorDetails.unspecified_error
                )
                throw IllegalArgumentException("Could not get Verifiable Credential from response: $responseBody")
            }
        msEntraSendIssuanceCompletionCB(entraIssuanceRequest, EntraIssuanceCompletionCode.issuance_successful)
        return listOf(
            ProcessedCredentialOffer(
                CredentialResponse.Companion.success(CredentialFormat.jwt_vc_json, vc),
                null,
                entraIssuanceRequest
            )
        )
    }

    private suspend fun msEntraSendIssuanceCompletionCB(
        entraIssuanceRequest: EntraIssuanceRequest,
        code: EntraIssuanceCompletionCode,
        errorDetails: EntraIssuanceCompletionErrorDetails? = null,
    ) {
        if (!entraIssuanceRequest.authorizationRequest.state.isNullOrEmpty() &&
            !entraIssuanceRequest.authorizationRequest.redirectUri.isNullOrEmpty()
        ) {
            val issuanceCompletionResponse = EntraIssuanceCompletionResponse(
                code, entraIssuanceRequest.authorizationRequest.state!!, errorDetails
            )
            logger.debug { "Sending Entra issuance completion response: $issuanceCompletionResponse" }
            http.post(entraIssuanceRequest.authorizationRequest.redirectUri!!) {
                contentType(ContentType.Application.Json)
                setBody(issuanceCompletionResponse)
            }.also {
                logger.debug { "Entra issuance completion callback response: ${it.status}: ${it.bodyAsText()}" }
            }
        } else logger.debug { "No authorization request state or redirectUri found in Entra issuance request, skipping completion response callback" }
    }

}
