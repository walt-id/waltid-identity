package id.walt.oid4vc.providers

import id.walt.crypto.keys.Key
import id.walt.crypto.utils.UuidUtils.randomUUIDString
import id.walt.did.dids.DidService.resolve
import id.walt.oid4vc.OpenID4VCI
import id.walt.oid4vc.data.*
import id.walt.oid4vc.data.dif.PresentationDefinition
import id.walt.oid4vc.errors.*
import id.walt.oid4vc.interfaces.IHttpClient
import id.walt.oid4vc.interfaces.ITokenProvider
import id.walt.oid4vc.interfaces.IVPTokenProvider
import id.walt.oid4vc.requests.*
import id.walt.oid4vc.responses.*
import id.walt.oid4vc.util.JwtUtils
import io.ktor.http.*
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.*
import org.kotlincrypto.hash.sha2.SHA256
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
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

    /**
     * Get the DID to use for this session
     */
    abstract fun getDidFor(session: S): String

    private fun httpGetAsJson(url: Url): JsonElement? =
        httpGet(url).body?.let { Json.decodeFromString<JsonElement>(it) }

    open suspend fun generateDidProof(
        did: String,
        issuerUrl: String,
        nonce: String?,
        client: OpenIDClientConfig? = null,
        proofType: ProofType = ProofType.jwt
    ): ProofOfPossession {
        // NOTE: This object/method is obsolete and will be removed or replaced
        val keyId = resolveDID(did)
        return when (proofType) {
            ProofType.cwt -> {
                ProofOfPossession.CWTProofBuilder(issuerUrl, client?.clientID, nonce).let { builder ->
                    builder.build(
                        signCWTToken(TokenTarget.PROOF_OF_POSSESSION, builder.payload, builder.headers, keyId)
                    )
                }
            }

            ProofType.ldp_vp -> TODO("ldp_vp proof not yet implemented")
            else -> {
                val didObj = resolve(did).getOrThrow()
                val jwtHeaderKeyId = (didObj["authentication"] ?: didObj["assertionMethod"]
                ?: didObj["verificationMethod"])?.jsonArray?.firstOrNull()?.let {
                    if (it is JsonObject) it.jsonObject["id"]?.jsonPrimitive?.content
                    else it.jsonPrimitive.contentOrNull
                } ?: did
                ProofOfPossession.JWTProofBuilder(
                    issuerUrl = issuerUrl,
                    clientId = client?.clientID,
                    nonce = nonce,
                    keyId = jwtHeaderKeyId,
                ).let { builder ->
                    builder.build(
                        signToken(TokenTarget.PROOF_OF_POSSESSION, builder.payload, builder.headers, keyId)
                    )
                }
            }

        }
    }

    open suspend fun generateKeyProof(
        key: Key,
        cosePubKey: ByteArray?,
        issuerUrl: String,
        nonce: String?,
        client: OpenIDClientConfig? = null,
        proofType: ProofType = ProofType.jwt
    ): ProofOfPossession {
        return when (proofType) {
            ProofType.cwt ->
                ProofOfPossession.CWTProofBuilder(issuerUrl, client?.clientID, nonce, coseKey = cosePubKey)
                    .let { builder ->
                        builder.build(
                            signCWTToken(
                                TokenTarget.PROOF_OF_POSSESSION,
                                builder.payload,
                                builder.headers,
                                key.getKeyId()
                            )
                        )
                    }

            ProofType.ldp_vp -> TODO("ldp_vp proof not yet implemented")
            else ->
                ProofOfPossession.JWTProofBuilder(
                    issuerUrl, client?.clientID, nonce,
                    keyJwk = key.getPublicKey().exportJWKObject(), keyId = key.getKeyId()
                ).let { builder ->
                    builder.build(
                        signToken(TokenTarget.PROOF_OF_POSSESSION, builder.payload, builder.headers, key.getKeyId())
                    )
                }
        }
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
        return ((authorizationRequest.responseType.contains(ResponseType.VpToken) ||
                authorizationRequest.responseType.contains(ResponseType.IdToken)) &&
                authorizationRequest.presentationDefinition != null &&
                isPresentationDefinitionSupported(authorizationRequest.presentationDefinition)
                )
    }

    open fun resolveVPAuthorizationParameters(authorizationRequest: AuthorizationRequest): AuthorizationRequest {
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
                    }
                    ?: authorizationRequest.claims?.get("vp_token")?.jsonObject?.get("presentation_definition")?.jsonObject?.let {
                        PresentationDefinition.fromJSON(it)
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
            exc.printStackTrace()
            throw AuthorizationError(
                authorizationRequest,
                AuthorizationErrorCode.invalid_presentation_definition_reference,
                "Invalid presentation definition reference, deserialization failed due to: ${exc.message}"
            )
        }
    }

    protected abstract fun createSIOPSession(
        id: String,
        authorizationRequest: AuthorizationRequest?,
        expirationTimestamp: Instant
    ): S

    //the authServerState is added because of AuthorizationSession()
    override fun initializeAuthorization(
        authorizationRequest: AuthorizationRequest,
        expiresIn: Duration,
        authServerState: String?
    ): S {
        val resolvedAuthReq = resolveVPAuthorizationParameters(authorizationRequest)
        return if (validateAuthorizationRequest(resolvedAuthReq)) {
            createSIOPSession(
                id = randomUUIDString(),
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
        val presentationDefinition = session.authorizationRequest?.presentationDefinition ?: throw TokenError(
            tokenRequest,
            TokenErrorCode.invalid_request
        )
        val result = generatePresentationForVPToken(session, tokenRequest)
        val holderDid = getDidFor(session)
        val idToken = if (session.authorizationRequest?.responseType?.contains(ResponseType.IdToken) == true) {
            signToken(TokenTarget.TOKEN, buildJsonObject {
                put("iss", "https://self-issued.me/v2/openid-vc")
                put("sub", holderDid)
                put("aud", session.authorizationRequest!!.clientId)
                put("exp", Clock.System.now().plus(5.minutes).epochSeconds)
                put("iat", Clock.System.now().epochSeconds)
                put("state", session.id)
                put("nonce", session.nonce)
                put("_vp_token", buildJsonObject {
                    put("presentation_submission", result.presentationSubmission.toJSON())
                })
            }, keyId = resolveDID(holderDid))
        } else null
        return if (result.presentations.size == 1) {
            TokenResponse.success(
                result.presentations.first().let { VpTokenParameter.fromJsonElement(it) },
                if (idToken == null) result.presentationSubmission else null,
                idToken = idToken,
                state = session.authorizationRequest?.state
            )
        } else {
            TokenResponse.success(
                JsonArray(result.presentations).let { VpTokenParameter.fromJsonElement(it) },
                if (idToken == null) result.presentationSubmission else null,
                idToken = idToken,
                state = session.authorizationRequest?.state
            )
        }
    }

    // ==========================================================
    // ===============  issuance flow ===========================
    open fun resolveCredentialOffer(credentialOfferRequest: CredentialOfferRequest): CredentialOffer {
        return credentialOfferRequest.credentialOffer ?: credentialOfferRequest.credentialOfferUri?.let { uri ->
            httpGetAsJson(Url(uri))?.jsonObject?.let { CredentialOffer.fromJSON(it) }
        } ?: throw CredentialOfferError(
            credentialOfferRequest,
            null,
            CredentialOfferErrorCode.invalid_request,
            "No credential offer value found on request, and credential offer could not be fetched by reference from given credential_offer_uri"
        )
    }

    open suspend fun executePreAuthorizedCodeFlow(
        credentialOffer: CredentialOffer,
        holderDid: String,
        client: OpenIDClientConfig,
        userPIN: String?
    ): List<CredentialResponse> {
        if (!credentialOffer.grants.containsKey(GrantType.pre_authorized_code.value)) throw CredentialOfferError(
            null,
            credentialOffer,
            CredentialOfferErrorCode.invalid_request,
            "Pre-authorized code issuance flow executed, but no pre-authorized_code found on credential offer"
        )
        val issuerMetadataUrl = getCIProviderMetadataUrl(credentialOffer.credentialIssuer)
        val issuerMetadata =
            httpGetAsJson(Url(issuerMetadataUrl))?.jsonObject?.let { OpenIDProviderMetadata.fromJSON(it) as OpenIDProviderMetadata.Draft13 }
                ?: throw CredentialOfferError(
                    null,
                    credentialOffer,
                    CredentialOfferErrorCode.invalid_issuer,
                    "Could not resolve issuer provider metadata from $issuerMetadataUrl"
                )
        val authorizationServerMetadata = issuerMetadata.authorizationServers?.let { authServer ->
            httpGetAsJson(Url(getCommonProviderMetadataUrl(authServer.first())))?.jsonObject?.let {
                OpenIDProviderMetadata.fromJSON(
                    it
                )
            }
        } ?: issuerMetadata
        val offeredCredentials = OpenID4VCI.resolveOfferedCredentials(credentialOffer, issuerMetadata)

        return executeAuthorizedIssuanceCodeFlow(
            authorizationServerMetadata, issuerMetadata, credentialOffer, GrantType.pre_authorized_code,
            offeredCredentials, holderDid, client, null, null, userPIN
        )
    }

    @OptIn(ExperimentalEncodingApi::class)
    open suspend fun executeFullAuthIssuance(
        credentialOffer: CredentialOffer,
        holderDid: String,
        client: OpenIDClientConfig
    ): List<CredentialResponse> {
        if (!credentialOffer.grants.containsKey(GrantType.authorization_code.value)) throw CredentialOfferError(
            null,
            credentialOffer,
            CredentialOfferErrorCode.invalid_request,
            "Full authorization issuance flow executed, but no authorization_code found on credential offer"
        )
        val issuerMetadataUrl = getCIProviderMetadataUrl(credentialOffer.credentialIssuer)
        val issuerMetadata =
            httpGetAsJson(Url(issuerMetadataUrl))?.jsonObject?.let { OpenIDProviderMetadata.fromJSON(it) as OpenIDProviderMetadata.Draft13 }
                ?: throw CredentialOfferError(
                    null,
                    credentialOffer,
                    CredentialOfferErrorCode.invalid_issuer,
                    "Could not resolve issuer provider metadata from $issuerMetadataUrl"
                )
        val authorizationServerMetadata = issuerMetadata.authorizationServers?.let { authServer ->
            httpGetAsJson(Url(getCommonProviderMetadataUrl(authServer.first())))?.jsonObject?.let {
                OpenIDProviderMetadata.fromJSON(
                    it
                ) as OpenIDProviderMetadata.Draft13
            }
        } ?: issuerMetadata as OpenIDProviderMetadata.Draft13
        val offeredCredentials = OpenID4VCI.resolveOfferedCredentials(credentialOffer, issuerMetadata)
        val codeVerifier = if (client.useCodeChallenge) randomUUIDString() else null

        val codeChallenge =
            codeVerifier?.let { Base64.UrlSafe.encode(SHA256().digest(it.toByteArray(Charsets.UTF_8))).trimEnd('=') }

        val authReq = AuthorizationRequest(
            responseType = setOf(ResponseType.Code),
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
                // 1. send pushed authorization request with authorization details, containing info of credentials to be issued, receive session id"
                val pushedAuthResp = httpSubmitForm(
                    Url(authorizationServerMetadata.pushedAuthorizationRequestEndpoint),
                    formParameters = parametersOf(authReq.toHttpParameters())
                ).body?.let { PushedAuthorizationResponse.fromJSONString(it) } ?: throw AuthorizationError(
                    authReq,
                    AuthorizationErrorCode.server_error,
                    "Pushed authorization request didn't succeed"
                )

                // 2. call authorize endpoint with request uri, receive HTTP redirect (302 Found) with Location header"
                AuthorizationRequest(
                    responseType = setOf(ResponseType.Code),
                    clientId = client.clientID,
                    requestUri = pushedAuthResp.requestUri
                )
            } else authReq
        }

        val authResp = httpGet(URLBuilder(Url(authorizationServerMetadata.authorizationEndpoint!!)).also {
            it.parameters.appendAll(parametersOf(authReq.toHttpParameters()))
        }.build())

        if (authResp.status != HttpStatusCode.Found) throw AuthorizationError(
            authReq,
            AuthorizationErrorCode.server_error,
            "Got unexpected status code ${authResp.status.value} from issuer"
        )
        var location = Url(authResp.headers[HttpHeaders.Location]!!)

        location =
            if (location.parameters.contains("response_type") && location.parameters["response_type"] == ResponseType.IdToken.name) {
                executeIdTokenAuthorization(location, holderDid, client)
            } else if (location.parameters.contains("response_type") && location.parameters["response_type"] == ResponseType.VpToken.name) {
                executeVpTokenAuthorization(location, holderDid, client)
            } else location

        val code = location.parameters["code"] ?: throw AuthorizationError(
            authReq,
            AuthorizationErrorCode.server_error,
            "No authorization code received from server"
        )

        return executeAuthorizedIssuanceCodeFlow(
            authorizationServerMetadata, issuerMetadata, credentialOffer,
            GrantType.authorization_code, offeredCredentials, holderDid, client, code, codeVerifier
        )
    }

    open fun fetchDeferredCredential(
        credentialOffer: CredentialOffer,
        credentialResponse: CredentialResponse
    ): CredentialResponse {
        if (credentialResponse.acceptanceToken.isNullOrEmpty()) throw CredentialOfferError(
            null,
            credentialOffer,
            CredentialOfferErrorCode.invalid_request,
            "Credential offer has no acceptance token for fetching deferred credential"
        )
        val issuerMetadataUrl = getCIProviderMetadataUrl(credentialOffer.credentialIssuer)
        val issuerMetadata =
            httpGetAsJson(Url(issuerMetadataUrl))?.jsonObject?.let { OpenIDProviderMetadata.fromJSON(it) as OpenIDProviderMetadata.Draft13 }
                ?: throw CredentialOfferError(
                    null,
                    credentialOffer,
                    CredentialOfferErrorCode.invalid_issuer,
                    "Could not resolve issuer provider metadata from $issuerMetadataUrl"
                )
        if (issuerMetadata.deferredCredentialEndpoint.isNullOrEmpty()) throw CredentialOfferError(
            null,
            credentialOffer,
            CredentialOfferErrorCode.invalid_issuer,
            "No deferred credential endpoint found in issuer metadata"
        )
        val deferredCredResp = httpSubmitForm(Url(issuerMetadata.deferredCredentialEndpoint), parametersOf(), headers {
            append(HttpHeaders.Authorization, "Bearer ${credentialResponse.acceptanceToken}")
        })
        if (!deferredCredResp.status.isSuccess() || deferredCredResp.body.isNullOrEmpty()) throw CredentialError(
            null,
            CredentialErrorCode.server_error,
            "No credential received from deferred credential endpoint, or server responded with error status ${deferredCredResp.status}"
        )
        return CredentialResponse.fromJSONString(deferredCredResp.body)
    }

    protected open suspend fun executeAuthorizedIssuanceCodeFlow(
        authorizationServerMetadata: OpenIDProviderMetadata,
        issuerMetadata: OpenIDProviderMetadata,
        credentialOffer: CredentialOffer,
        grantType: GrantType,
        offeredCredentials: List<OfferedCredential>,
        holderDid: String,
        client: OpenIDClientConfig,
        authorizationCode: String? = null,
        codeVerifier: String? = null,
        userPIN: String? = null
    ): List<CredentialResponse> {
        val tokenReq = TokenRequest.PreAuthorizedCode(
            clientId = client.clientID,
            preAuthorizedCode = credentialOffer.grants[grantType.value]!!.preAuthorizedCode!!,
            userPIN = userPIN
        )

        authorizationServerMetadata as OpenIDProviderMetadata.Draft13
        issuerMetadata as OpenIDProviderMetadata.Draft13

        val tokenHttpResp =
            httpSubmitForm(Url(authorizationServerMetadata.tokenEndpoint!!), parametersOf(tokenReq.toHttpParameters()))
        if (!tokenHttpResp.status.isSuccess() || tokenHttpResp.body == null) throw TokenError(
            tokenReq,
            TokenErrorCode.server_error,
            "Server returned error code ${tokenHttpResp.status}, or empty body"
        )
        val tokenResp = TokenResponse.fromJSONString(tokenHttpResp.body)
        if (tokenResp.accessToken == null) throw TokenError(
            tokenReq,
            TokenErrorCode.server_error,
            "No access token returned by server"
        )

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
            executeBatchCredentialRequest(
                issuerMetadata.batchCredentialEndpoint!!,
                tokenResp.accessToken,
                offeredCredentials.map {
                    CredentialRequest.forOfferedCredential(
                        it,
                        generateDidProof(holderDid, credentialOffer.credentialIssuer, nonce, client)
                    )
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
            httpPostObject(
                Url(batchEndpoint),
                req.toJSON(),
                Headers.build { set(HttpHeaders.Authorization, "Bearer $accessToken") })
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

    protected open fun executeIdTokenAuthorization(
        idTokenRequestUri: Url,
        holderDid: String,
        client: OpenIDClientConfig
    ): Url {
        val authReq =
            AuthorizationRequest.fromHttpQueryString(idTokenRequestUri.encodedQuery).let { authorizationRequest ->
                authorizationRequest.customParameters["request"]?.let {
                    AuthorizationJSONRequest.fromJSON(
                        JwtUtils.parseJWTPayload(
                            it.first()
                        )
                    )
                }
                    ?: authorizationRequest
            }
        if (authReq.responseMode != ResponseMode.direct_post || !authReq.responseType.contains(ResponseType.IdToken) || authReq.redirectUri.isNullOrEmpty())
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

    open fun executeVpTokenAuthorization(vpTokenRequestUri: Url, holderDid: String, client: OpenIDClientConfig): Url {
        val authReq = AuthorizationRequest.fromHttpQueryString(vpTokenRequestUri.encodedQuery)
        val tokenResp = processImplicitFlowAuthorization(
            authReq.copy(
                clientId = client.clientID,
            )
        )
        val httpResp = httpSubmitForm(
            Url(authReq.responseUri ?: authReq.redirectUri!!),
            parametersOf(tokenResp.toHttpParameters())
        )
        return when (httpResp.status) {
            HttpStatusCode.Found -> httpResp.headers[HttpHeaders.Location]
            HttpStatusCode.OK -> httpResp.body?.let { AuthorizationDirectPostResponse.fromJSONString(it) }?.redirectUri
            else -> null
        }?.let { Url(it) } ?: throw AuthorizationError(
            authReq,
            AuthorizationErrorCode.invalid_request,
            "Request could not be executed"
        )
    }

}
