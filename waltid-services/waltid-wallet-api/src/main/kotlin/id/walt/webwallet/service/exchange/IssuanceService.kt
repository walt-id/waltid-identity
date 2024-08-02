package id.walt.webwallet.service.exchange

import com.auth0.jwt.JWT
import id.walt.commons.config.ConfigManager
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.JsonUtils.toJsonElement
import id.walt.crypto.utils.JwsUtils
import id.walt.crypto.utils.JwsUtils.decodeJws
import id.walt.did.dids.DidService
import id.walt.oid4vc.OpenID4VCI
import id.walt.oid4vc.data.*
import id.walt.oid4vc.definitions.JWTClaims
import id.walt.oid4vc.definitions.OPENID_CREDENTIAL_AUTHORIZATION_TYPE
import id.walt.oid4vc.definitions.OPENID_CREDENTIAL_SCOPE
import id.walt.oid4vc.providers.IssuanceSession
import id.walt.oid4vc.providers.TokenTarget
import id.walt.oid4vc.requests.*
import id.walt.oid4vc.responses.*
import id.walt.oid4vc.util.randomUUID
import id.walt.webwallet.config.WalletConfig
import id.walt.webwallet.manifest.extractor.EntraManifestExtractor
import id.walt.webwallet.service.events.EventType
import id.walt.webwallet.service.oidc4vc.TestCredentialWallet
import id.walt.webwallet.utils.RandomUtils
import id.walt.webwallet.utils.WalletHttpClients
import io.klogging.logger
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import kotlinx.uuid.UUID
import org.jetbrains.exposed.sql.Op
import java.util.*


// FIXME: USE CACHE INSTEAD OF AUTH SESSION MAPPING MAPPING
// TODO: Hack
// -------------------------------
// Simple in-memory session management
data class AuthReqSession(
    val id: String,
    val accountId: UUID,
    val walletId: UUID,
    val authorizationRequest: AuthorizationRequest,
    val issuerMetadata: OpenIDProviderMetadata,
    val consentPageUri: String,
    var authServerState: String? = null //the state used for additional authentication with pwd, id_token or vp_token.
)

val authReqSessions: MutableMap<String, AuthReqSession> = mutableMapOf()

object IssuanceService {

    private val http = WalletHttpClients.getHttpClient()
    private val logger = logger<IssuanceService>()

    suspend fun useOfferRequest(
        offer: String, credentialWallet: TestCredentialWallet, clientId: String,
    ) = let {
        logger.debug { "// -------- WALLET ----------" }
        logger.debug { "// as WALLET: receive credential offer, either being called via deeplink or by scanning QR code" }
        logger.debug { "// parse credential URI" }
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
        logger.debug { "// parse and verify credential(s)" }
        check(credentialResponses.any { it.credential != null }) { "No credential was returned from credentialEndpoint: $credentialResponses" }

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
        logger.debug { "// get issuer metadata" }
        val providerMetadataUri =
            credentialWallet.getCIProviderMetadataUrl(credentialOffer.credentialIssuer)
        logger.debug { "Getting provider metadata from: $providerMetadataUri" }
        val providerMetadataResult = http.get(providerMetadataUri)
        logger.debug { "Provider metadata returned: " + providerMetadataResult.bodyAsText() }

        val providerMetadata = providerMetadataResult.body<JsonObject>().let { OpenIDProviderMetadata.fromJSON(it) }
        logger.debug { "providerMetadata: $providerMetadata" }

        logger.debug { "// resolve offered credentials" }
        val offeredCredentials = OpenID4VCI.resolveOfferedCredentials(credentialOffer, providerMetadata)
        logger.debug { "offeredCredentials: $offeredCredentials" }

        //val offeredCredential = offeredCredentials.first()
        //logger.debug { "offeredCredentials[0]: $offeredCredential" }

        logger.debug { "// fetch access token using pre-authorized code (skipping authorization step)" }
        val tokenReq = TokenRequest(
            grantType = GrantType.pre_authorized_code,
            clientId = clientId,
            redirectUri = credentialWallet.config.redirectUri,
            preAuthorizedCode = credentialOffer.grants[GrantType.pre_authorized_code.value]!!.preAuthorizedCode,
            txCode = null
        )
//        logger.debug { "tokenReq: {}", tokenReq }

        val tokenResp = http.submitForm(
            providerMetadata.tokenEndpoint!!, formParameters = parametersOf(tokenReq.toHttpParameters())
        ).let {
            logger.debug { "tokenResp raw: $it" }
            it.body<JsonObject>().let { TokenResponse.fromJSON(it) }
        }

//        logger.debug { "tokenResp: {}", tokenResp }

        logger.debug { ">>> Token response = success: ${tokenResp.isSuccess}" }

        logger.debug { "// receive credential" }
        val nonce = tokenResp.cNonce


        logger.debug { "Using issuer URL: ${credentialOffer.credentialIssuer}" }
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
        logger.debug { "credReqs: $credReqs" }

        require(credReqs.isNotEmpty()) { "No credentials offered" }

        return when {
            credReqs.size == 1 -> {
                val credReq = credReqs.first()

                val credentialResponse = http.post(providerMetadata.credentialEndpoint!!) {
                    contentType(ContentType.Application.Json)
                    bearerAuth(tokenResp.accessToken!!)
                    setBody(credReq.toJSON())
                }.body<JsonObject>().let { CredentialResponse.fromJSON(it) }
                logger.debug { "credentialResponse: $credentialResponse" }

                listOf(credentialResponse)
            }

            else -> {
                val batchCredentialRequest = BatchCredentialRequest(credReqs)

                val credentialResponses = http.post(providerMetadata.batchCredentialEndpoint!!) {
                    contentType(ContentType.Application.Json)
                    bearerAuth(tokenResp.accessToken!!)
                    setBody(batchCredentialRequest.toJSON())
                }.body<JsonObject>().let { BatchCredentialResponse.fromJSON(it) }
                logger.debug { "credentialResponses: $credentialResponses" }

                credentialResponses.credentialResponses
                    ?: throw IllegalArgumentException("No credential responses returned")
            }
        }
    }


    suspend fun useOfferRequestAuthorize(
        offer: String, accountId: UUID, credentialWallet: TestCredentialWallet, clientId: String, walletId: UUID, consentPageUri: String
    ): String = let {
        val reqParams = Url(offer).parameters.toMap()

        return processCredentialOfferAuthorize(
            credentialWallet.resolveCredentialOffer(CredentialOfferRequest.fromHttpParameters(reqParams)),
            accountId,
            credentialWallet,
            clientId,
            walletId,
            consentPageUri
        )
    }

    private suspend fun processCredentialOfferAuthorize(
        credentialOffer: CredentialOffer,
        accountId: UUID,
        credentialWallet: TestCredentialWallet,
        clientId: String,
        walletId: UUID,
        consentPageUri: String
    ): String {

        val providerMetadataUri = credentialWallet.getCIProviderMetadataUrl(credentialOffer.credentialIssuer)

        val providerMetadataResult = http.get(providerMetadataUri)

        val providerMetadata = providerMetadataResult.body<JsonObject>().let { OpenIDProviderMetadata.fromJSON(it) }

        providerMetadata.validateBasic()

        val offeredCredentials = OpenID4VCI.resolveOfferedCredentials(credentialOffer, providerMetadata)

        // 1. extract issuer state
        val issuerState = credentialOffer.grants[GrantType.authorization_code.value]?.issuerState
            ?: throw IllegalArgumentException("Offer does not contain authorization code and issuer state value")

        // 2. assemble authorize request
        val authReqSessionId = RandomUtils.randomBase64UrlString(256)
        val state = RandomUtils.randomBase64UrlString(256)
        val redirectUri = "http://localhost:7001/wallet-api/wallet/${walletId}/exchange1/callback/$authReqSessionId"
        val authorizationEndpoint =  "http://localhost:7001/wallet-api/wallet/${walletId}/exchange1/authorization/$authReqSessionId"
//        val redirectUri = "http://localhost:22222/wallet-api/wallet/${walletId}/exchange1/callback/$authReqSessionId"
//        val authorizationEndpoint =  "http://localhost:22222/wallet-api/wallet/${walletId}/exchange1/authorization/$authReqSessionId"
        val authReq = AuthorizationRequest(
            responseType = setOf(ResponseType.Code),
            clientId = credentialWallet.did,
            scope = setOf(OPENID_CREDENTIAL_SCOPE),
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

        authReqSessions[authReqSessionId] = AuthReqSession(authReqSessionId, accountId, walletId, authReq, providerMetadata, consentPageUri)

        return authReq.toRedirectUri(providerMetadata.authorizationEndpoint ?: throw IllegalArgumentException("Issuer Authorization endpoint is not defined"), ResponseMode.query)
    }


    suspend fun resolveReceivedAuthorizationRequest(
        receivedAuthReq: AuthorizationRequest, authReqSessionId: String, credentialWallet: TestCredentialWallet, clientId: String, walletId: UUID
    ): ResolveAuthorizationRequestResponse = let {

        // 1. Check if the received authorization request uses the request object and if yes, validate the singed jwt
            // 1.b Validate with keyid or using the jwks_uri
            // 1.a.Validate request's jwt payload parameters against the plain authorization request's query parameters
        // 2. Return plain authorization request as received

        // 1.
        val issuerMetadata = authReqSessions[authReqSessionId]!!.issuerMetadata
        val consentPageUri = authReqSessions[authReqSessionId]!!.consentPageUri


        when {
            receivedAuthReq.request != null -> parseAndValidateRequestObject(receivedAuthReq, receivedAuthReq.request!!, issuerMetadata, credentialWallet)
            receivedAuthReq.requestUri != null -> parseAndValidateRequestObject(receivedAuthReq, resolveRequestUri(receivedAuthReq.requestUri!!), issuerMetadata, credentialWallet)
        }

        val fullReceivedAuthReq = receivedAuthReq.toRedirectUri(receivedAuthReq.redirectUri ?: throw IllegalArgumentException("Redirect URI is not defined"), ResponseMode.query)
        val receivedAuthReqEnc = Base64.getEncoder().encodeToString(fullReceivedAuthReq.toByteArray())

        // 2.
        return ResolveAuthorizationRequestResponse(receivedAuthReqEnc, receivedAuthReq.responseType.first().value, consentPageUri)
    }

    private suspend fun parseAndValidateRequestObject(receivedAuthReq: AuthorizationRequest, request: String, issuerMetadata: OpenIDProviderMetadata, credentialWallet: TestCredentialWallet) {
        val jwtParts = request.decodeJws(withSignature = true, allowMissingSignature = false)
        require(receivedAuthReq.compareWithJwtRequestObject(AuthorizationRequest.fromJSON(jwtParts.payload))) {"Error in request object jwt verification - parameters are not identical"}

        val key = fetchKeyFromJwks(jwtParts.header["kid"]?.jsonPrimitive?.content, issuerMetadata.jwksUri ?: throw IllegalArgumentException("Invalid 'kid' and 'jwks_uri'"))
        require(key.verifyJws(request).isSuccess) {"Error in request object jwt verification - invalid signature"}
    }

    private suspend fun fetchKeyFromJwks(kid: String?, jwksUri: String): JWKKey {
        val jwksResponse = http.get(jwksUri)
        val jwks = jwksResponse.body<JsonObject>()

        val keyStr = jwks["keys"]?.jsonArray?.firstOrNull {
            it.jsonObject["kid"]?.jsonPrimitive?.content == kid
        }?.toString() ?: throw IllegalArgumentException("Error in request object jwt - invalid kid")

        return JWKKey.importJWK(keyStr).getOrThrow()
    }

    private suspend fun resolveRequestUri(requestUri: String): String {
        return http.get(requestUri).body<String>()
    }

    suspend fun useIdTokenRequest(
        authReq: AuthorizationRequest, credentialWallet: TestCredentialWallet, clientId: String, walletId: UUID
    ): String = let {
        // 1. Set claims of the id_token
        // 2. Signed jwt with did
        // 3. Extract the redirect_uri and POST the response
        // 4. Return the response. Can be 200 or 302, including the redirect uri either in the body or in location respectively

        // 1.
        val idTokenPayload = buildJsonObject {
            put(JWTClaims.Payload.subject, credentialWallet.did)
            put(JWTClaims.Payload.nonce, authReq.nonce)
            put(JWTClaims.Payload.issuer, credentialWallet.did)
            put(JWTClaims.Payload.audience, authReq.clientId)
            put(JWTClaims.Payload.issuedAtTime, Clock.System.now().epochSeconds)
            put(JWTClaims.Payload.expirationTime, (Clock.System.now().epochSeconds + 864000L))
        }

        // 2.
        val idToken = credentialWallet.signToken(TokenTarget.TOKEN, idTokenPayload, null, credentialWallet.did)

        // 3.
        val directPostResponse = http.submitForm(
            url = authReq.redirectUri ?: throw IllegalArgumentException("Authorization request redirect_uri is missing"),
            formParameters = Parameters.build {
                append("id_token", idToken)
                append("state", authReq.state ?: throw IllegalArgumentException("Authorization request state is missing"))
            },
        )

        println(directPostResponse)
        when (directPostResponse.status) {
            HttpStatusCode.OK -> return directPostResponse.body<JsonObject>()["redirect_uri"]?.jsonPrimitive?.content ?: throw IllegalArgumentException("Error in request object jwt - invalid kid")
            HttpStatusCode.Found -> return directPostResponse.headers["location"] ?: throw IllegalStateException("Error in retrieving location header")
            else -> throw IllegalStateException("Error in status code response: status code is ${directPostResponse.status}")
        }
    }

    suspend fun handleCallback(
        state: String, code: String, credentialWallet: TestCredentialWallet, clientId: String, walletId: UUID, authReqSessionId: String
    ) = let {

        // 1. Validate State
        // 2. Exchange code for token
        // 3. Exchange token for credential

        // 1.
        val authReqSession = authReqSessions[authReqSessionId] ?: throw IllegalArgumentException("No authorization request found")
        require(authReqSession.authorizationRequest.state == state) {"Invalid state parameter"}

        // 2.
        val tokenResponse = exchangeCodeForToken( authReqSession, code, credentialWallet, clientId)

        // 3.
        val credentialResponses = getCredential( authReqSession, tokenResponse,  credentialWallet)
        require(credentialResponses.all { it.credential != null }) { "No credential was returned from credentialEndpoint: $credentialResponses"}
        credentialResponses.map { getCredentialData(it, null) }
    }

    private suspend fun exchangeCodeForToken(
        authReqSession: AuthReqSession,
        code: String,
        credentialWallet: TestCredentialWallet,
        clientId: String,
    ): TokenResponse {

        logger.debug("// fetch access token using authorized code")
        val tokenReq = TokenRequest(
            grantType = GrantType.authorization_code,
            clientId = clientId,
            redirectUri = credentialWallet.config.redirectUri,
            code = code,
        )

        val tokenResp = http.submitForm(authReqSession.issuerMetadata.tokenEndpoint!!, formParameters = parametersOf(tokenReq.toHttpParameters())).let {
            logger.debug("tokenResp raw: {}", it)
            it.body<JsonObject>().let { TokenResponse.fromJSON(it) }
        }

        return tokenResp
    }

    private suspend fun getCredential(
        authReqSession: AuthReqSession,
        tokenResponse: TokenResponse,
        credentialWallet: TestCredentialWallet
    ): List<CredentialResponse> {
        val nonce = tokenResponse.cNonce

        val offeredCredential = OfferedCredential(
            format = authReqSession.authorizationRequest.authorizationDetails?.firstOrNull()?.format ?: CredentialFormat.jwt_vc_json,
            types = authReqSession.authorizationRequest.authorizationDetails?.firstOrNull()?.types,
        )

        val credReqs = listOf(CredentialRequest.forOfferedCredential(
                offeredCredential = offeredCredential,
                proof = credentialWallet.generateDidProof(
                    did = credentialWallet.did,
                    issuerUrl = authReqSession.issuerMetadata.credentialIssuer!!,
                    nonce = nonce
                )
            )
        )

        return when {
            credReqs.size == 1 -> {
                val credReq = credReqs.first()

                val credentialResponse = http.post(authReqSession.issuerMetadata.credentialEndpoint!!) {
                    contentType(ContentType.Application.Json)
                    bearerAuth(tokenResponse.accessToken!!)
                    setBody(credReq.toJSON())
                }.body<JsonObject>().let { CredentialResponse.fromJSON(it) }

                listOf(credentialResponse)
            }

            else -> {
                val batchCredentialRequest = BatchCredentialRequest(credReqs)

                val credentialResponses = http.post(authReqSession.issuerMetadata.batchCredentialEndpoint!!) {
                    contentType(ContentType.Application.Json)
                    bearerAuth(tokenResponse.accessToken!!)
                    setBody(batchCredentialRequest.toJSON())
                }.body<JsonObject>().let { BatchCredentialResponse.fromJSON(it) }
                logger.debug { "credentialResponses: $credentialResponses" }

                credentialResponses.credentialResponses ?: throw IllegalArgumentException("No credential responses returned")
            }
        }
    }

    private suspend fun processMSEntraIssuanceRequest(
        entraIssuanceRequest: EntraIssuanceRequest,
        credentialWallet: TestCredentialWallet,
        pin: String? = null,
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
        return listOf(CredentialResponse.Companion.success(CredentialFormat.jwt_vc_json, vc))
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

    private suspend fun getCredentialData(
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

    private suspend fun parseJwtCredentialResponse(
        credentialJwt: JwsUtils.JwsParts, document: String, manifest: JsonObject?, type: String,
    ) = let {
        val credentialId =
            credentialJwt.payload["vc"]!!.jsonObject["id"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                ?: randomUUID()

        logger.debug { "Got JWT credential: $credentialJwt" }

        CredentialDataResult(
            id = credentialId,
            document = document,
            manifest = manifest?.toString(),
            type = type,
        )
    }

    private suspend fun parseSdJwtCredentialResponse(
        credentialJwt: JwsUtils.JwsParts, document: String, manifest: JsonObject?, type: String,
    ) = let {
        val credentialId =
            credentialJwt.payload["id"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: randomUUID()

        logger.debug { "Got SD-JWT credential: $credentialJwt" }

        val disclosures = credentialJwt.signature.split("~").drop(1)
        logger.debug { "Disclosures (${disclosures.size}): $disclosures" }

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

    @Serializable
    data class ResolveAuthorizationRequestResponse(
        val request: String,
        val type: String,
        val consentPageUri: String
    )

}
