@file:OptIn(ExperimentalSerializationApi::class)

package id.waltid.openid4vp.wallet

import id.walt.credentials.formats.DigitalCredential
import id.walt.credentials.signatures.sdjwt.SdJwtSelectiveDisclosure
import id.walt.credentials.utils.JwtUtils.isJwt
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.JwsUtils.decodeJws
import id.walt.crypto.utils.ShaUtils.calculateSha256Base64Url
import id.walt.crypto.utils.Base64Utils.decodeFromBase64Url
import id.walt.dcql.DcqlMatcher
import id.walt.dcql.RawDcqlCredential
import id.walt.dcql.models.CredentialFormat
import id.walt.dcql.models.DcqlQuery
import id.walt.holderpolicies.HolderPolicy
import id.walt.holderpolicies.HolderPolicyEngine
import id.walt.openid4vp.clientidprefix.*
import id.walt.verifier.openid.models.authorization.AuthorizationRequest
import id.walt.verifier.openid.models.authorization.ClientMetadata
import id.walt.verifier.openid.models.openid.OpenID4VPResponseMode
import id.walt.verifier.openid.models.openid.OpenID4VPResponseType
import id.walt.webdatafetching.WebDataFetcher
import id.walt.webdatafetching.WebDataFetcherId
import id.walt.verifier.openid.transactiondata.calculateTransactionDataHashes
import id.walt.verifier.openid.transactiondata.decodeList
import id.walt.verifier.openid.transactiondata.resolveHashAlgorithm
import id.walt.verifier.openid.transactiondata.TransactionDataTypeRegistry
import id.walt.verifier.openid.transactiondata.validateRequestTransactionData
import id.waltid.openid4vp.wallet.presentation.LDPPresenter
import id.waltid.openid4vp.wallet.presentation.MdocPresenter
import id.waltid.openid4vp.wallet.presentation.SdJwtVcPresenter
import id.waltid.openid4vp.wallet.presentation.SelfIssuedIdTokenBuilder
import id.waltid.openid4vp.wallet.presentation.W3CPresenter
import id.waltid.openid4vp.wallet.request.AuthorizationRequestResolver
import id.waltid.openid4vp.wallet.response.ResponseEncryptionHandler
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.filter
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import kotlin.time.Clock
import kotlin.uuid.Uuid


object WalletPresentFunctionality2 {

    private val log = KotlinLogging.logger { }

    private val webResolveAuthReq = WebDataFetcher(WebDataFetcherId.OPENID4VP_WALLET_RESOLVE_AUTHORIZATIONREQUEST)
    private val webPostToken = WebDataFetcher(WebDataFetcherId.OPENID4VP_WALLET_POST_TOKEN)

    /**
     * @param matchedData: Credentials that were choosen by the DCQL query
     */
    private suspend fun generateVpTokenForRequest(
        authorizationRequest: AuthorizationRequest,
        matchedData: Map<String, List<DcqlMatcher.DcqlMatchResult>>,
        /** For mdocs: this is the device key */
        holderKey: Key,
        holderDid: String?,
        typeRegistry: TransactionDataTypeRegistry,
    ): String {
        val vpTokenMapContents = mutableMapOf<String, JsonArray>()

        for ((queryId, matchedCredsWithClaimsList) in matchedData) {
            log.trace { "Query ID: $queryId, matched credentials: $matchedCredsWithClaimsList" }
            val presentationsForThisQueryId = buildJsonArray {
                for (matchResult in matchedCredsWithClaimsList) {
                    val digitalCredential = (matchResult.credential as RawDcqlCredential).originalCredential as DigitalCredential

                    val resolvedFormat = WalletPresentationFormatRegistry.resolve(digitalCredential.format)
                    val presentationStringOrObject: JsonElement = when {
                        resolvedFormat == WalletPresentationFormatRegistry.SupportedFormat.JWT_VC_JSON -> W3CPresenter.presentW3C(
                            digitalCredential = digitalCredential,
                            matchResult = matchResult,
                            authorizationRequest = authorizationRequest,
                            holderKey = holderKey,
                            holderDid = holderDid ?: throw IllegalArgumentException("Missing DID for presentation"),
                        )

                        resolvedFormat == WalletPresentationFormatRegistry.SupportedFormat.DC_SD_JWT -> SdJwtVcPresenter.presentSdJwtVc(
                            digitalCredential = digitalCredential,
                            matchResult = matchResult,
                            authorizationRequest = authorizationRequest,
                            holderKey = holderKey,
                            holderDid = holderDid
                        )

                        resolvedFormat == WalletPresentationFormatRegistry.SupportedFormat.MSO_MDOC -> {
                            MdocPresenter.presentMdoc(
                                digitalCredential = digitalCredential,
                                matchResult = matchResult,
                                authorizationRequest = authorizationRequest,
                                holderKey = holderKey,
                                typeRegistry = typeRegistry,
                            )
                        }

                        // Kept separate because ldp_vc presentation is not implemented yet.
                        digitalCredential.format == CredentialFormat.LDP_VC.id.first() -> LDPPresenter.presentLdpTodo()

                        else ->
                            // Fallback for other formats or if it's a simple signed string
                            JsonPrimitive(
                                digitalCredential.signed
                                    ?: error("Credential for query $queryId is not signed and no specific presentation logic found for format ${digitalCredential.format}")
                            )
                    }
                    add(presentationStringOrObject)
                }
            }
            if (presentationsForThisQueryId.isNotEmpty()) {
                vpTokenMapContents[queryId] = presentationsForThisQueryId
            }
        }

        if (vpTokenMapContents.isEmpty() && authorizationRequest.dcqlQuery?.credentials?.isNotEmpty() == true) {
            // No credentials matched any part of a non-empty DCQL query.
            // Depending on policy, this might be an error or an empty vp_token.
            // OpenID4VP spec implies vp_token is only returned if something matches.
            // "A VP Token is only returned if the corresponding Authorization Request contained a dcql_query parameter..."
            // "...There MUST NOT be any entry in the JSON-encoded object for optional Credential Queries when there are no matching Credentials..."
            // So, if all queries were effectively optional and none matched, an empty vp_token is possible.
            // If required queries didn't match, DcqlMatcher should have failed earlier.
            log.warn { "No presentations generated for any query ID. Returning empty vp_token object." }
        }

        log.trace { "Generated VP Token Map Contents: $vpTokenMapContents" }
        return Json.encodeToString(JsonObject(vpTokenMapContents))
    }

    @Serializable
    data class WalletPresentResult(
        @SerialName("get_url")
        val getUrl: String? = null,

        @SerialName("form_post_html")
        val formPostHtml: String? = null,


        @SerialName("transmission_success")
        val transmissionSuccess: Boolean? = null,
        @SerialName("verifier_response")
        val verifierResponse: JsonElement? = null,

        @SerialName("redirect_to")
        val redirectTo: String? = null
    )

    /**
     * OpenID4VP 1.0 §8.5 wallet-side error codes (with RFC 6749 §4.1.2.1 / §4.2.2.1 parents).
     *
     * The verifier side accepts any `error` string (permissive); this typed enum only steers
     * wallet-side callers away from typos in the finite set that the spec enumerates. If a
     * future spec addition is needed before this enum is updated, use
     * [walletRejectHandling]'s String overload.
     */
    enum class OID4VPErrorCode(val code: String) {
        ACCESS_DENIED("access_denied"),
        INVALID_REQUEST("invalid_request"),
        INVALID_CLIENT("invalid_client"),
        INVALID_SCOPE("invalid_scope"),
        UNAUTHORIZED_CLIENT("unauthorized_client"),
        UNSUPPORTED_RESPONSE_TYPE("unsupported_response_type"),
        SERVER_ERROR("server_error"),
        TEMPORARILY_UNAVAILABLE("temporarily_unavailable"),
        VP_FORMATS_NOT_SUPPORTED("vp_formats_not_supported"),
        INVALID_REQUEST_URI_METHOD("invalid_request_uri_method"),
        INVALID_TRANSACTION_DATA("invalid_transaction_data"),
        WALLET_UNAVAILABLE("wallet_unavailable"),
        
        // Encryption-specific error codes (WAL-896)
        /** Encryption is required but cannot be performed. */
        ENCRYPTION_REQUIRED("encryption_required"),
        /** Invalid encryption parameters in client_metadata. */
        INVALID_ENCRYPTION_PARAMETERS("invalid_encryption_parameters"),
        /** Failed to encrypt the authorization response. */
        ENCRYPTION_FAILURE("encryption_failure"),
    }

    /**
     * Produce an OpenID4VP 1.0 §8.5 wallet rejection response for the given
     * [authorizationRequest]. The response is shaped according to the request's `response_mode`
     * and carries `error`, optional `error_description`, and the request's `state` (when
     * present). For `direct_post` and `direct_post.jwt`, the rejection is transmitted to the
     * verifier's `response_uri` and the verifier's acknowledgement is returned. OpenID4VP 1.0
     * permits an unencrypted error response for `direct_post.jwt` when the Wallet cannot generate
     * the encrypted response.
     */
    suspend fun walletRejectHandling(
        authorizationRequest: AuthorizationRequest,
        error: OID4VPErrorCode = OID4VPErrorCode.ACCESS_DENIED,
        errorDescription: String? = null,
    ): Result<WalletPresentResult> = walletRejectHandling(authorizationRequest, error.code, errorDescription)

    /**
     * String overload of [walletRejectHandling] for forward-compatibility with error codes
     * not yet in [OID4VPErrorCode]. Prefer the typed variant.
     */
    suspend fun walletRejectHandling(
        authorizationRequest: AuthorizationRequest,
        error: String,
        errorDescription: String? = null,
    ): Result<WalletPresentResult> = runCatching {
        val responseMode = authorizationRequest.responseMode ?: inferResponseMode(authorizationRequest)
        val errorParameters = buildErrorResponseParameters(authorizationRequest, error, errorDescription)
        val redirectUri = authorizationRequest.redirectUri
        val responseUri = authorizationRequest.responseUri

        when (responseMode) {
            OpenID4VPResponseMode.FRAGMENT -> {
                requireNotNull(redirectUri) { "Invalid AuthorizationRequest: 'redirect_uri' is required for response_mode 'fragment'." }
                WalletPresentResult(getUrl = "${redirectUri}#${errorParameters.formUrlEncode()}")
            }

            OpenID4VPResponseMode.QUERY -> {
                requireNotNull(redirectUri) { "Invalid AuthorizationRequest: 'redirect_uri' is required for response_mode 'query'." }
                WalletPresentResult(getUrl = URLBuilder(redirectUri).apply { parameters.appendAll(errorParameters) }.buildString())
            }

            OpenID4VPResponseMode.FORM_POST -> {
                requireNotNull(redirectUri) { "Invalid AuthorizationRequest: 'redirect_uri' is required for response_mode 'form_post'." }
                WalletPresentResult(
                    formPostHtml = buildFormPostHtml(
                        actionUrl = redirectUri,
                        title = "Submitting Error Response...",
                        fields = errorParameters.entries().flatMap { (name, values) -> values.map { name to it } },
                    )
                )
            }

            OpenID4VPResponseMode.DIRECT_POST, OpenID4VPResponseMode.DIRECT_POST_JWT -> {
                require(responseUri != null) { "Invalid AuthorizationRequest: 'response_uri' is required for response_mode '$responseMode'." }
                postFormResponse(responseUri, errorParameters)
            }

            OpenID4VPResponseMode.DC_API, OpenID4VPResponseMode.DC_API_JWT ->
                throw UnsupportedOperationException("OID4VP error responses are not supported for DC API response modes.")

            null -> throw IllegalArgumentException("Missing response mode from AuthorizationRequest")
        }
    }

    private fun inferResponseMode(authorizationRequest: AuthorizationRequest): OpenID4VPResponseMode? {
        val responseType = authorizationRequest.responseType?.responseType
        return when {
            responseType == null -> null
            "vp_token" in responseType && "code" !in responseType -> OpenID4VPResponseMode.FRAGMENT
            "code" in responseType -> OpenID4VPResponseMode.QUERY
            else -> null
        }
    }

    /**
     * Generates a fresh wallet_nonce per OID4VP 1.0 §5.6.
     */
        private fun generateWalletNonce(): String = Uuid.random().toHexString()

    private fun buildErrorResponseParameters(
        authorizationRequest: AuthorizationRequest,
        error: String,
        errorDescription: String?,
    ): Parameters = ParametersBuilder().apply {
        append("error", error)
        errorDescription?.let { append("error_description", it) }
        authorizationRequest.state?.let { append("state", it) }
    }.build()

    private suspend fun postFormResponse(
        responseUri: String,
        parameters: Parameters,
    ): WalletPresentResult {
        val response = webPostToken.sendForm(responseUri, parameters)
        val responseBody = response.bodyAsText()
        val responseBodyJson = Json.parseToJsonElement(responseBody).jsonObject

        return WalletPresentResult(
            transmissionSuccess = response.status.isSuccess(),
            verifierResponse = responseBodyJson,
            redirectTo = responseBodyJson["redirect_uri"]?.jsonPrimitive?.content,
        )
    }

    /**
     * Build a self-submitting `form_post` HTML page that POSTs [fields] to [actionUrl].
     * Shared by [walletPresentHandling] (carrying `vp_token`) and [walletRejectHandling]
     * (carrying `error` / `error_description` / `state`).
     */
    private fun buildFormPostHtml(
        actionUrl: String,
        title: String,
        fields: List<Pair<String, String>>,
    ): String = buildString {
        appendLine("<!DOCTYPE html>")
        appendLine("<html>")
        appendLine("<head><title>${title.escapeHTML()}</title></head>")
        appendLine("<body onload=\"document.forms[0].submit()\">")
        appendLine("<noscript><p>Your browser does not support JavaScript. Please press the button below to continue.</p></noscript>")
        appendLine("<form method=\"POST\" action=\"${actionUrl.escapeHTML()}\">")
        fields.forEach { (name, value) ->
            appendLine("<input type=\"hidden\" name=\"${name.escapeHTML()}\" value=\"${value.escapeHTML()}\"/>")
        }
        appendLine("<input type=\"submit\" value=\"Continue\"/>")
        appendLine("</form>")
        appendLine("</body>")
        appendLine("</html>")
    }

    suspend fun walletPresentHandling(
        holderKey: Key,
        holderDid: String?,
        presentationRequestUrl: Url,

        selectCredentialsForQuery: suspend (DcqlQuery) -> Map<String, List<DcqlMatcher.DcqlMatchResult>>,

        holderPoliciesToRun: Flow<HolderPolicy>?,
        runPolicies: Boolean?,

        // Added from Branch B
        transactionDataTypeRegistry: TransactionDataTypeRegistry = TransactionDataTypeRegistry.STANDARD,

        // TODO: selected credentials

        /**
         * Fallback for ancient legacy tests, wrong integration tests, and various other stuff that should have long been removed
         * Use: `OldWalletPresentFunctionality.oldWalletPresentHandling(walletService, presentationRequestUrl, request)` for this
         */
        legacyFallbackCallback: (suspend (Url) -> Result<JsonElement>)? = null,

        // Added from Branch B
        unsignedRequestObjectPolicy: AuthorizationRequestResolver.UnsignedRequestObjectPolicy =
            AuthorizationRequestResolver.UnsignedRequestObjectPolicy.REQUIRE_SIGNED,
    ): Result<WalletPresentResult> {
        log.trace { "- Start of Wallet Present Handling -" }

        log.trace { "Wallet presentation will use key $holderKey, and did $holderDid" }

        // Resolve AuthorizationRequest (Kept Branch A's inline processing to ensure wallet_nonce logic operates seamlessly):
        val authorizationRequest: AuthorizationRequest = if (presentationRequestUrl.parameters.contains("request_uri")) {
            val requestUri = presentationRequestUrl.parameters["request_uri"]!!
            val requestUriMethod = presentationRequestUrl.parameters["request_uri_method"]

            log.trace { "Resolving AuthorizationRequest from URI: $requestUri (method=${requestUriMethod ?: "get"})" }

            // Per OID4VP 1.0 §5.1: if request_uri_method=post, send a POST with wallet_nonce
            // to bind this request and prevent replay attacks.
            val walletNonce: String? = if (requestUriMethod?.lowercase() == "post") {
                // Generate a fresh, cryptographically random wallet_nonce (base64url, 128 bits)
                generateWalletNonce()
            } else null

            val httpResponse = if (walletNonce != null) {
                log.trace { "Sending POST to request URI with wallet_nonce (request_uri_method=post)" }
                webResolveAuthReq.rawFetch(io.ktor.http.Url(requestUri)) {
                    method = HttpMethod.Post
                    headers.append(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
                    headers.append(HttpHeaders.Accept, "application/oauth-authz-req+jwt")
                    setBody("wallet_nonce=${walletNonce.encodeURLQueryComponent()}")
                }
            } else {
                webResolveAuthReq.rawFetch(requestUri)
            }

            check(httpResponse.status.isSuccess()) { "AuthorizationRequest cannot be retrieved (${httpResponse.status}): from $requestUri - ${httpResponse.bodyAsText()}" }

            val authorizationRequestContentType =
                httpResponse.contentType()
                    ?: throw IllegalArgumentException("AuthorizationRequest does not have HTTP ContentType header set: $requestUri")
            log.trace { "Retrieved response has content type: $authorizationRequestContentType" }

            val retrievedAuthorizationRequest = when {
                authorizationRequestContentType.match("application/oauth-authz-req+jwt") -> {

                    val authReqJwt = httpResponse.bodyAsText()
                    require(authReqJwt.isJwt()) { "Response for AuthorizationRequest should be JWT, but is not a valid JWT" }
                    val authReqJws = authReqJwt.decodeJws()
                    val jwtAlg = authReqJws.header["alg"]?.jsonPrimitive?.content
                    log.trace { "JWT AuthorizationRequest algorithm: $jwtAlg" }

                    if (jwtAlg.equals("none", true)) {
                        // Integrated Branch B's unsignedRequestObjectPolicy validation logic
                        if (unsignedRequestObjectPolicy == AuthorizationRequestResolver.UnsignedRequestObjectPolicy.REQUIRE_SIGNED) {
                            throw IllegalArgumentException(
                                "Authorization request JWT uses alg=none — unsigned requests are not accepted for request_uri signed flows."
                            )
                        } else {
                            Json.decodeFromJsonElement<AuthorizationRequest>(authReqJws.payload)
                        }
                    } else {
                        val authReqBody = authReqJws.payload

                        val clientId = authReqBody["client_id"]?.jsonPrimitive?.contentOrNull
                        log.trace { "AuthorizationRequest is signed, authentication with client ID: $clientId" }
                        require(clientId != null) { "Missing client_id for signed AuthorizationRequest authentication" }

                        val clientIdPrefix = ClientIdPrefixParser.parse(clientId)
                            .getOrElse { e -> throw IllegalArgumentException("Could not parse client id prefix: $clientId", e) }
                        log.trace { "Parsed client id prefix: $clientIdPrefix" }

                        val clientMetadata = authReqBody["client_metadata"]?.let {
                            ClientMetadata.fromJson(it)
                                .getOrElse { e -> throw IllegalArgumentException("Could not parse client metadata: $it", e) }
                        }

                        val redirectUri = authReqBody["redirect_uri"]?.jsonPrimitive?.contentOrNull
                        val responseUri = authReqBody["response_uri"]?.jsonPrimitive?.contentOrNull

                        val context = RequestContext(
                            clientId = clientId,
                            clientMetadata = clientMetadata,
                            requestObjectJws = authReqJwt,
                            redirectUri = redirectUri,
                            responseUri = responseUri
                        )

                        val result = ClientIdPrefixAuthenticator.authenticate(clientIdPrefix, context)
                        when (result) {
                            is ClientValidationResult.Failure -> {
                                // Preserved Branch A's direct handling to avoid unknown Exceptions from Branch B's resolver
                                if (result.error is ClientIdError.PreRegisteredClientNotFound && legacyFallbackCallback != null) {
                                    val fallbackResult = runCatching { legacyFallbackCallback(presentationRequestUrl) }
                                    if (fallbackResult.isSuccess && fallbackResult.getOrThrow().isSuccess)
                                        return Result.success(
                                            WalletPresentResult(
                                                transmissionSuccess = true,
                                                verifierResponse = fallbackResult.getOrThrow().getOrThrow()
                                            )
                                        )
                                }

                                throw IllegalArgumentException("Could not verify signed AuthorizationRequest with client id prefix: ${result.error::class.simpleName} - ${result.error.message}")
                            }

                            is ClientValidationResult.Success -> {
                                val decodedRequest = Json.decodeFromJsonElement<AuthorizationRequest>(authReqJws.payload)

                                // Per OID4VP 1.0 §5.6: if wallet sent wallet_nonce, the request object
                                // MUST contain the same value in a wallet_nonce claim.
                                if (walletNonce != null) {
                                    val receivedWalletNonce = authReqJws.payload["wallet_nonce"]?.jsonPrimitive?.contentOrNull
                                    require(receivedWalletNonce == walletNonce) {
                                        "wallet_nonce mismatch: sent '$walletNonce' but request object contains '$receivedWalletNonce'. " +
                                                "Possible replay attack — terminating request processing."
                                    }
                                    log.trace { "wallet_nonce validated successfully" }
                                }

                                decodedRequest
                            }
                        }
                    }
                }

                authorizationRequestContentType.match(ContentType.Application.Json) -> {
                    runCatching { httpResponse.body<AuthorizationRequest>() }.recover {
                        throw IllegalArgumentException("Error parsing AuthorizationRequest retrieved from: $presentationRequestUrl")
                    }.getOrThrow()
                }

                else -> throw IllegalArgumentException("Invalid ContentType \"$authorizationRequestContentType\" for AuthorizationRequest retrieved from \"$presentationRequestUrl\", content is: ${runCatching { httpResponse.bodyAsText() }.getOrElse { "(could not read http response body)" }}")
            }
            retrievedAuthorizationRequest
        } else {
            val parsedParameters = JsonObject(presentationRequestUrl.parameters.flattenEntries().associate { (k, v) ->
                k to Json.parseToJsonElement(v)
            })
            Json.decodeFromJsonElement<AuthorizationRequest>(parsedParameters)
        }

        log.trace { "Wallet will try to present to AuthorizationRequest: $authorizationRequest" }

        // Validate transaction_data - throw exception instead of calling walletRejectHandling
        // because for validation failures we should NOT call the verifier's response_uri at all.
        // The conformance tests expect the wallet to reject without making any network calls.
        validateRequestTransactionData(
            transactionData = authorizationRequest.transactionData,
            typeRegistry = transactionDataTypeRegistry,
            credentialQueriesById = authorizationRequest.dcqlQuery?.credentials?.associateBy { it.id },
        )

        // OID4VP 1.0 §5.5: redirect_uri and response_uri are mutually exclusive.
        // When response_mode is direct_post or direct_post.jwt, only response_uri should be present.
        // Having both is an invalid request.
        // NOTE: We throw IllegalArgumentException instead of calling walletRejectHandling because
        // for these validation failures we should NOT call the verifier's response_uri at all.
        // The wallet should reject the request without making any network calls to the verifier.
        if (authorizationRequest.redirectUri != null && authorizationRequest.responseUri != null) {
            log.warn { "Invalid request: both redirect_uri and response_uri are present (mutually exclusive per OID4VP §5.5)" }
            throw IllegalArgumentException(
                "redirect_uri and response_uri are mutually exclusive; both cannot be present in the same request"
            )
        }

        // OID4VP 1.0 §5.5: When using direct_post or direct_post.jwt response modes,
        // redirect_uri MUST NOT be present. These modes use response_uri exclusively.
        // We throw instead of calling walletRejectHandling to avoid POSTing to response_uri.
        val responseMode = authorizationRequest.responseMode
        if (responseMode in listOf(OpenID4VPResponseMode.DIRECT_POST, OpenID4VPResponseMode.DIRECT_POST_JWT)
            && authorizationRequest.redirectUri != null) {
            log.warn { "Invalid request: redirect_uri present with response_mode=$responseMode (OID4VP §5.5)" }
            throw IllegalArgumentException(
                "redirect_uri must not be present when response_mode is $responseMode; use response_uri instead"
            )
        }

        require(
            authorizationRequest.responseType == OpenID4VPResponseType.VP_TOKEN ||
            authorizationRequest.responseType == OpenID4VPResponseType.VP_TOKEN_ID_TOKEN
        ) {
            // Other response types (e.g. "code") are not supported in this wallet implementation.
            "Unsupported response_type '${authorizationRequest.responseType}': " +
                "only 'vp_token' and 'vp_token id_token' are supported."
        }

        val isSiopv2 = authorizationRequest.responseType == OpenID4VPResponseType.VP_TOKEN_ID_TOKEN

        // Build VP Token response
        val credentials = selectCredentialsForQuery(
            authorizationRequest.dcqlQuery ?: throw IllegalArgumentException("Missing dcql_query for AuthorizationRequest"),
        )
        log.trace { "Auto-selected credential count: ${credentials.mapValues { it.value.count() }}" }
        log.trace { "Auto-selected credentials for query: $credentials" }



        if (holderPoliciesToRun != null) {
            // transaction_data checks are intentionally not implemented as HolderPolicy checks:
            // HolderPolicyEngine receives credentials only and has no authorization-request context.
            // TODO: ----------------- Handle disclosures from DcqlMatchResult

            val relevantHolderPolicies = holderPoliciesToRun
                .filter { it.direction == null || it.direction == HolderPolicy.HolderPolicyDirection.PRESENT }
            val credentialsToEvaluate = credentials.values.flatMap { matchResults ->
                matchResults.map { matchResult ->
                    // TODO: handle it.selectedDisclosures
                    (matchResult.credential as RawDcqlCredential).originalCredential as DigitalCredential
                }
            }

            val evalResult = HolderPolicyEngine.evaluate(relevantHolderPolicies, credentialsToEvaluate.asFlow())
            when {
                runPolicies == null && evalResult == null -> {
                    // ok
                }

                runPolicies == true -> {
                    if (evalResult == HolderPolicy.HolderPolicyAction.BLOCK) {
                        throw IllegalArgumentException("Presentation execution was blocked by Holder Policy.")
                    }
                    if (evalResult == null) {
                        throw IllegalArgumentException("Presentation execution was not allowed by any Holder Policy.")
                    }
                }
            }

            //-----
        }


        val vpToken = generateVpTokenForRequest(authorizationRequest, credentials, holderKey, holderDid, transactionDataTypeRegistry)

        // For vp_token id_token (SIOPv2 combined flow per OID4VP §"Combining with SIOPv2"),
        // generate a Self-Issued ID Token alongside the VP Token.
        val idToken: String? = if (isSiopv2) {
            log.trace { "Generating Self-Issued ID Token for vp_token id_token response type" }
            SelfIssuedIdTokenBuilder.build(authorizationRequest, holderKey, holderDid)
        } else null

        // Send AuthorizationResponse:
        if (authorizationRequest.responseMode == null) {
            require(authorizationRequest.responseType != null) { "Missing response_type" }
            val rt = authorizationRequest.responseType!!.responseType
            if ("vp_token" in rt && "code" !in rt) {
                authorizationRequest.responseMode = OpenID4VPResponseMode.FRAGMENT
            } else if ("code" in rt) {
                authorizationRequest.responseMode = OpenID4VPResponseMode.QUERY
            }
        }

        log.trace { "- Wallet will now present (send) AuthorizationResponse (with mode ${authorizationRequest.responseMode}) -" }

        when (authorizationRequest.responseMode) {
            OpenID4VPResponseMode.FRAGMENT -> {
                // Construct URL with #vp_token=...&state=... and trigger browser redirect

                require(authorizationRequest.redirectUri != null) {
                    "Invalid AuthorizationRequest: 'redirect_uri' is required for response_mode 'fragment'."
                }

                // Build the parameters that will go into the URL fragment.
                val fragmentParameters = ParametersBuilder().apply {
                    append("vp_token", vpToken)
                    idToken?.let { append("id_token", it) }
                    authorizationRequest.state?.let { append("state", it) }
                }.build()

                // Create the final redirect URL.
                // e.g., https://verifier.com/callback#vp_token=...&state=...
                val redirectUrl = "${authorizationRequest.redirectUri}#${fragmentParameters.formUrlEncode()}"

                log.trace { "Responding with fragment redirect to: $redirectUrl" }

                // We return the URL for the client to handle the redirect.
                return Result.success(
                    WalletPresentResult(
                        getUrl = redirectUrl
                    )
                )
            }

            OpenID4VPResponseMode.QUERY -> {
                // This mode requires a redirect_uri to send the user back to.
                require(authorizationRequest.redirectUri != null) {
                    "Invalid AuthorizationRequest: 'redirect_uri' is required for response_mode 'query'."
                }

                // Build the parameters that will go into the URL query string.
                val queryParameters = ParametersBuilder().apply {
                    append("vp_token", vpToken)
                    idToken?.let { append("id_token", it) }
                    authorizationRequest.state?.let { append("state", it) }
                }.build()

                // Create the final redirect URL.
                // Note the use of '?' instead of '#'.
                // e.g., https://verifier.com/callback?vp_token=...&state=...
                val redirectUrl = URLBuilder(authorizationRequest.redirectUri!!).apply {
                    // Ktor's URLBuilder handles adding '?' or '&' correctly,
                    // even if the original redirectUri already has query parameters.
                    parameters.appendAll(queryParameters)
                }.buildString()

                log.trace { "Responding with query redirect to: $redirectUrl" }

                // We return the URL for the client to handle the redirect.
                return Result.success(
                    WalletPresentResult(
                        getUrl = redirectUrl
                    )
                )
            }

            OpenID4VPResponseMode.FORM_POST -> {
                // This mode also requires a redirect_uri to POST the form to.
                val redirectUri = authorizationRequest.redirectUri
                requireNotNull(redirectUri) { "Invalid AuthorizationRequest: 'redirect_uri' is required for response_mode 'form_post'." }

                val fields = buildList {
                    add("vp_token" to vpToken)
                    idToken?.let { add("id_token" to it) }
                    authorizationRequest.state?.let { add("state" to it) }
                }
                val htmlContent = buildFormPostHtml(
                    actionUrl = redirectUri,
                    title = "Submitting Presentation...",
                    fields = fields,
                )

                log.trace { "Responding with self-submitting HTML form to post to: $redirectUri" }

                // For a pure API, we return the HTML for the client to render in a WebView.
                return Result.success(
                    WalletPresentResult(
                        formPostHtml = htmlContent
                    )
                )
            }

            // authorizationRequest.responseUri
            OpenID4VPResponseMode.DIRECT_POST -> {
                val responseUri = authorizationRequest.responseUri
                requireNotNull(responseUri) { "Invalid AuthorizationRequest: 'response_uri' is required for response_mode 'direct_post'." }
                val parameters = ParametersBuilder().apply {
                    append("vp_token", vpToken)
                    idToken?.let { append("id_token", it) }
                    if (authorizationRequest.state != null) {
                        append("state", authorizationRequest.state!!)
                    }
                }.build()

                log.trace { "Submitting direct_post form to Verifier: $responseUri" }
                return Result.success(postFormResponse(responseUri, parameters))
            }

            OpenID4VPResponseMode.DIRECT_POST_JWT -> {
                // Encrypt the vp_token and state into a JWE, then POST response=<JWE_string>.
                val responseUri = authorizationRequest.responseUri
                requireNotNull(responseUri) { "Invalid AuthorizationRequest: 'response_uri' is required for response_mode 'direct_post.jwt'." }

                // Extract encryption configuration using ResponseEncryptionHandler
                val encryptionConfig = ResponseEncryptionHandler.extractEncryptionConfig(authorizationRequest)
                    .getOrElse { error ->
                        return walletRejectHandling(
                            authorizationRequest,
                            OID4VPErrorCode.INVALID_ENCRYPTION_PARAMETERS,
                            "Encryption configuration error: ${error.message}"
                        )
                    }
                    ?: return walletRejectHandling(
                        authorizationRequest,
                        OID4VPErrorCode.ENCRYPTION_REQUIRED,
                        "Encryption required for direct_post.jwt but no encryption config available"
                    )

                // Construct Payload
                val vpTokenElement = Json.parseToJsonElement(vpToken)
                val payloadJson = buildJsonObject {
                    put("vp_token", vpTokenElement)
                    idToken?.let { put("id_token", it) }
                    authorizationRequest.state?.let { put("state", JsonPrimitive(it)) }
                }

                // Encrypt using ResponseEncryptionHandler
                val jweString = runCatching {
                    ResponseEncryptionHandler.encryptResponse(payloadJson, encryptionConfig)
                }.getOrElse { error ->
                    return walletRejectHandling(
                        authorizationRequest,
                        OID4VPErrorCode.ENCRYPTION_FAILURE,
                        "Failed to encrypt authorization response: ${error.message}"
                    )
                }

                // Send Response
                val parameters = ParametersBuilder().apply {
                    append("response", jweString)
                }.build()

                log.trace { "Submitting direct_post.jwt (encrypted) to Verifier: $responseUri" }
                return Result.success(postFormResponse(responseUri, parameters))
            }

            // DC API
            OpenID4VPResponseMode.DC_API -> TODO("DC API is not yet supported")
            OpenID4VPResponseMode.DC_API_JWT -> TODO("DC API is not yet supported")
            null -> throw IllegalArgumentException("Missing response mode from AuthorizationRequest")
        }
    }

    /**
     * Creates a Key Binding JWT for SD-JWT presentations.
     *
     * Per OID4VP 1.0 §5.5.1, when the authorization request includes `transaction_data`,
     * the wallet MUST include `transaction_data_hashes` in the KB-JWT. Each entry is the
     * base64url-encoded SHA-256 hash of the corresponding base64url-encoded transaction data
     * item as it appeared in the request. The algorithm is SHA-256 by default.
     */
    internal suspend fun createKeyBindingJwt(
        disclosed: String,
        nonce: String,
        audience: String?,
        selectedDisclosures: List<SdJwtSelectiveDisclosure>,
        holderKey: Key,
        transactionData: List<String>? = null,
    ): String {
        selectedDisclosures.map { it.asEncoded() }
        log.trace { "Creating KB+JWT for disclosures: $selectedDisclosures" }
        // Per RFC 9901 §4.3.1, sd_hash is computed over:
        // <Issuer-signed JWT>~<Disclosure 1>~...~<Disclosure N>~
        // The trailing ~ is always required, even when there are no disclosures.
        // disclose() returns "issuer_jwt~disc1~disc2" (no trailing ~) for non-empty disclosures,
        // and "issuer_jwt~" (trailing ~) for zero disclosures — so we only append ~ when needed.
        val stringToHash = if (selectedDisclosures.isNotEmpty()) {
            "$disclosed~"
        } else {
            disclosed // disclose() already produces "issuer_jwt~" for zero disclosures
        }

        log.trace { "Wallet presentation: Calculating hash for SD-JWT kb from: $stringToHash" }
        val sdHash = calculateSha256Base64Url(stringToHash)
        val decodedTransactionData = decodeList(transactionData.orEmpty())
        val transactionDataHashAlgorithm = resolveHashAlgorithm(decodedTransactionData)
        val transactionDataHashes = transactionDataHashAlgorithm?.let {
            calculateTransactionDataHashes(
                transactionData = transactionData.orEmpty(),
                algorithm = it,
            )
        }

        val jwsHeaders = buildJsonObject {
            // alg is REQUIRED in the KB-JWT JOSE header per RFC 9901 §4.3
            put("alg", JsonPrimitive(holderKey.keyType.jwsAlg))
            put("typ", JsonPrimitive("kb+jwt"))
        }

        val kbJwtPayload = buildJsonObject {
            put("aud", JsonPrimitive(audience))
            put("nonce", JsonPrimitive(nonce))
            put("iat", JsonPrimitive(Clock.System.now().epochSeconds))
            put("sd_hash", JsonPrimitive(sdHash)) // binding to the selected disclosures
            transactionDataHashes?.takeIf { it.isNotEmpty() }?.let { hashes ->
                put(
                    "transaction_data_hashes",
                    buildJsonArray { hashes.forEach { add(JsonPrimitive(it)) } },
                )
                if (decodedTransactionData.any { !it.transactionData.transactionDataHashesAlg.isNullOrEmpty() }) {
                    put(
                        "transaction_data_hashes_alg",
                        JsonPrimitive(transactionDataHashAlgorithm),
                    )
                }
            }
        }
        return holderKey.signJws(plaintext = kbJwtPayload.toString().encodeToByteArray(), headers = jwsHeaders)
    }

}
