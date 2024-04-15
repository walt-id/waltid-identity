package id.walt.webwallet.service.exchange

import id.walt.crypto.utils.JwsUtils
import id.walt.crypto.utils.JwsUtils.decodeJws
import id.walt.did.dids.DidService
import id.walt.oid4vc.OpenID4VCI
import id.walt.oid4vc.data.CredentialFormat
import id.walt.oid4vc.data.CredentialOffer
import id.walt.oid4vc.data.GrantType
import id.walt.oid4vc.data.OpenIDProviderMetadata
import id.walt.oid4vc.providers.TokenTarget
import id.walt.oid4vc.requests.*
import id.walt.oid4vc.responses.*
import id.walt.oid4vc.util.randomUUID
import id.walt.webwallet.manifest.extractor.EntraManifestExtractor
import id.walt.webwallet.service.oidc4vc.TestCredentialWallet
import id.walt.webwallet.utils.WalletHttpClients
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory

object IssuanceService {

    private val http = WalletHttpClients.getHttpClient()
    private val logger = LoggerFactory.getLogger(this::class.java)

    suspend fun useOfferRequest(
        offer: String, credentialWallet: TestCredentialWallet, clientId: String,
    ) = let {
        logger.debug("// -------- WALLET ----------")
        logger.debug("// as WALLET: receive credential offer, either being called via deeplink or by scanning QR code")
        logger.debug("// parse credential URI")
        val reqParams = Url(offer).parameters.toMap()

        // entra or openid4vc credential offer
        val isEntra = EntraIssuanceRequest.isEntraIssuanceRequestUri(offer)
        val credentialResponses = if (isEntra) {
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
        logger.debug("// parse and verify credential(s)")
        if (credentialResponses.all { it.credential == null }) {
            throw IllegalStateException("No credential was returned from credentialEndpoint: $credentialResponses")
        }

        // ??multiple credentials manifests
        val manifest =
            isEntra.takeIf { it }?.let { EntraManifestExtractor().extract(offer) }
        credentialResponses.map {
            getCredentialData(it, manifest)
        }
    }

    private suspend fun processCredentialOffer(
        credentialOffer: CredentialOffer,
        credentialWallet: TestCredentialWallet,
        clientId: String,
    ): List<CredentialResponse> {
        logger.debug("// get issuer metadata")
        val providerMetadataUri =
            credentialWallet.getCIProviderMetadataUrl(credentialOffer.credentialIssuer)
        logger.debug("Getting provider metadata from: $providerMetadataUri")
        val providerMetadataResult = http.get(providerMetadataUri)
        logger.debug("Provider metadata returned: " + providerMetadataResult.bodyAsText())

        val providerMetadata = providerMetadataResult.body<JsonObject>().let { OpenIDProviderMetadata.fromJSON(it) }
        logger.debug("providerMetadata: {}", providerMetadata)

        logger.debug("// resolve offered credentials")
        val offeredCredentials = OpenID4VCI.resolveOfferedCredentials(credentialOffer, providerMetadata)
        logger.debug("offeredCredentials: {}", offeredCredentials)

        //val offeredCredential = offeredCredentials.first()
        //logger.debug("offeredCredentials[0]: $offeredCredential")

        logger.debug("// fetch access token using pre-authorized code (skipping authorization step)")
        val tokenReq = TokenRequest(
            grantType = GrantType.pre_authorized_code,
            clientId = clientId,
            redirectUri = credentialWallet.config.redirectUri,
            preAuthorizedCode = credentialOffer.grants[GrantType.pre_authorized_code.value]!!.preAuthorizedCode,
            txCode = null
        )
//        logger.debug("tokenReq: {}", tokenReq)

        val tokenResp = http.submitForm(
            providerMetadata.tokenEndpoint!!, formParameters = parametersOf(tokenReq.toHttpParameters())
        ).let {
            logger.debug("tokenResp raw: {}", it)
            it.body<JsonObject>().let { TokenResponse.fromJSON(it) }
        }

//        logger.debug("tokenResp: {}", tokenResp)

        logger.debug(">>> Token response = success: ${tokenResp.isSuccess}")

        logger.debug("// receive credential")
        val nonce = tokenResp.cNonce


        logger.debug("Using issuer URL: ${credentialOffer.credentialIssuer}")
        val credReqs = offeredCredentials.map { offeredCredential ->
            CredentialRequest.forOfferedCredential(
                offeredCredential = offeredCredential,
                proof = credentialWallet.generateDidProof(
                    did = credentialWallet.did,
                    issuerUrl = credentialOffer.credentialIssuer,
                    nonce = nonce
                )
            )
        }
        logger.debug("credReqs: {}", credReqs)


        return when {
            credReqs.size >= 2 -> {
                val batchCredentialRequest = BatchCredentialRequest(credReqs)

                val credentialResponses = http.post(providerMetadata.batchCredentialEndpoint!!) {
                    contentType(ContentType.Application.Json)
                    bearerAuth(tokenResp.accessToken!!)
                    setBody(batchCredentialRequest.toJSON())
                }.body<JsonObject>().let { BatchCredentialResponse.fromJSON(it) }
                logger.debug("credentialResponses: {}", credentialResponses)

                credentialResponses.credentialResponses
                    ?: throw IllegalArgumentException("No credential responses returned")
            }

            credReqs.size == 1 -> {
                val credReq = credReqs.first()

                val credentialResponse = http.post(providerMetadata.credentialEndpoint!!) {
                    contentType(ContentType.Application.Json)
                    bearerAuth(tokenResp.accessToken!!)
                    setBody(credReq.toJSON())
                }.body<JsonObject>().let { CredentialResponse.fromJSON(it) }
                logger.debug("credentialResponse: {}", credentialResponse)

                listOf(credentialResponse)
            }

            else -> throw IllegalStateException("No credentials offered")
        }
    }

    private suspend fun processMSEntraIssuanceRequest(
        entraIssuanceRequest: EntraIssuanceRequest,
        credentialWallet: TestCredentialWallet,
        pin: String? = null
    ): List<CredentialResponse> {
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
        logger.debug("Resp: {}", resp)
        logger.debug(responseBody)
        val vc =
            runCatching { Json.parseToJsonElement(responseBody).jsonObject["vc"]!!.jsonPrimitive.content }.getOrElse {
                msEntraSendIssuanceCompletionCB(entraIssuanceRequest, EntraIssuanceCompletionCode.issuance_failed, EntraIssuanceCompletionErrorDetails.unspecified_error)
                throw IllegalArgumentException("Could not get Verifiable Credential from response: $responseBody")
            }
        msEntraSendIssuanceCompletionCB(entraIssuanceRequest, EntraIssuanceCompletionCode.issuance_successful)
        return listOf(CredentialResponse.Companion.success(CredentialFormat.jwt_vc_json, vc))
    }

    private suspend fun msEntraSendIssuanceCompletionCB(entraIssuanceRequest: EntraIssuanceRequest,
                                                        code: EntraIssuanceCompletionCode,
                                                        errorDetails: EntraIssuanceCompletionErrorDetails? = null) {
        if(!entraIssuanceRequest.authorizationRequest.state.isNullOrEmpty() &&
           !entraIssuanceRequest.authorizationRequest.redirectUri.isNullOrEmpty()) {
            val issuanceCompletionResponse = EntraIssuanceCompletionResponse(
                code, entraIssuanceRequest.authorizationRequest.state!!, errorDetails)
            logger.debug("Sending Entra issuance completion response: $issuanceCompletionResponse")
            http.post(entraIssuanceRequest.authorizationRequest.redirectUri!!) {
                contentType(ContentType.Application.Json)
                setBody(issuanceCompletionResponse)
            }.also {
                logger.debug("Entra issuance completion callback response: ${it.status}: ${it.bodyAsText()}")
            }
        } else logger.debug("No authorization request state or redirectUri found in Entra issuance request, skipping completion response callback")
    }

    private fun getCredentialData(
        credentialResp: CredentialResponse, manifest: JsonObject?,
    ) = let {
        val credential = credentialResp.credential!!.jsonPrimitive.content
        val credentialJwt = credential.decodeJws(withSignature = true)
        when (val typ = credentialJwt.header["typ"]?.jsonPrimitive?.content?.lowercase()) {
            "jwt" -> parseJwtCredentialResponse(credentialJwt, credential, manifest, typ)
            "vc+sd-jwt" -> parseSdJwtCredentialResponse(credentialJwt, credential, manifest, typ)
            null -> throw IllegalArgumentException("WalletCredential JWT does not have \"typ\"")
            else -> throw IllegalArgumentException("Invalid credential \"typ\": $typ")
        }
    }

    private fun parseJwtCredentialResponse(
        credentialJwt: JwsUtils.JwsParts, document: String, manifest: JsonObject?, type: String
    ) = let {
        val credentialId =
            credentialJwt.payload["vc"]!!.jsonObject["id"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                ?: randomUUID()

        logger.debug("Got JWT credential: {}", credentialJwt)

        CredentialDataResult(
            id = credentialId,
            document = document,
            manifest = manifest?.toString(),
            type = type,
        )
    }

    private fun parseSdJwtCredentialResponse(
        credentialJwt: JwsUtils.JwsParts, document: String, manifest: JsonObject?, type: String
    ) = let {
        val credentialId =
            credentialJwt.payload["id"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: randomUUID()

        logger.debug("Got SD-JWT credential: $credentialJwt")

        val disclosures = credentialJwt.signature.split("~").drop(1)
        logger.debug("Disclosures (${disclosures.size}): $disclosures")

        val disclosuresString = disclosures.joinToString("~")

        val credentialWithoutDisclosures = document.substringBefore("~")

        CredentialDataResult(
            id = credentialId,
            document = credentialWithoutDisclosures,
            disclosures = disclosuresString,
            manifest = manifest?.toString(),
            type = type,
        )
    }

    @Serializable
    data class CredentialDataResult(
        val id: String,
        val document: String,
        val manifest: String? = null,
        val disclosures: String? = null,
        val type: String?,
    )
}
