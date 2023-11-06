package id.walt.oid4vc.providers

import id.walt.oid4vc.data.*
import id.walt.oid4vc.data.dif.PresentationDefinition
import id.walt.oid4vc.definitions.JWTClaims
import id.walt.oid4vc.errors.*
import id.walt.oid4vc.interfaces.IHttpClient
import id.walt.oid4vc.interfaces.ITokenProvider
import id.walt.oid4vc.interfaces.IVPTokenProvider
import id.walt.oid4vc.requests.*
import id.walt.oid4vc.responses.*
import id.walt.oid4vc.util.randomUUID
import id.walt.oid4vc.util.sha256
import id.walt.sdjwt.SDJwt
import io.ktor.http.*
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.*
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.js.ExperimentalJsExport
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Base object for a self-issued OpenID provider, providing identity information by presenting verifiable credentials,
 * in reply to OpenID4VP authorization requests.
 * e.g.: Verifiable Credentials holder wallets
 */
abstract class OpenIDCredentialWallet<S : SIOPSession>(
    baseUrl: String,
    override val config: CredentialWalletConfig
) : OpenIDProvider<S>(baseUrl), ITokenProvider, IVPTokenProvider<S>, IHttpClient {
    /**
     * Resolve DID to key ID
     * @param did DID to resolve
     * @return Key ID of resolved DID, as resolvable by given crypto provider
     */
    abstract fun resolveDID(did: String): String

    fun httpGetAsJson(url: Url): JsonElement? = httpGet(url).body?.let { Json.decodeFromString<JsonElement>(it) }

    open fun generateDidProof(
        did: String,
        issuerUrl: String,
        nonce: String?,
        client: OpenIDClientConfig? = null
    ): ProofOfPossession {
        val keyId = resolveDID(did)
        return ProofOfPossession(
            jwt = signToken(TokenTarget.PROOF_OF_POSSESSION, buildJsonObject {
                client?.let { put(JWTClaims.Payload.issuer, it.clientID) }
                put(JWTClaims.Payload.audience, issuerUrl)
                put(JWTClaims.Payload.issuedAtTime, Clock.System.now().epochSeconds)
                nonce?.let { put(JWTClaims.Payload.nonce, it) }
            }, header = buildJsonObject {
                put(JWTClaims.Header.keyID, keyId)
                put(JWTClaims.Header.type, "openid4vci-proof+jwt")
            }, keyId = keyId)
        )
    }

    open fun getCIProviderMetadataUrl(baseUrl: String): String {
        return URLBuilder(baseUrl).apply {
            appendPathSegments(".well-known", "openid-credential-issuer")
        }.buildString()
    }

    fun getCommonProviderMetadataUrl(baseUrl: String): String {
        return URLBuilder(baseUrl).apply {
            appendPathSegments(".well-known", "openid-configuration")
        }.buildString()
    }

    protected abstract fun isPresentationDefinitionSupported(presentationDefinition: PresentationDefinition): Boolean

    override fun validateAuthorizationRequest(authorizationRequest: AuthorizationRequest): Boolean {
        return (authorizationRequest.responseType == ResponseType.vp_token.name &&
                authorizationRequest.presentationDefinition != null &&
                isPresentationDefinitionSupported(authorizationRequest.presentationDefinition)
                ) //|| true // FIXME
    }

    protected open fun resolveVPAuthorizationParameters(authorizationRequest: AuthorizationRequest): AuthorizationRequest {
        try {
            return authorizationRequest.copy(
                presentationDefinition = authorizationRequest.presentationDefinition
                    ?: authorizationRequest.presentationDefinitionUri?.let {
                        PresentationDefinition.fromJSON(
                            httpGetAsJson(Url(it))?.jsonObject
                                ?: throw AuthorizationError(
                                    authorizationRequest,
                                    AuthorizationErrorCode.invalid_presentation_definition_uri,
                                    message = "Presentation definition URI cannot be resolved."
                                )
                        )
                    } ?: throw AuthorizationError(
                        authorizationRequest,
                        AuthorizationErrorCode.invalid_request,
                        message = "Presentation definition could not be resolved from presentation_definition or presentation_definition_uri parameters"
                    ),
                clientMetadata = authorizationRequest.clientMetadata
                    ?: authorizationRequest.clientMetadataUri?.let { uri ->
                        httpGetAsJson(Url(uri))?.jsonObject?.let { OpenIDClientMetadata.fromJSON(it) }
                    }
            )
        } catch (exc: SerializationException) {
            throw AuthorizationError(
                authorizationRequest,
                AuthorizationErrorCode.invalid_presentation_definition_reference
            )
        }
    }

    protected abstract fun createSIOPSession(id: String, authorizationRequest: AuthorizationRequest?, expirationTimestamp: Instant): S

    override fun initializeAuthorization(authorizationRequest: AuthorizationRequest, expiresIn: Duration): S {
        val resolvedAuthReq = resolveVPAuthorizationParameters(authorizationRequest)
        return if (validateAuthorizationRequest(resolvedAuthReq)) {
            createSIOPSession(
                id = randomUUID(),
                authorizationRequest = resolvedAuthReq,
                expirationTimestamp = Clock.System.now().plus(expiresIn)
            )
        } else {
            throw AuthorizationError(
                resolvedAuthReq,
                AuthorizationErrorCode.invalid_request,
                message = "Invalid VP authorization request"
            )
        }.also {
            putSession(it.id, it)
        }
    }

    override fun generateTokenResponse(session: S, tokenRequest: TokenRequest): TokenResponse {
        println("SIOPCredentialProvider generateTokenResponse")
        val presentationDefinition = session.authorizationRequest?.presentationDefinition ?: throw TokenError(
            tokenRequest,
            TokenErrorCode.invalid_request
        )
        val result = generatePresentationForVPToken(session, tokenRequest)
        return if (result.presentations.size == 1) {
            TokenResponse.success(
                result.presentations.first(),
                result.presentationSubmission,
                session.authorizationRequest?.state
            )
        } else {
            TokenResponse.success(
                JsonArray(result.presentations),
                result.presentationSubmission,
                session.authorizationRequest?.state
            )
        }
    }

    // ==========================================================
    // ===============  issuance flow ===========================
    open fun getCredentialOffer(credentialOfferRequest: CredentialOfferRequest): CredentialOffer {
        return credentialOfferRequest.credentialOffer ?: credentialOfferRequest.credentialOfferUri?.let { uri ->
            httpGetAsJson(Url(uri))?.jsonObject?.let { CredentialOffer.fromJSON(it) }
        } ?: throw CredentialOfferError(
            credentialOfferRequest,
            CredentialOfferErrorCode.invalid_request,
            "No credential offer value found on request, and credential offer could not be fetched by reference from given credential_offer_uri"
        )
    }

    @OptIn(ExperimentalEncodingApi::class)
    open fun executeFullAuthIssuance(
        credentialOfferRequest: CredentialOfferRequest,
        holderDid: String,
        client: OpenIDClientConfig
    ): List<CredentialResponse> {
        val credentialOffer = getCredentialOffer(credentialOfferRequest)
        if (!credentialOffer.grants.containsKey(GrantType.authorization_code.value)) throw CredentialOfferError(
            credentialOfferRequest,
            CredentialOfferErrorCode.invalid_request,
            "Full authorization issuance flow executed, but no authorization_code found on credential offer"
        )
        val issuerMetadataUrl = getCIProviderMetadataUrl(credentialOffer.credentialIssuer)
        val issuerMetadata =
            httpGetAsJson(Url(issuerMetadataUrl))?.jsonObject?.let { OpenIDProviderMetadata.fromJSON(it) } ?: throw CredentialOfferError(
                credentialOfferRequest,
                CredentialOfferErrorCode.invalid_issuer,
                "Could not resolve issuer provider metadata from $issuerMetadataUrl"
            )
        val authorizationServerMetadata = issuerMetadata.authorizationServer?.let { authServer ->
            httpGetAsJson(Url(getCommonProviderMetadataUrl(authServer)))?.jsonObject?.let { OpenIDProviderMetadata.fromJSON(it) }
        } ?: issuerMetadata
        val offeredCredentials = credentialOffer.resolveOfferedCredentials(issuerMetadata)
        val codeVerifier = if (client.useCodeChallenge) randomUUID() else null
        val codeChallenge = codeVerifier?.let { Base64.UrlSafe.encode(sha256(it.toByteArray(Charsets.UTF_8))).trimEnd('=') }

        val authReq = AuthorizationRequest(
            responseType = ResponseType.getResponseTypeString(ResponseType.code),
            clientId = client.clientID,
            redirectUri = config.redirectUri,
            scope = setOf("openid"),
            issuerState = credentialOffer.grants[GrantType.authorization_code.value]?.issuerState,
            authorizationDetails = offeredCredentials.map {
                AuthorizationDetails.fromOfferedCredential(
                    it,
                    issuerMetadata.credentialIssuer
                )
            },
            codeChallenge = codeChallenge,
            codeChallengeMethod = codeChallenge?.let { "S256" }
        ).let { authReq ->
            if (authorizationServerMetadata.pushedAuthorizationRequestEndpoint != null) {
                // execute pushed authorization request
                println("// 1. send pushed authorization request with authorization details, containing info of credentials to be issued, receive session id")
                println("pushedAuthReq: $authReq")

                val pushedAuthResp = httpSubmitForm(
                    Url(authorizationServerMetadata.pushedAuthorizationRequestEndpoint),
                    formParameters = parametersOf(authReq.toHttpParameters())
                ).body?.let { PushedAuthorizationResponse.fromJSONString(it) } ?: throw AuthorizationError(
                    authReq,
                    AuthorizationErrorCode.server_error,
                    "Pushed authorization request didn't succeed"
                )
                println("pushedAuthResp: $pushedAuthResp")

                println("// 2. call authorize endpoint with request uri, receive HTTP redirect (302 Found) with Location header")
                AuthorizationRequest(
                    responseType = ResponseType.code.name,
                    clientId = client.clientID,
                    requestUri = pushedAuthResp.requestUri
                )
            } else authReq
        }

        println("authReq: $authReq")
        val authResp = httpGet(URLBuilder(Url(authorizationServerMetadata.authorizationEndpoint!!)).also {
            it.parameters.appendAll(parametersOf(authReq.toHttpParameters()))
        }.build())
        println("authResp: $authResp")
        if (authResp.status != HttpStatusCode.Found) throw AuthorizationError(
            authReq,
            AuthorizationErrorCode.server_error,
            "Got unexpected status code ${authResp.status.value} from issuer"
        )
        var location = Url(authResp.headers[HttpHeaders.Location]!!)
        println("location: $location")
        location =
            if (location.parameters.contains("response_type") && location.parameters["response_type"] == ResponseType.id_token.name) {
                executeIdTokenAuthorization(location, holderDid, client)
            } else location

        val code = location.parameters["code"] ?: throw AuthorizationError(
            authReq,
            AuthorizationErrorCode.server_error,
            "No authorization code received from server"
        )

        val tokenReq = TokenRequest(GrantType.authorization_code, client.clientID, config.redirectUri, code, codeVerifier = codeVerifier)
        val tokenHttpResp = httpSubmitForm(Url(authorizationServerMetadata.tokenEndpoint!!), parametersOf(tokenReq.toHttpParameters()))
        if (!tokenHttpResp.status.isSuccess() || tokenHttpResp.body == null) throw TokenError(
            tokenReq,
            TokenErrorCode.server_error,
            "Server returned error code ${tokenHttpResp.status}, or empty body"
        )
        val tokenResp = TokenResponse.fromJSONString(tokenHttpResp.body)
        if (tokenResp.accessToken == null) throw TokenError(tokenReq, TokenErrorCode.server_error, "No access token returned by server")

        var nonce = tokenResp.cNonce
        return if (issuerMetadata.batchCredentialEndpoint.isNullOrEmpty() || offeredCredentials.size == 1) {
            // execute credential requests individually
            offeredCredentials.map { offeredCredential ->
                val credReq = CredentialRequest.forOfferedCredential(
                    offeredCredential,
                    generateDidProof(holderDid, credentialOffer.credentialIssuer, nonce, client)
                )
                executeCredentialRequest(
                    issuerMetadata.credentialEndpoint ?: throw CredentialError(
                        credReq,
                        CredentialErrorCode.server_error,
                        "No credential endpoint specified in issuer metadata"
                    ),
                    tokenResp.accessToken, credReq
                ).also {
                    nonce = it.cNonce ?: nonce
                }
            }
        } else {
            // execute batch credential request
            executeBatchCredentialRequest(issuerMetadata.batchCredentialEndpoint, tokenResp.accessToken, offeredCredentials.map {
                CredentialRequest.forOfferedCredential(it, generateDidProof(holderDid, credentialOffer.credentialIssuer, nonce, client))
            })
        }
    }

    protected open fun executeBatchCredentialRequest(
        batchEndpoint: String,
        accessToken: String,
        credentialRequests: List<CredentialRequest>
    ): List<CredentialResponse> {
        val req = BatchCredentialRequest(credentialRequests)
        val httpResp =
            httpPostObject(Url(batchEndpoint), req.toJSON(), Headers.build { set(HttpHeaders.Authorization, "Bearer $accessToken") })
        if (!httpResp.status.isSuccess() || httpResp.body == null) throw BatchCredentialError(
            req,
            CredentialErrorCode.server_error,
            "Batch credential endpoint returned error status ${httpResp.status}, or body is empty"
        )
        return BatchCredentialResponse.fromJSONString(httpResp.body).credentialResponses ?: listOf()
    }

    protected open fun executeCredentialRequest(
        credentialEndpoint: String,
        accessToken: String,
        credentialRequest: CredentialRequest
    ): CredentialResponse {
        val httpResp = httpPostObject(
            Url(credentialEndpoint),
            credentialRequest.toJSON(),
            Headers.build { set(HttpHeaders.Authorization, "Bearer $accessToken") })
        if (!httpResp.status.isSuccess() || httpResp.body == null) throw CredentialError(
            credentialRequest,
            CredentialErrorCode.server_error,
            "Credential error returned error status ${httpResp.status}, or body is empty"
        )
        return CredentialResponse.fromJSONString(httpResp.body)
    }

    @OptIn(ExperimentalJsExport::class)
    protected open fun executeIdTokenAuthorization(idTokenRequestUri: Url, holderDid: String, client: OpenIDClientConfig): Url {
        var authReq = AuthorizationRequest.fromHttpQueryString(idTokenRequestUri.encodedQuery).let { authorizationRequest ->
            authorizationRequest.customParameters["request"]?.let { AuthorizationJSONRequest.fromJSON(SDJwt.parse(it.first()).fullPayload) }
                ?: authorizationRequest
        }
        if (authReq.responseMode != ResponseMode.direct_post || authReq.responseType != ResponseType.id_token.name || authReq.redirectUri.isNullOrEmpty())
            throw AuthorizationError(
                authReq,
                AuthorizationErrorCode.server_error,
                "Unexpected response_mode ${authReq.responseMode}, or response_type ${authReq.responseType} returned from server, or redirect_uri is missing"
            )

        val keyId = resolveDID(holderDid)
        val idToken = signToken(TokenTarget.TOKEN, buildJsonObject {
            put("iss", holderDid)
            put("sub", holderDid)
            put("aud", authReq.clientId)
            put("exp", Clock.System.now().plus(5.minutes).epochSeconds)
            put("iat", Clock.System.now().epochSeconds)
            put("state", authReq.state)
            put("nonce", authReq.nonce)
        }, keyId = keyId)
        val httpResp = httpSubmitForm(
            Url(authReq.redirectUri!!),
            parametersOf(Pair("id_token", listOf(idToken)), Pair("state", listOf(authReq.state!!)))
        )
        if (httpResp.status != HttpStatusCode.Found) throw AuthorizationError(
            authReq,
            AuthorizationErrorCode.server_error,
            "Unexpected status code ${httpResp.status} returned from server for id_token response"
        )
        return httpResp.headers[HttpHeaders.Location]?.let { Url(it) }
            ?: throw AuthorizationError(
                authReq,
                AuthorizationErrorCode.server_error,
                "Location parameter missing on http response for id_token response"
            )
    }

}
