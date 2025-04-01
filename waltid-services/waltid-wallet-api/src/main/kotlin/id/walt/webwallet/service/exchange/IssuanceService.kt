package id.walt.webwallet.service.exchange

import id.walt.commons.config.ConfigManager
import id.walt.crypto.utils.JsonUtils.toJsonElement
import id.walt.did.dids.DidService
import id.walt.oid4vc.OpenID4VCI
import id.walt.oid4vc.data.*
import id.walt.oid4vc.definitions.OPENID_CREDENTIAL_AUTHORIZATION_TYPE
import id.walt.oid4vc.definitions.OPENID_CREDENTIAL_SCOPE
import id.walt.oid4vc.providers.TokenTarget
import id.walt.oid4vc.requests.*
import id.walt.oid4vc.responses.*
import id.walt.webwallet.config.WalletServiceConfig
import id.walt.webwallet.db.models.WalletOid4vciAuthReqSession
import id.walt.webwallet.db.models.getAuthReqSessions
import id.walt.webwallet.manifest.extractor.EntraManifestExtractor
import id.walt.webwallet.service.oidc4vc.TestCredentialWallet
import id.walt.webwallet.utils.RandomUtils
import io.klogging.logger
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.*
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import id.walt.webwallet.db.models.insertAuthReqSession
import kotlinx.datetime.Clock
import kotlinx.serialization.json.*

object IssuanceService : IssuanceServiceBase() {

    override val logger = logger<IssuanceService>()

    suspend fun useOfferRequest(
        offer: String, credentialWallet: TestCredentialWallet, clientId: String,
    ) = let {
        logger.debug { "// -------- WALLET ----------" }
        logger.debug { "// as WALLET: receive credential offer, either being called via deeplink or by scanning QR code" }
        logger.debug { "// parse credential URI" }
        val reqParams = parseOfferParams(offer)

        // entra or openid4vc credential offer
        val isEntra = EntraIssuanceRequest.isEntraIssuanceRequestUri(offer)
        val processedCredentialOffers = if (isEntra) {
            processMSEntraIssuanceRequest(
                entraIssuanceRequest = EntraIssuanceRequest.fromAuthorizationRequest(
                    AuthorizationRequest.fromHttpParametersAuto(
                        reqParams
                    )
                ),
                credentialWallet = credentialWallet
            )
        } else {
            processCredentialOffer(
                credentialOffer = OpenID4VCI.parseAndResolveCredentialOfferRequestUrl(offer),
                credentialWallet = credentialWallet,
                clientId = clientId
            )
        }
        // === original ===
        logger.debug { "// parse and verify credential(s)" }
        check(processedCredentialOffers.any { it.credentialResponse.credential != null }) { "No credential was returned from credentialEndpoint: $processedCredentialOffers" }

        // ??multiple credentials manifests
        val manifest =
            isEntra.takeIf { it }?.let { EntraManifestExtractor().extract(offer) }
        processedCredentialOffers.map {
            getCredentialData(it, manifest)
        }
    }

    private suspend fun processCredentialOffer(
        credentialOffer: CredentialOffer,
        credentialWallet: TestCredentialWallet,
        clientId: String,
    ): List<ProcessedCredentialOffer> {

        val providerMetadata = OpenID4VCI.resolveCIProviderMetadata(credentialOffer)

        logger.debug { "providerMetadata: $providerMetadata" }

        logger.debug { "// resolve offered credentials" }
        val offeredCredentials = OpenID4VCI.resolveOfferedCredentials(credentialOffer, providerMetadata)
        logger.debug { "offeredCredentials: $offeredCredentials" }

        require(offeredCredentials.isNotEmpty()) { "Resolved an empty list of offered credentials" }

        logger.debug { "// fetch access token using pre-authorized code (skipping authorization step)" }

        val tokenReq = TokenRequest.PreAuthorizedCode(
            preAuthorizedCode = credentialOffer.grants[GrantType.pre_authorized_code.value]!!.preAuthorizedCode!!,
            clientId = clientId
        )

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
                    isKeyProofRequiredForOfferedCredential(offeredCredential),
                    credentialWallet,
                    offeredCredential,
                    credentialOffer,
                    nonce
                )
            )
        }
        logger.debug { "credReqs: $credReqs" }

        require(credReqs.isNotEmpty()) { "No credentials offered" }
        return CredentialOfferProcessor.process(credReqs, providerMetadata, accessToken)
    }

    private suspend fun processMSEntraIssuanceRequest(
        entraIssuanceRequest: EntraIssuanceRequest,
        credentialWallet: TestCredentialWallet,
        pin: String? = null,
    ): List<ProcessedCredentialOffer> {
        // *) Load key:
//        val walletKey = getKeyByDid(credentialWallet.did)
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


    @OptIn(ExperimentalUuidApi::class)
    suspend fun useOfferRequestAuthorize(
        offer: String,
        accountId: Uuid,
        credentialWallet: TestCredentialWallet,
        walletId: Uuid,
        successRedirectUri: String
    ): String = let {
        val reqParams = Url(offer).parameters.toMap()

        return processCredentialOfferAuthorize(
            credentialOffer = credentialWallet.resolveCredentialOffer(
                credentialOfferRequest = CredentialOfferRequest.fromHttpParameters(reqParams)
            ),
            accountId = accountId,
            credentialWallet = credentialWallet,
            walletId = walletId,
            successRedirectUri = successRedirectUri
        )
    }

    @OptIn(ExperimentalUuidApi::class)
    private suspend fun processCredentialOfferAuthorize(
        credentialOffer: CredentialOffer,
        accountId: Uuid,
        credentialWallet: TestCredentialWallet,
        walletId: Uuid,
        successRedirectUri: String
    ): String {

        val providerMetadataUri = credentialWallet.getCIProviderMetadataUrl(credentialOffer.credentialIssuer)

        val providerMetadataResult = http.get(providerMetadataUri)

        val providerMetadata = providerMetadataResult.body<JsonObject>().let { OpenIDProviderMetadata.fromJSON(it) }

        val offeredCredentials = OpenID4VCI.resolveOfferedCredentials(credentialOffer, providerMetadata)

        // 1. extract issuer state
        val issuerState = credentialOffer.grants[GrantType.authorization_code.value]?.issuerState
            ?: throw IllegalArgumentException("Offer does not contain authorization code and issuer state value")

        // 2. assemble authorize request
        val authReqSessionId = RandomUtils.randomBase64UrlString(256)
        val state = RandomUtils.randomBase64UrlString(256)
        val redirectUri = "http://localhost:7001/wallet-api/wallet/${walletId}/exchange/callback/$authReqSessionId"
        val authorizationEndpoint = "http://localhost:7001/wallet-api/wallet/${walletId}/exchange/authorization/$authReqSessionId"

        val authReq = AuthorizationRequest(
            responseType = setOf(ResponseType.Code),
            clientId = credentialWallet.did,
            scope = setOf(OPENID_CREDENTIAL_SCOPE, "profile"),
            redirectUri = redirectUri,
            state = state,
            clientMetadata = OpenIDClientMetadata(
                requestUris = listOf(redirectUri, authorizationEndpoint),
                customParameters = mapOf("authorization_endpoint" to authorizationEndpoint.toJsonElement()),
            ),
            authorizationDetails = listOf(
                AuthorizationDetails(
                    format = offeredCredentials.firstOrNull()?.format,
                    type = OPENID_CREDENTIAL_AUTHORIZATION_TYPE,
                    types = offeredCredentials.firstOrNull()?.types,
                )
            ),
            issuerState = issuerState
        )

        insertAuthReqSession(
            wallet = walletId,
            session = WalletOid4vciAuthReqSession(
                id = authReqSessionId,
                accountId = accountId,
                authorizationRequest = authReq.toJSON(),
                issuerMetadata = providerMetadata,
                successRedirectUri = successRedirectUri,
                createdOn = Clock.System.now()
            )
        )

        return authReq.toRedirectUri(
            providerMetadata.authorizationEndpoint
                ?: throw IllegalArgumentException("Issuer Authorization endpoint is not defined"), ResponseMode.query
        )
    }

    @OptIn(ExperimentalUuidApi::class)
    suspend fun handleCallback(
        state: String,
        code: String,
        credentialWallet: TestCredentialWallet,
        clientId: String,
        walletId: Uuid,
        authReqSessionId: String
    ): Pair<List<CredentialDataResult>, String> = let {


        // 1.
        val authReqSession = getAuthReqSessions(wallet = walletId, id = authReqSessionId)
        println(authReqSession)
        require(authReqSession!!.authorizationRequest["state"]?.jsonPrimitive?.content == state) { "Invalid state parameter" }

        // 2.
        val tokenResponse = exchangeCodeForToken(authReqSession.issuerMetadata, code, credentialWallet, clientId)

         // 3.
        OpenID4VCI.validateTokenResponse(tokenResponse)

        val accessToken = tokenResponse.accessToken!!

        logger.debug { "// receive credential" }
        val nonce = tokenResponse.cNonce

        val authDetailsArray = authReqSession.authorizationRequest["authorizationDetails"]?.jsonArray

        val firstDetail = authDetailsArray?.firstOrNull()?.jsonObject

        val format = firstDetail?.get("format")?.jsonPrimitive?.contentOrNull?.let { CredentialFormat.fromValue(it) } ?: CredentialFormat.jwt_vc_json

        val types = firstDetail?.get("types")?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }

        val offeredCredential = OfferedCredential(
            format = format,
            types = types
        )

//        val offeredCredential = OfferedCredential(
//            format = authReqSession.authorizationRequest.authorizationDetails?.firstOrNull()?.format ?: CredentialFormat.jwt_vc_json,
//            types = authReqSession.authorizationRequest.authorizationDetails?.firstOrNull()?.types,
//        )

        val offeredCredentials = listOf(offeredCredential)
        val credentialOfferBuilder =  CredentialOffer.Draft13.Builder(authReqSession.issuerMetadata.credentialIssuer!!) // we create credential offer since the generatePoP needs a credential offer object to extract the issuer url


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
                    isKeyProofRequiredForOfferedCredential(offeredCredential),
                    credentialWallet,
                    offeredCredential,
                    credentialOfferBuilder.build(),
                    nonce
                )
            )
        }
        logger.debug { "credReqs: $credReqs" }

        require(credReqs.isNotEmpty()) { "No credentials offered" }
        val processedCredentialOffers = CredentialOfferProcessor.process(credReqs, authReqSession.issuerMetadata, accessToken)

        logger.debug { "// parse and verify credential(s)" }
        check(processedCredentialOffers.any { it.credentialResponse.credential != null }) { "No credential was returned from credentialEndpoint: $processedCredentialOffers" }

        val credentials =  processedCredentialOffers.map {
            getCredentialData(it, null)
        }

        return credentials to authReqSession.successRedirectUri
    }

    private suspend fun exchangeCodeForToken(
        providerMetadata: OpenIDProviderMetadata,
        code: String,
        credentialWallet: TestCredentialWallet,
        clientId: String,
    ): TokenResponse {

        logger.debug("// fetch access token using authorized code")

        val tokenReq = TokenRequest.AuthorizationCode(
            code = code,
            redirectUri = credentialWallet.config.redirectUri,
            clientId = clientId
        )

        val tokenResp = OpenID4VCI.sendTokenRequest(
            providerMetadata = providerMetadata,
            tokenRequest = tokenReq
        )

        return tokenResp
    }

}
