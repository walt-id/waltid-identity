package id.walt.webwallet.service.exchange

import cbor.Cbor
import id.walt.crypto.utils.Base64Utils.base64UrlDecode
import id.walt.crypto.utils.JsonUtils.toJsonElement
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
import kotlinx.datetime.Clock
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.json.*

object IssuanceService {

    private val http = WalletHttpClients.getHttpClient()
    private val logger = logger<IssuanceService>()

    suspend fun prepareExternallySignedOfferRequest(
        offer: String,
        credentialWallet: TestCredentialWallet,
        keyId: String,
        did: String,
    ): PrepareExternalClaimResult {
        logger.debug { "// -------- WALLET: PREPARE STEP FOR OID4VCI WITH EXTERNAL SIGNATURES ----------" }
        logger.debug { "// parse credential URI" }
        val reqParams = parseOfferParams(offer)

        // entra or openid4vc credential offer
        val isEntra = EntraIssuanceRequest.isEntraIssuanceRequestUri(offer)
        return if(isEntra) {
            TODO()
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
        logger.debug { "// get issuer metadata" }
        val providerMetadata = getCredentialIssuerOpenIDMetadata(
            credentialOffer.credentialIssuer,
            credentialWallet,
        )
        logger.debug { "providerMetadata: $providerMetadata" }

        logger.debug { "// resolve offered credentials" }
        val offeredCredentials = OpenID4VCI.resolveOfferedCredentials(credentialOffer, providerMetadata)
        logger.debug { "offeredCredentials: $offeredCredentials" }

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
        logger.debug { ">>> Token response = success: ${tokenResp.isSuccess}" }
        return PrepareExternalClaimResult(
            resolvedCredentialOffer = credentialOffer,
            offeredCredentials = offeredCredentials,
            tokenResponse = tokenResp,
            jwtParams = UnsignedJWTParameters(
                header = mapOf(
                    "typ" to "JWT".toJsonElement(),
                    "kid" to keyId.toJsonElement(),
                ),
                payload = buildJsonObject {
                    put("iss", did)
                    put("aud", credentialOffer.credentialIssuer)
                    put("iat", Clock.System.now().epochSeconds)
                    tokenResp.cNonce?.let { put("nonce", it) }
                }.toString()
            )
        )
    }

    suspend fun submitExternallySignedOfferRequest(): List<CredentialDataResult> {
        TODO()
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

    private suspend fun processCredentialOffer(
        credentialOffer: CredentialOffer,
        credentialWallet: TestCredentialWallet,
        clientId: String,
    ): List<ProcessedCredentialOffer> {
        logger.debug { "// get issuer metadata" }
        val providerMetadata = getCredentialIssuerOpenIDMetadata(
            credentialOffer.credentialIssuer,
            credentialWallet,
        )
        logger.debug { "providerMetadata: $providerMetadata" }

        logger.debug { "// resolve offered credentials" }
        val offeredCredentials = OpenID4VCI.resolveOfferedCredentials(credentialOffer, providerMetadata)
        logger.debug { "offeredCredentials: $offeredCredentials" }

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
        logger.debug { ">>> Token response = success: ${tokenResp.isSuccess}" }

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
            // Use key proof if supported cryptographic binding method is not empty, doesn't contain did and contains cose_key
            val useKeyProof = (offeredCredential.cryptographicBindingMethodsSupported != null &&
                    (offeredCredential.cryptographicBindingMethodsSupported!!.contains("cose_key") ||
                            offeredCredential.cryptographicBindingMethodsSupported!!.contains("jwk")) &&
                    !offeredCredential.cryptographicBindingMethodsSupported!!.contains("did"))
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
        return CredentialOfferProcessor.process(credReqs, providerMetadata, tokenResp)
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
        val offeredCredentials: List<OfferedCredential>,
        val tokenResponse: TokenResponse,
        val jwtParams: UnsignedJWTParameters,
    )

    @Serializable
    data class UnsignedJWTParameters(
        val header: Map<String, JsonElement>,
        val payload: String,
    )
}
