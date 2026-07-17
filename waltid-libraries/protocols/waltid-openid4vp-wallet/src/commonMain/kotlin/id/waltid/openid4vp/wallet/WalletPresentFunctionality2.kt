@file:OptIn(ExperimentalSerializationApi::class)

package id.waltid.openid4vp.wallet

import id.walt.credentials.formats.DigitalCredential
import id.walt.credentials.signatures.sdjwt.SdJwtSelectiveDisclosure
import id.walt.crypto.keys.Key
import id.walt.crypto.utils.ShaUtils.calculateSha256Base64Url
import id.walt.dcql.DcqlMatcher
import id.walt.dcql.RawDcqlCredential
import id.walt.dcql.models.CredentialFormat
import id.walt.dcql.models.DcqlQuery
import id.walt.holderpolicies.HolderPolicy
import id.walt.holderpolicies.HolderPolicyEngine
import id.walt.openid4vp.clientidprefix.ClientIdError
import id.walt.verifier.openid.models.authorization.AuthorizationRequest
import id.walt.verifier.openid.models.openid.OpenID4VPResponseMode
import id.walt.verifier.openid.models.openid.OpenID4VPResponseType
import id.walt.verifier.openid.transactiondata.*
import id.walt.webdatafetching.WebDataFetcher
import id.walt.webdatafetching.WebDataFetcherId
import id.waltid.openid4vp.wallet.WalletPresentFunctionality2.buildIdToken
import id.waltid.openid4vp.wallet.WalletPresentFunctionality2.buildVpToken
import id.waltid.openid4vp.wallet.WalletPresentFunctionality2.resolveAuthorizationRequest
import id.waltid.openid4vp.wallet.WalletPresentFunctionality2.sendAuthorizationResponse
import id.waltid.openid4vp.wallet.WalletPresentFunctionality2.walletPresentHandling
import id.waltid.openid4vp.wallet.WalletPresentFunctionality2.walletRejectHandling
import id.waltid.openid4vp.wallet.presentation.*
import id.waltid.openid4vp.wallet.request.AuthorizationRequestResolver
import id.waltid.openid4vp.wallet.response.ResponseEncryption
import id.waltid.openid4vp.wallet.request.ResolvedAuthorizationRequest
import io.github.oshai.kotlinlogging.KotlinLogging
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


object WalletPresentFunctionality2 {

    private val log = KotlinLogging.logger { }

    private val webResolveAuthReq = WebDataFetcher(WebDataFetcherId.OPENID4VP_WALLET_RESOLVE_AUTHORIZATIONREQUEST)
    private val webPostToken = WebDataFetcher(WebDataFetcherId.OPENID4VP_WALLET_POST_TOKEN)

    /**
     * @param matchedData: Credentials that were choosen by the DCQL query
     */
    internal suspend fun generateVpTokenForRequest(
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

    // ---------------------------------------------------------------------------
    // Step-by-step presentation API
    //
    // The full flow is: resolveAuthorizationRequest -> (user selects credentials) ->
    // buildVpToken -> sendAuthorizationResponse.
    //
    // Each step can be called independently so wallet UIs can interpose user-consent
    // screens between steps. The combined [walletPresentHandling] function calls
    // these steps internally and remains the preferred path for automated wallets.
    // ---------------------------------------------------------------------------

    /**
     * Step 1 - Resolve and verify the OpenID4VP authorization request.
     *
     * Handles all request_uri transport cases including request_uri_method=post
     * (wallet_nonce), signed JWT request objects (client ID prefix verification),
     * and inline URL-encoded parameters.
     *
     * @param presentationRequestUrl The openid4vp:// or https:// URL containing or
     *   referencing the authorization request.
     * @param unsignedRequestObjectPolicy Whether to accept unsigned (alg=none) JWTs.
     *   Defaults to [AuthorizationRequestResolver.UnsignedRequestObjectPolicy.REQUIRE_SIGNED].
     * @param legacyFallbackCallback Optional legacy fallback for pre-registered client IDs.
     * @return The resolved and verified [AuthorizationRequest].
     * @throws IllegalArgumentException if the request cannot be resolved or verified.
     */
    suspend fun resolveAuthorizationRequest(
        presentationRequestUrl: Url,
        unsignedRequestObjectPolicy: AuthorizationRequestResolver.UnsignedRequestObjectPolicy =
            AuthorizationRequestResolver.UnsignedRequestObjectPolicy.REQUIRE_SIGNED,
        legacyFallbackCallback: (suspend (Url) -> Result<JsonElement>)? = null,
    ): AuthorizationRequest {
        return try {
            AuthorizationRequestResolver.resolve(
                requestUrl = presentationRequestUrl,
                unsignedRequestObjectPolicy = unsignedRequestObjectPolicy,
                fetchRequestUri = { requestUri, requestUriMethod ->
                    AuthorizationRequestResolver.fetchRequestUriWithWebDataFetcher(
                        webResolveAuthReq = webResolveAuthReq,
                        requestUri = requestUri,
                        requestUriMethod = requestUriMethod,
                        // Optional wallet metadata is omitted until the caller explicitly profiles
                        // its values. Some Final-compliant verifier endpoints reject unsupported
                        // capability members, while wallet_nonce remains mandatory for this flow.
                        sendWalletMetadata = false,
                    )
                },
            ).authorizationRequest.also(::validateAuthorizationRequest)
        } catch (error: AuthorizationRequestResolver.SignedAuthorizationRequestValidationException) {
            if (error.clientIdError is ClientIdError.PreRegisteredClientNotFound && legacyFallbackCallback != null) {
                val fallback = legacyFallbackCallback(presentationRequestUrl)
                if (fallback.isSuccess) throw LegacyFallbackException(fallback.getOrThrow())
            }
            throw error
        } catch (error: IllegalArgumentException) {
            if (legacyFallbackCallback != null) {
                val fallback = legacyFallbackCallback(presentationRequestUrl)
                if (fallback.isSuccess) throw LegacyFallbackException(fallback.getOrThrow())
            }
            throw error
        }
    }

    private fun validateAuthorizationRequest(request: AuthorizationRequest) {
        require(!request.clientId.isNullOrBlank()) { "Authorization Request client_id is required" }
        require(!request.nonce.isNullOrBlank()) { "Authorization Request nonce is required" }
        require(request.dcqlQuery != null) {
            if (request.scope != null) {
                "Scope-based DCQL is not configured for this wallet"
            } else {
                "Authorization Request must contain dcql_query"
            }
        }
        if (request.responseMode in OpenID4VPResponseMode.DIRECT_POST_RESPONSES) {
            require(request.redirectUri == null) {
                "redirect_uri must not be present with response_mode=${request.responseMode}"
            }
            require(!request.responseUri.isNullOrBlank()) {
                "response_uri is required with response_mode=${request.responseMode}"
            }
        }
    }

    /**
     * Internal marker exception thrown by [resolveAuthorizationRequest] when the
     * legacy fallback path is taken. Caught by [walletPresentHandling] only.
     */
    internal class LegacyFallbackException(val result: JsonElement) : Exception()

    /**
     * Step 2 - Build the VP token from the matched credentials.
     *
     * Takes the output of the credential-selection step and produces the serialized
     * `vp_token` JSON string ready to include in the authorization response.
     *
     * For SIOPv2 (`vp_token id_token` response type), also build the ID token via
     * [buildIdToken] before calling [sendAuthorizationResponse].
     *
     * @param authorizationRequest The resolved authorization request from [resolveAuthorizationRequest].
     * @param matchedCredentials The DCQL-matched credentials to present, keyed by DCQL query ID.
     * @param holderKey The holder's signing key.
     * @param holderDid The holder's DID, or null for JWK-bound presentations.
     * @param transactionDataTypeRegistry Registry for transaction_data type handlers.
     * @return The serialized `vp_token` JSON string.
     */
    suspend fun buildVpToken(
        authorizationRequest: AuthorizationRequest,
        matchedCredentials: Map<String, List<DcqlMatcher.DcqlMatchResult>>,
        holderKey: Key,
        holderDid: String?,
        transactionDataTypeRegistry: TransactionDataTypeRegistry = TransactionDataTypeRegistry(),
    ): String = generateVpTokenForRequest(authorizationRequest, matchedCredentials, holderKey, holderDid, transactionDataTypeRegistry)

    /**
     * Build the Self-Issued ID Token for `vp_token id_token` (SIOPv2) response types.
     * Returns null for plain `vp_token` requests.
     *
     * Call this after [buildVpToken] and pass the result to [sendAuthorizationResponse].
     */
    suspend fun buildIdToken(
        authorizationRequest: AuthorizationRequest,
        holderKey: Key,
        holderDid: String?,
    ): String? = if (authorizationRequest.responseType == OpenID4VPResponseType.VP_TOKEN_ID_TOKEN) {
        log.trace { "Generating Self-Issued ID Token for vp_token id_token response type" }
        SelfIssuedIdTokenBuilder.build(authorizationRequest, holderKey, holderDid)
    } else null

    /**
     * Step 3 - Send the authorization response to the verifier.
     *
     * Dispatches the `vp_token` (and optional `id_token`) to the verifier according to
     * the request's `response_mode` (fragment, query, form_post, direct_post, direct_post.jwt).
     *
     * @param authorizationRequest The resolved authorization request from [resolveAuthorizationRequest].
     * @param vpToken The VP token string from [buildVpToken].
     * @param idToken The optional ID token from [buildIdToken]. Pass null for plain vp_token flows.
     * @return A [Result] wrapping a [WalletPresentResult] describing the transmission outcome.
     */
    suspend fun sendAuthorizationResponse(
        authorizationRequest: AuthorizationRequest,
        vpToken: String,
        idToken: String? = null,
    ): Result<WalletPresentResult> = runCatching {
        // Infer response_mode from response_type if not explicitly set
        if (authorizationRequest.responseMode == null) {
            require(authorizationRequest.responseType != null) { "Missing response_type" }
            val rt = authorizationRequest.responseType!!.responseType
            if ("vp_token" in rt && "code" !in rt) {
                authorizationRequest.responseMode = OpenID4VPResponseMode.FRAGMENT
            } else if ("code" in rt) {
                authorizationRequest.responseMode = OpenID4VPResponseMode.QUERY
            }
        }
        log.trace { "Sending AuthorizationResponse (mode=${authorizationRequest.responseMode})" }

        when (authorizationRequest.responseMode) {
            OpenID4VPResponseMode.FRAGMENT -> {
                require(authorizationRequest.redirectUri != null) {
                    "Invalid AuthorizationRequest: 'redirect_uri' is required for response_mode 'fragment'."
                }
                val fragmentParameters = ParametersBuilder().apply {
                    append("vp_token", vpToken)
                    idToken?.let { append("id_token", it) }
                    authorizationRequest.state?.let { append("state", it) }
                }.build()
                WalletPresentResult(getUrl = "${authorizationRequest.redirectUri}#${fragmentParameters.formUrlEncode()}")
            }

            OpenID4VPResponseMode.QUERY -> {
                require(authorizationRequest.redirectUri != null) {
                    "Invalid AuthorizationRequest: 'redirect_uri' is required for response_mode 'query'."
                }
                val queryParameters = ParametersBuilder().apply {
                    append("vp_token", vpToken)
                    idToken?.let { append("id_token", it) }
                    authorizationRequest.state?.let { append("state", it) }
                }.build()
                WalletPresentResult(
                    getUrl = URLBuilder(authorizationRequest.redirectUri!!).apply {
                        parameters.appendAll(queryParameters)
                    }.buildString()
                )
            }

            OpenID4VPResponseMode.FORM_POST -> {
                requireNotNull(authorizationRequest.redirectUri) {
                    "Invalid AuthorizationRequest: 'redirect_uri' is required for response_mode 'form_post'."
                }
                val fields = buildList {
                    add("vp_token" to vpToken)
                    idToken?.let { add("id_token" to it) }
                    authorizationRequest.state?.let { add("state" to it) }
                }
                WalletPresentResult(
                    formPostHtml = buildFormPostHtml(
                        actionUrl = authorizationRequest.redirectUri!!,
                        title = "Submitting Presentation...",
                        fields = fields,
                    )
                )
            }

            OpenID4VPResponseMode.DIRECT_POST -> {
                val responseUri = authorizationRequest.responseUri
                requireNotNull(responseUri) {
                    "Invalid AuthorizationRequest: 'response_uri' is required for response_mode 'direct_post'."
                }
                val parameters = ParametersBuilder().apply {
                    append("vp_token", vpToken)
                    idToken?.let { append("id_token", it) }
                    authorizationRequest.state?.let { append("state", it) }
                }.build()
                postFormResponse(responseUri, parameters)
            }

            OpenID4VPResponseMode.DIRECT_POST_JWT -> {
                val responseUri = authorizationRequest.responseUri
                requireNotNull(responseUri) {
                    "Invalid AuthorizationRequest: 'response_uri' is required for response_mode 'direct_post.jwt'."
                }
                val encryption = requireNotNull(ResponseEncryption.resolve(authorizationRequest))
                val vpTokenElement = Json.parseToJsonElement(vpToken)
                val payloadJson = buildJsonObject {
                    put("vp_token", vpTokenElement)
                    idToken?.let { put("id_token", it) }
                    authorizationRequest.state?.let { put("state", JsonPrimitive(it)) }
                }
                val jweString = encryption.key.encryptJwe(
                    payloadJson.toString().encodeToByteArray(),
                    encryption.encryptionMethod,
                )
                val parameters = ParametersBuilder().apply { append("response", jweString) }.build()
                postFormResponse(responseUri, parameters)
            }

            OpenID4VPResponseMode.DC_API -> TODO("DC API is not yet supported")
            OpenID4VPResponseMode.DC_API_JWT -> TODO("DC API is not yet supported")
            null -> throw IllegalArgumentException("Missing response mode from AuthorizationRequest")
        }
    }

    private suspend fun resolveAuthorizationRequestObject(
        presentationRequestUrl: Url,
        unsignedRequestObjectPolicy: AuthorizationRequestResolver.UnsignedRequestObjectPolicy,
    ): ResolvedAuthorizationRequest =
        AuthorizationRequestResolver.resolve(
            requestUrl = presentationRequestUrl,
            unsignedRequestObjectPolicy = unsignedRequestObjectPolicy,
            fetchRequestUri = { requestUri, requestUriMethod ->
                AuthorizationRequestResolver.fetchRequestUriWithWebDataFetcher(
                    webResolveAuthReq = webResolveAuthReq,
                    requestUri = requestUri,
                    requestUriMethod = requestUriMethod,
                    // Optional wallet metadata is omitted until the caller explicitly profiles
                    // its values. Some Final-compliant verifier endpoints reject unsupported
                    // capability members, while wallet_nonce remains mandatory for this flow.
                    sendWalletMetadata = false,
                )
            },
        )

    private suspend fun legacyFallbackResult(
        presentationRequestUrl: Url,
        legacyFallbackCallback: (suspend (Url) -> Result<JsonElement>)?,
        error: AuthorizationRequestResolver.SignedAuthorizationRequestValidationException,
    ): WalletPresentResult? {
        if (error.clientIdError !is ClientIdError.PreRegisteredClientNotFound || legacyFallbackCallback == null) {
            return null
        }

        val fallbackResponse = runCatching { legacyFallbackCallback(presentationRequestUrl) }
            .getOrNull()
            ?.getOrNull()

        if (fallbackResponse != null) {
            return WalletPresentResult(
                transmissionSuccess = true,
                verifierResponse = fallbackResponse,
            )
        }
        return null
    }

    suspend fun walletPresentHandling(
        holderKey: Key,
        holderDid: String?,
        presentationRequestUrl: Url,

        selectCredentialsForQuery: suspend (DcqlQuery) -> Map<String, List<DcqlMatcher.DcqlMatchResult>>,

        holderPoliciesToRun: Flow<HolderPolicy>?,
        runPolicies: Boolean?,

        transactionDataTypeRegistry: TransactionDataTypeRegistry,

        // TODO: selected credentials

        /**
         * Fallback for ancient legacy tests, wrong integration tests, and various other stuff that should have long been removed
         * Use: `OldWalletPresentFunctionality.oldWalletPresentHandling(walletService, presentationRequestUrl, request)` for this
         */
        legacyFallbackCallback: (suspend (Url) -> Result<JsonElement>)? = null,

        unsignedRequestObjectPolicy: AuthorizationRequestResolver.UnsignedRequestObjectPolicy =
            AuthorizationRequestResolver.UnsignedRequestObjectPolicy.REQUIRE_SIGNED,

        resolvedAuthorizationRequest: ResolvedAuthorizationRequest? = null,
    ): Result<WalletPresentResult> = walletPresentHandling(
        holderKey = holderKey,
        holderDid = holderDid,
        presentationRequestUrl = presentationRequestUrl,
        selectCredentialsForQuery = selectCredentialsForQuery,
        holderPoliciesToRun = holderPoliciesToRun,
        runPolicies = runPolicies,
        transactionDataTypeRegistry = transactionDataTypeRegistry,
        legacyFallbackCallback = legacyFallbackCallback,
        unsignedRequestObjectPolicy = unsignedRequestObjectPolicy,
        resolvedAuthorizationRequest = resolvedAuthorizationRequest,
        beforeCredentialsUsed = {},
    )

    suspend fun walletPresentHandling(
        holderKey: Key,
        holderDid: String?,
        presentationRequestUrl: Url,
        selectCredentialsForQuery: suspend (DcqlQuery) -> Map<String, List<DcqlMatcher.DcqlMatchResult>>,
        holderPoliciesToRun: Flow<HolderPolicy>?,
        runPolicies: Boolean?,
        transactionDataTypeRegistry: TransactionDataTypeRegistry,
        legacyFallbackCallback: (suspend (Url) -> Result<JsonElement>)? = null,
        unsignedRequestObjectPolicy: AuthorizationRequestResolver.UnsignedRequestObjectPolicy =
            AuthorizationRequestResolver.UnsignedRequestObjectPolicy.REQUIRE_SIGNED,
        resolvedAuthorizationRequest: ResolvedAuthorizationRequest? = null,
        beforeCredentialsUsed: suspend (Int) -> Unit,
    ): Result<WalletPresentResult> {
        log.trace { "- Start of Wallet Present Handling -" }
        log.trace { "Wallet presentation will use key $holderKey, and did $holderDid" }

        // Step 1: Resolve AuthorizationRequest.
        val authorizationRequest: AuthorizationRequest = resolvedAuthorizationRequest
            ?.authorizationRequest
            ?: try {
                resolveAuthorizationRequestObject(presentationRequestUrl, unsignedRequestObjectPolicy)
                    .authorizationRequest
                    .also(::validateAuthorizationRequest)
            } catch (error: AuthorizationRequestResolver.SignedAuthorizationRequestValidationException) {
                legacyFallbackResult(presentationRequestUrl, legacyFallbackCallback, error)
                    ?.let { return Result.success(it) }
                throw error
            } catch (error: IllegalArgumentException) {
                val fallbackResponse = runCatching { legacyFallbackCallback?.invoke(presentationRequestUrl) }
                    .getOrNull()
                    ?.getOrNull()
                if (fallbackResponse != null) {
                    return Result.success(
                        WalletPresentResult(
                            transmissionSuccess = true,
                            verifierResponse = fallbackResponse,
                        )
                    )
                }
                throw error
            }

        log.trace { "Wallet will try to present to AuthorizationRequest: $authorizationRequest" }

        runCatching {
            validateRequestTransactionData(
                transactionData = authorizationRequest.transactionData,
                typeRegistry = transactionDataTypeRegistry,
                credentialQueriesById = authorizationRequest.dcqlQuery?.credentials?.associateBy { it.id },
            )
        }.onFailure { error ->
            return walletRejectHandling(authorizationRequest, OID4VPErrorCode.INVALID_TRANSACTION_DATA, error.message)
        }
        require(
            authorizationRequest.responseType == OpenID4VPResponseType.VP_TOKEN ||
                    authorizationRequest.responseType == OpenID4VPResponseType.VP_TOKEN_ID_TOKEN
        ) {
            "Unsupported response_type '${authorizationRequest.responseType}': " +
                    "only 'vp_token' and 'vp_token id_token' are supported."
        }

        // Step 2: Select credentials via the caller-supplied lambda.
        val credentials = selectCredentialsForQuery(
            authorizationRequest.dcqlQuery ?: throw IllegalArgumentException("Missing dcql_query for AuthorizationRequest"),
        )
        log.trace { "Auto-selected credential count: ${credentials.mapValues { it.value.count() }}" }

        // Apply holder policies.
        if (holderPoliciesToRun != null) {
            // transaction_data checks are intentionally not implemented as HolderPolicy checks:
            // HolderPolicyEngine receives credentials only and has no authorization-request context.
            // TODO: handle disclosures from DcqlMatchResult
            val relevantHolderPolicies = holderPoliciesToRun
                .filter { it.direction == null || it.direction == HolderPolicy.HolderPolicyDirection.PRESENT }
            val credentialsToEvaluate = credentials.values.flatMap { matchResults ->
                matchResults.map { matchResult ->
                    // TODO: handle matchResult.selectedDisclosures
                    (matchResult.credential as RawDcqlCredential).originalCredential as DigitalCredential
                }
            }
            val evalResult = HolderPolicyEngine.evaluate(relevantHolderPolicies, credentialsToEvaluate.asFlow())
            when {
                runPolicies == null && evalResult == null -> { /* ok */
                }

                runPolicies == true -> {
                    if (evalResult == HolderPolicy.HolderPolicyAction.BLOCK)
                        throw IllegalArgumentException("Presentation execution was blocked by Holder Policy.")
                    if (evalResult == null)
                        throw IllegalArgumentException("Presentation execution was not allowed by any Holder Policy.")
                }
            }
        }

        val credentialCount = distinctCredentialCount(credentials)
        if (credentialCount > 0) beforeCredentialsUsed(credentialCount)

        // Step 3: Build VP token (and optional ID token for SIOPv2).
        val vpToken = buildVpToken(authorizationRequest, credentials, holderKey, holderDid, transactionDataTypeRegistry)
        val idToken = buildIdToken(authorizationRequest, holderKey, holderDid)

        // Step 4: Send response.
        return sendAuthorizationResponse(authorizationRequest, vpToken, idToken)
    }

    internal fun distinctCredentialCount(
        credentials: Map<String, List<DcqlMatcher.DcqlMatchResult>>,
    ): Int = credentials.values.flatten().distinctBy { it.credential.id }.size

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
