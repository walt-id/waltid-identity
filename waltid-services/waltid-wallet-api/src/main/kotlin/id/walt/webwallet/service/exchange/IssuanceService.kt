package id.walt.webwallet.service.exchange

import cbor.Cbor
import id.walt.crypto.utils.Base64Utils.base64UrlDecode
import id.walt.crypto.utils.JwsUtils.decodeJwsOrSdjwt
import id.walt.did.dids.DidService
import id.walt.mdoc.dataelement.toDataElement
import id.walt.mdoc.doc.MDoc
import id.walt.mdoc.issuersigned.IssuerSigned
import id.walt.oid4vc.OpenID4VCI
import id.walt.oid4vc.data.*
import id.walt.oid4vc.providers.TokenTarget
import id.walt.oid4vc.requests.*
import id.walt.oid4vc.responses.*
import id.walt.oid4vc.util.randomUUID
import id.walt.sdjwt.SDJWTVCTypeMetadata
import id.walt.webwallet.manifest.extractor.EntraManifestExtractor
import id.walt.webwallet.service.oidc4vc.TestCredentialWallet
import id.walt.webwallet.utils.WalletHttpClients
import io.klogging.logger
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.json.*

object IssuanceService {

    private val http = WalletHttpClients.getHttpClient()
    private val logger = logger<IssuanceService>()

    suspend fun prepareExternallySignedOfferRequest(
        offerURL: String,
        credentialWallet: TestCredentialWallet,
        keyId: String,
        did: String,
    ): PrepareExternalClaimResult {
        logger.debug { "// -------- WALLET: PREPARE STEP FOR OID4VCI WITH EXTERNAL SIGNATURES ----------" }
        logger.debug { "// parse credential URI" }
        val reqParams = parseOfferParams(offerURL)

        // entra or openid4vc credential offer
        val isEntra = EntraIssuanceRequest.isEntraIssuanceRequestUri(offerURL)
        return if (isEntra) {
            //TODO: Not yet implemented
            throw UnsupportedOperationException("MS Entra credential issuance requests with externally provided signatures are not supported yet")
        } else {
            processPrepareCredentialOffer(
                credentialWallet.resolveCredentialOffer(CredentialOfferRequest.fromHttpParameters(reqParams)),
                credentialWallet,
                did,
                keyId,
            )
        }
    }

    private suspend fun processPrepareCredentialOffer(
        credentialOffer: CredentialOffer,
        credentialWallet: TestCredentialWallet,
        did: String,
        keyId: String,
    ): PrepareExternalClaimResult {
        val providerMetadata = getCredentialIssuerOpenIDMetadata(
            credentialOffer.credentialIssuer,
            credentialWallet,
        )
        logger.debug { "providerMetadata: $providerMetadata" }

        logger.debug { "// resolve offered credentials" }
        val offeredCredentials = OpenID4VCI.resolveOfferedCredentials(credentialOffer, providerMetadata)
        logger.debug { "offeredCredentials: $offeredCredentials" }
        require(offeredCredentials.isNotEmpty()) { "Resolved an empty list of offered credentials" }

        logger.debug { "// fetch access token using pre-authorized code (skipping authorization step)" }
        val tokenReq = TokenRequest(
            grantType = GrantType.pre_authorized_code,
            clientId = did,
            redirectUri = credentialWallet.config.redirectUri,
            preAuthorizedCode = credentialOffer.grants[GrantType.pre_authorized_code.value]!!.preAuthorizedCode,
            txCode = null
        )
        val tokenResp = issueTokenRequest(
            providerMetadata.tokenEndpoint!!,
            tokenReq,
        )
        logger.debug { ">>> Token response is: $tokenResp" }
        validateTokenResponse(tokenResp)

        val offeredCredentialsProofRequests = offeredCredentials.map { offeredCredential ->
            OfferedCredentialProofOfPossessionParameters(
                offeredCredential,
                getOfferedCredentialProofOfPossessionParameters(
                    credentialOffer,
                    offeredCredential,
                    did,
                    keyId,
                    tokenResp.cNonce,
                ),
            )
        }
        return PrepareExternalClaimResult(
            resolvedCredentialOffer = credentialOffer,
            offeredCredentialsProofRequests = offeredCredentialsProofRequests,
            accessToken = tokenResp.accessToken,
        )
    }

    private suspend fun getOfferedCredentialProofOfPossessionParameters(
        credentialOffer: CredentialOffer,
        offeredCredential: OfferedCredential,
        did: String,
        keyId: String,
        nonce: String?,
    ): ProofOfPossessionParameters {
        val useKeyProof = (offeredCredential.cryptographicBindingMethodsSupported != null &&
                (offeredCredential.cryptographicBindingMethodsSupported!!.contains("cose_key") ||
                        offeredCredential.cryptographicBindingMethodsSupported!!.contains("jwk")) &&
                !offeredCredential.cryptographicBindingMethodsSupported!!.contains("did"))
        return ProofOfPossessionParameterFactory.new(
            did,
            keyId,
            useKeyProof,
            offeredCredential,
            credentialOffer,
            nonce,
        )
    }

    suspend fun submitExternallySignedOfferRequest(
        offerURL: String,
        credentialIssuerURL: String,
        credentialWallet: TestCredentialWallet,
        offeredCredentialProofsOfPossession: List<OfferedCredentialProofOfPossession>,
        accessToken: String?,
    ): List<CredentialDataResult> {
        val isEntra = EntraIssuanceRequest.isEntraIssuanceRequestUri(offerURL)
        val processedCredentialOffers = if (isEntra) {
            //TODO: Not yet implemented
            throw UnsupportedOperationException("MS Entra credential issuance requests with externally provided signatures are not supported yet")
        } else {
            submitExternallySignedOID4VCICredentialRequests(
                credentialIssuerURL,
                credentialWallet,
                offeredCredentialProofsOfPossession,
                accessToken,
            )
        }
        logger.debug { "// parse and verify credential(s)" }
        check(processedCredentialOffers.any { it.credentialResponse.credential != null }) { "No credential was returned from credentialEndpoint: $processedCredentialOffers" }

        val manifest = if (isEntra) EntraManifestExtractor().extract(offerURL) else null
        return processedCredentialOffers.map {
            getCredentialData(it, manifest)
        }
    }

    private suspend fun submitExternallySignedOID4VCICredentialRequests(
        credentialIssuerURL: String,
        credentialWallet: TestCredentialWallet,
        offeredCredentialProofsOfPossession: List<OfferedCredentialProofOfPossession>,
        accessToken: String?,
    ): List<ProcessedCredentialOffer> {
        logger.debug { "// get issuer metadata" }
        val providerMetadata = getCredentialIssuerOpenIDMetadata(
            credentialIssuerURL,
            credentialWallet,
        )
        logger.debug { "providerMetadata: $providerMetadata" }
        logger.debug { "Using issuer URL: $credentialIssuerURL" }
        val credReqs = offeredCredentialProofsOfPossession.map { offeredCredentialProofOfPossession ->
            val offeredCredential = offeredCredentialProofOfPossession.offeredCredential
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
                proof = offeredCredentialProofOfPossession.toProofOfPossession(),
            )
        }
        logger.debug { "credReqs: $credReqs" }

        require(credReqs.isNotEmpty()) { "No credentials offered" }
        return CredentialOfferProcessor.process(credReqs, providerMetadata, accessToken!!)
    }

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
                EntraIssuanceRequest.fromAuthorizationRequest(
                    AuthorizationRequest.fromHttpParametersAuto(
                        reqParams
                    )
                ), credentialWallet
            )
        } else {
            processCredentialOffer(
                credentialWallet.resolveCredentialOffer(CredentialOfferRequest.fromHttpParameters(reqParams)),
                credentialWallet,
                clientId
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

    private fun parseOfferParams(offerURL: String) = Url(offerURL).parameters.toMap()

    private suspend fun getCredentialIssuerOpenIDMetadata(
        issuerURL: String,
        credentialWallet: TestCredentialWallet,
    ): OpenIDProviderMetadata {
        logger.debug { "// get issuer metadata" }
        val providerMetadataUri =
            credentialWallet.getCIProviderMetadataUrl(issuerURL)
        logger.debug { "Getting provider metadata from: $providerMetadataUri" }
        val providerMetadataResult = http.get(providerMetadataUri)
        logger.debug { "Provider metadata returned: " + providerMetadataResult.bodyAsText() }
        return providerMetadataResult
            .body<JsonObject>()
            .let {
                OpenIDProviderMetadata.fromJSON(it)
            }
    }

    private suspend fun issueTokenRequest(
        tokenURL: String,
        req: TokenRequest,
    ) = http.submitForm(
        tokenURL, formParameters = parametersOf(req.toHttpParameters())
    ).let {
        logger.debug { "Raw TokenResponse: $it" }
        it.body<JsonObject>().let { TokenResponse.fromJSON(it) }
    }

    private fun validateTokenResponse(
        tokenResponse: TokenResponse,
    ) {
        require(tokenResponse.isSuccess) {
            "token request failed: ${tokenResponse.error} ${tokenResponse.errorDescription}"
        }
        //there has to be an access token in the response, otherwise we are unable
        //to invoke the credential endpoint
        requireNotNull(tokenResponse.accessToken) {
            "invalid Authorization Server token response: no access token included in the response: $tokenResponse "
        }
    }

    private suspend fun processCredentialOffer(
        credentialOffer: CredentialOffer,
        credentialWallet: TestCredentialWallet,
        clientId: String,
    ): List<ProcessedCredentialOffer> {
        val providerMetadata = getCredentialIssuerOpenIDMetadata(
            credentialOffer.credentialIssuer,
            credentialWallet,
        )
        logger.debug { "providerMetadata: $providerMetadata" }

        logger.debug { "// resolve offered credentials" }
        val offeredCredentials = OpenID4VCI.resolveOfferedCredentials(credentialOffer, providerMetadata)
        logger.debug { "offeredCredentials: $offeredCredentials" }
        require(offeredCredentials.isNotEmpty()) { "Resolved an empty list of offered credentials" }

        logger.debug { "// fetch access token using pre-authorized code (skipping authorization step)" }
        val tokenReq = TokenRequest(
            grantType = GrantType.pre_authorized_code,
            clientId = clientId,
            redirectUri = credentialWallet.config.redirectUri,
            preAuthorizedCode = credentialOffer.grants[GrantType.pre_authorized_code.value]!!.preAuthorizedCode,
            txCode = null
        )
        val tokenResp = issueTokenRequest(
            providerMetadata.tokenEndpoint!!,
            tokenReq,
        )
        logger.debug { ">>> Token response is: $tokenResp" }
        validateTokenResponse(tokenResp)
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
            // Use key proof if supported cryptographic binding method is not empty, doesn't contain did and contains cose_key or jwk
            val useKeyProof = (offeredCredential.cryptographicBindingMethodsSupported != null &&
                    (offeredCredential.cryptographicBindingMethodsSupported!!.contains("cose_key") ||
                            offeredCredential.cryptographicBindingMethodsSupported!!.contains("jwk")) &&
                    !offeredCredential.cryptographicBindingMethodsSupported!!.contains("did") || (offeredCredential.format.value == "vc+sd-jwt"))
            CredentialRequest.forOfferedCredential(
                offeredCredential = offeredCredential,
                proof = ProofOfPossessionFactory.new(
                    useKeyProof,
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

    @OptIn(ExperimentalSerializationApi::class)
    private suspend fun getCredentialData(
        processedOffer: ProcessedCredentialOffer, manifest: JsonObject?,
    ) = let {
        val credential = processedOffer.credentialResponse.credential!!.jsonPrimitive.content

        when (val credentialFormat = processedOffer.credentialResponse.format) {
            CredentialFormat.mso_mdoc -> {
                val credentialEncoding =
                    processedOffer.credentialResponse.customParameters["credential_encoding"]?.jsonPrimitive?.content
                        ?: "issuer-signed"
                val docType =
                    processedOffer.credentialRequest?.docType
                        ?: throw IllegalArgumentException("Credential request has no docType property")
                val format =
                    processedOffer.credentialResponse.format
                        ?: throw IllegalArgumentException("Credential response has no format property")
                val mdoc = when (credentialEncoding) {
                    "issuer-signed" -> MDoc(
                        docType.toDataElement(), IssuerSigned.fromMapElement(
                            Cbor.decodeFromByteArray(credential.base64UrlDecode())
                        ), null
                    )

                    else -> throw IllegalArgumentException("Invalid credential encoding: $credentialEncoding")
                }
                // TODO: review ID generation for mdoc
                CredentialDataResult(randomUUID(), mdoc.toCBORHex(), type = docType, format = format)
            }

            else -> {
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
                    manifest = manifest?.toString(),
                    type = typ,
                    format = credentialFormat ?: error("No credential format")
                )
            }
        }
    }

    suspend fun resolveVct(vct: String): SDJWTVCTypeMetadata {
        val authority = Url(vct).protocolWithAuthority
        val response = http.get("$authority/.well-known/vct${vct.substringAfter(authority)}")

        require(response.status.isSuccess()) {"VCT URL returns error: ${response.status}"}

        return response.body<JsonObject>().let { SDJWTVCTypeMetadata.fromJSON(it) }
    }

    @Serializable
    data class CredentialDataResult(
        val id: String,
        val document: String,
        val manifest: String? = null,
        val disclosures: String? = null,
        val type: String,
        val format: CredentialFormat,
    )

    @Serializable
    data class PrepareExternalClaimResult(
        val resolvedCredentialOffer: CredentialOffer,
        val offeredCredentialsProofRequests: List<OfferedCredentialProofOfPossessionParameters>,
        val accessToken: String?,
    )

    @Serializable
    data class OfferedCredentialProofOfPossessionParameters(
        val offeredCredential: OfferedCredential,
        val proofOfPossessionParameters: ProofOfPossessionParameters,
    )

    @Serializable
    data class OfferedCredentialProofOfPossession(
        val offeredCredential: OfferedCredential,
        val proofType: ProofType,
        val signedProofOfPossession: String,
    ) {
        fun toProofOfPossession() = when(proofType) {
            ProofType.cwt -> {
                ProofOfPossession.CWTProofBuilder("").build(signedProofOfPossession)
            }
            ProofType.ldp_vp -> TODO("ldp_vp proof not yet implemented")
            else -> {
                ProofOfPossession.JWTProofBuilder("").build(signedProofOfPossession)
            }
        }
    }
}
