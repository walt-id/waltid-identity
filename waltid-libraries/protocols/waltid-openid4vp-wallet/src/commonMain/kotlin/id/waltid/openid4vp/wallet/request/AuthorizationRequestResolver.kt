@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.waltid.openid4vp.wallet.request

import id.walt.credentials.utils.JwtUtils.isJwt
import id.walt.crypto.utils.UuidUtils
import id.walt.crypto.utils.JwsUtils.decodeJws
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.openid4vp.clientidprefix.ClientIdError
import id.walt.openid4vp.clientidprefix.ClientIdTrustConfiguration
import id.walt.openid4vp.clientidprefix.ClientIdPrefix
import id.walt.openid4vp.clientidprefix.ClientIdPrefixAuthenticator
import id.walt.openid4vp.clientidprefix.ClientIdPrefixParser
import id.walt.openid4vp.clientidprefix.ClientValidationResult
import id.walt.openid4vp.clientidprefix.RequestContext
import id.walt.openid4vp.clientidprefix.prefixes.RedirectUri
import id.walt.x509.platformSupportsPkixCertificatePathValidation
import id.walt.verifier.openid.models.authorization.AuthorizationRequest
import id.walt.verifier.openid.models.authorization.ClientMetadata
import id.walt.verifier.openid.models.authorization.RequestUriHttpMethod
import id.walt.verifier.openid.models.openid.OpenID4VPResponseMode
import id.walt.verifier.openid.models.openid.OpenID4VPResponseType
import id.walt.webdatafetching.WebDataFetcher
import id.waltid.openid4vp.wallet.WalletPresentationFormatRegistry
import io.github.oshai.kotlinlogging.KotlinLogging
import id.waltid.openid4vp.wallet.walletResponseMode
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlin.time.Clock

object AuthorizationRequestResolver {
    private val log = KotlinLogging.logger { }
    private const val REQUEST_OBJECT_TYPE = "oauth-authz-req+jwt"
    const val DEFAULT_REQUEST_OBJECT_AUDIENCE = "https://self-issued.me/v2"
    private const val CLOCK_SKEW_SECONDS = 60L

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        isLenient = true
    }

    class SignedAuthorizationRequestValidationException(
        val clientIdError: ClientIdError,
    ) : IllegalArgumentException(
        "Could not verify signed AuthorizationRequest with client id prefix: ${clientIdError::class.simpleName} - ${clientIdError.message}",
    )

    data class RequestUriFetchResponse(
        val status: HttpStatusCode,
        val contentType: ContentType?,
        val body: String,
        val walletNonce: String? = null,
    )

    /**
     * Policy for unsigned OpenID4VP Authorization Requests (JSON from `request_uri`, query
     * parameters, or `alg: none` JWTs).
     *
     * [ALLOW_UNSIGNED] is the default: signed Request Objects with a signable client ID prefix
     * (`x509_san_dns`, `x509_hash`, DID, attestation, pre-registered) are accepted, and unsigned
     * JSON / `redirect_uri` requests fetched via `request_uri` are accepted. [REQUIRE_SIGNED]
     * rejects unsigned encodings and the `redirect_uri` prefix; reserved for a future HAIP profile.
     */
    enum class UnsignedRequestObjectPolicy {
        ALLOW_UNSIGNED,
        REQUIRE_SIGNED,
    }

    class UnsignedAuthorizationRequestNotAllowedException(
        message: String = "Unsigned Authorization Request is not allowed for this client identifier",
    ) : IllegalArgumentException(message)

    private object RequestUriPostWalletMetadata {
        val default: String by lazy {
            build(WalletPresentationFormatRegistry.buildVpFormatsSupported())
        }

        private val unsupportedResponseTypes = setOf(
            // Authorization Code flow requires a wallet token endpoint; this flow only submits VP token responses.
            OpenID4VPResponseType.CODE,
        )

        private val responseTypesSupported = (OpenID4VPResponseType.entries - unsupportedResponseTypes)
            .map { it.responseType }

        // DC API response modes are protocol values, but this wallet flow does not submit through DC API yet.
        private val unsupportedResponseModes = OpenID4VPResponseMode.DC_API_RESPONSES

        private val responseModesSupported = (OpenID4VPResponseMode.entries - unsupportedResponseModes)
            .map { json.encodeToJsonElement(OpenID4VPResponseMode.serializer(), it).jsonPrimitive.content }

        private val unsupportedClientIdPrefixes = setOf(
            // OpenID Federation parsing exists, but trust chain resolution is not implemented yet.
            ClientIdPrefix.OPENID_FEDERATION,
        )

        // Ed448 is modelled by the JOSE layer but is not supported by the wallet's platform crypto providers.
        private val requestObjectSigningAlgorithmsSupported = JwsAlgorithm.entries
            .filterNot { it == JwsAlgorithm.ED448 }
            .map { it.identifier }

        fun build(
            vpFormatsSupported: JsonObject,
            trustConfiguration: ClientIdTrustConfiguration = ClientIdTrustConfiguration(),
            unsignedRequestObjectPolicy: UnsignedRequestObjectPolicy = UnsignedRequestObjectPolicy.ALLOW_UNSIGNED,
        ): String = json.encodeToString(
            serializer = JsonObject.serializer(),
            value = buildJsonObject {
                put("response_types_supported", responseTypesSupported.toJsonArray())
                put("response_modes_supported", responseModesSupported.toJsonArray())
                val unsupported = unsupportedClientIdPrefixes + buildSet {
                    if (trustConfiguration.preRegisteredClients.isEmpty()) {
                        add(ClientIdPrefix.PRE_REGISTERED)
                    }
                    if (unsignedRequestObjectPolicy == UnsignedRequestObjectPolicy.REQUIRE_SIGNED) {
                        add(ClientIdPrefix.REDIRECT_URI)
                    }
                    if (trustConfiguration.x509TrustAnchors == null || !platformSupportsPkixCertificatePathValidation) {
                        add(ClientIdPrefix.X509_SAN_DNS)
                        add(ClientIdPrefix.X509_HASH)
                    }
                    if (trustConfiguration.trustedVerifierAttestationIssuers.isEmpty()) {
                        add(ClientIdPrefix.VERIFIER_ATTESTATION)
                    }
                }
                val supportedClientIdPrefixes = ClientIdPrefix.entries - unsupported
                put("client_id_prefixes_supported", supportedClientIdPrefixes.map { it.value }.toJsonArray())
                if (supportedClientIdPrefixes.any { it != ClientIdPrefix.REDIRECT_URI }) {
                    put("request_object_signing_alg_values_supported", requestObjectSigningAlgorithmsSupported.toJsonArray())
                }
                put("vp_formats_supported", vpFormatsSupported)
                put("authorization_encryption_alg_values_supported", JsonArray(listOf(JsonPrimitive("ECDH-ES"))))
                put(
                    "authorization_encryption_enc_values_supported",
                    JsonArray(listOf(JsonPrimitive("A128GCM"), JsonPrimitive("A256GCM"))),
                )
            },
        )

        private fun Iterable<String>.toJsonArray(): JsonArray =
            JsonArray(map(::JsonPrimitive))
    }

    val defaultRequestUriPostWalletMetadata: String
        get() = RequestUriPostWalletMetadata.default

    fun buildRequestUriPostWalletMetadata(
        vpFormatsSupported: JsonObject,
        trustConfiguration: ClientIdTrustConfiguration = ClientIdTrustConfiguration(),
        unsignedRequestObjectPolicy: UnsignedRequestObjectPolicy = UnsignedRequestObjectPolicy.ALLOW_UNSIGNED,
    ): String = RequestUriPostWalletMetadata.build(
        vpFormatsSupported,
        trustConfiguration,
        unsignedRequestObjectPolicy,
    )

    /**
     * Shared transport mapping for retrieving Authorization Requests via `request_uri`.
     * Keeps GET/POST behavior and response conversion centralized for all wallet callers.
     */
    suspend fun fetchRequestUriWithWebDataFetcher(
        webResolveAuthReq: WebDataFetcher,
        requestUri: String,
        requestUriMethod: RequestUriHttpMethod?,
        requestUriPostWalletMetadata: String? = null,
        sendWalletMetadata: Boolean = true,
    ): RequestUriFetchResponse {
        val walletNonce = requestUriMethod
            .takeIf { it == RequestUriHttpMethod.POST }
            ?.let { UuidUtils.randomUUIDString().replace("-", "") }

        val response = when (requestUriMethod) {
            null, RequestUriHttpMethod.GET -> webResolveAuthReq.rawFetch(requestUri)
            RequestUriHttpMethod.POST -> webResolveAuthReq.rawFetch(Url(requestUri)) {
                method = HttpMethod.Post
                contentType(ContentType.Application.FormUrlEncoded)
                accept(ContentType.parse("application/oauth-authz-req+jwt"))
                setBody(
                    buildRequestUriPostBody(
                        walletNonce = requireNotNull(walletNonce),
                        walletMetadata = requestUriPostWalletMetadata ?: defaultRequestUriPostWalletMetadata,
                        sendWalletMetadata = sendWalletMetadata,
                    )
                )
            }
        }

        return RequestUriFetchResponse(
            status = response.status,
            contentType = response.contentType(),
            body = response.bodyAsText(),
            walletNonce = walletNonce,
        )
    }

    internal fun buildRequestUriPostBody(
        walletNonce: String,
        walletMetadata: String,
        sendWalletMetadata: Boolean,
    ): String = Parameters.build {
        if (sendWalletMetadata) append("wallet_metadata", walletMetadata)
        append("wallet_nonce", walletNonce)
    }.formUrlEncode()

    suspend fun resolve(
        requestUrl: Url,
        unsignedRequestObjectPolicy: UnsignedRequestObjectPolicy = UnsignedRequestObjectPolicy.ALLOW_UNSIGNED,
        enforceFinalRequestObject: Boolean = true,
        fetchRequestUri: suspend (requestUri: String, requestUriMethod: RequestUriHttpMethod?) -> RequestUriFetchResponse,
    ): ResolvedAuthorizationRequest = resolve(
        requestUrl = requestUrl,
        unsignedRequestObjectPolicy = unsignedRequestObjectPolicy,
        enforceFinalRequestObject = enforceFinalRequestObject,
        fetchRequestUri = fetchRequestUri,
        trustConfiguration = ClientIdTrustConfiguration(),
    )

    suspend fun resolve(
        requestUrl: Url,
        unsignedRequestObjectPolicy: UnsignedRequestObjectPolicy = UnsignedRequestObjectPolicy.ALLOW_UNSIGNED,
        enforceFinalRequestObject: Boolean = true,
        fetchRequestUri: suspend (requestUri: String, requestUriMethod: RequestUriHttpMethod?) -> RequestUriFetchResponse,
        trustConfiguration: ClientIdTrustConfiguration,
        expectedRequestObjectAudience: String = DEFAULT_REQUEST_OBJECT_AUDIENCE,
    ): ResolvedAuthorizationRequest {
        val parameters = authorizationRequestParameters(requestUrl)
        val requestUri = parameters["request_uri"]
        val requestObject = parameters["request"]
        if (enforceFinalRequestObject) {
            require(requestUri == null || requestObject == null) {
                "Authorization Request must not contain both request and request_uri"
            }
            require(requestUri != null || parameters["request_uri_method"] == null) {
                "request_uri_method must not be present without request_uri"
            }
        }
        if (requestUri != null) {
            return resolveFromRequestUri(
                requestUri = requestUri,
                requestUriMethod = parameters["request_uri_method"],
                outerClientId = parameters["client_id"],
                enforceFinalRequestObject = enforceFinalRequestObject,
                unsignedRequestObjectPolicy = unsignedRequestObjectPolicy,
                fetchRequestUri = fetchRequestUri,
                trustConfiguration = trustConfiguration,
                expectedRequestObjectAudience = expectedRequestObjectAudience,
            )
        }

        if (requestObject != null) return resolveFromRequestObject(
            requestObject = requestObject,
            outerClientId = parameters["client_id"],
            enforceFinalRequestObject = enforceFinalRequestObject,
            unsignedRequestObjectPolicy = unsignedRequestObjectPolicy,
            trustConfiguration = trustConfiguration,
            expectedRequestObjectAudience = expectedRequestObjectAudience,
            requireOuterClientId = true,
        )

        return ResolvedAuthorizationRequest.Plain(
            if (enforceFinalRequestObject) {
                requireUnsignedRequestObjectAllowed(
                    clientId = parameters["client_id"],
                    policy = unsignedRequestObjectPolicy,
                )
                parsePlainRequest(parameters)
            } else parseParametersLegacy(parameters),
        )
    }

    /**
     * Query parameters of an Authorization Request, decoding `+` as a literal plus sign.
     *
     * [Url.parameters] applies `application/x-www-form-urlencoded` rules, where `+` denotes a space.
     * That reading is defensible - OAuth 2.0 Section 3.1 does say the endpoint query is built that way,
     * which would oblige a Verifier to send `%2B` - but RFC 3986 Section 3.4 equally allows a bare `+`
     * in a query component, where it means itself, and that is what implementations send.
     *
     * The conflict is not academic: several OpenID4VP Credential Format Identifiers contain a `+`
     * (`dc+sd-jwt`, `jwt_vc_json`+..., `vc+sd-jwt`). Form-decoding a verifier's
     * `client_metadata={"vp_formats_supported":{"dc+sd-jwt":...}}` yields the format `dc sd-jwt`, which
     * matches nothing and fails the request outright. The conformance suite sends the bare `+`, so
     * form-decoding here made every SD-JWT VC presentation over `request_method=url_query` impossible
     * while ISO mdoc - whose `mso_mdoc` identifier has no `+` - was unaffected.
     *
     * Reading `+` literally is therefore the interoperable choice, and it loses nothing: a Verifier
     * that means a space has `%20` available, and no OpenID4VP parameter carries a meaningful space.
     */
    internal fun authorizationRequestParameters(requestUrl: Url): Parameters =
        Parameters.build {
            parseQueryString(requestUrl.encodedQuery, decode = false).forEach { name, values ->
                val decodedName = name.decodeURLQueryComponent(plusIsSpace = false)
                values.forEach { value ->
                    append(decodedName, value.decodeURLQueryComponent(plusIsSpace = false))
                }
            }
        }

    fun parseParameters(parameters: Parameters): AuthorizationRequest {
        log.trace { "Resolving AuthorizationRequest from direct request parameters" }
        return json.decodeFromJsonElement(
            AuthorizationRequest.serializer(),
            applyRedirectUriPrefixBinding(
                buildJsonObject {
                    parameters.entries()
                        .mapNotNull { (key, values) -> values.lastOrNull()?.let { key to it } }
                        .forEach { (key, value) ->
                            put(
                                key,
                                AuthorizationRequestParameterCodec.parse(json, value)
                            )
                        }
                }
            ),
        ).also { it.dcqlQuery?.precheck() }
    }

    /**
     * Plain requests are authenticated only by the redirect_uri client identifier. Other client
     * identifier prefixes require a signed Request Object or a configured registration.
     */
    private fun parsePlainRequest(parameters: Parameters): AuthorizationRequest {
        val clientId = requireNotNull(parameters["client_id"]) {
            "client_id is required in an Authorization Request"
        }
        val client = ClientIdPrefixParser.parse(clientId).getOrElse { error ->
            throw IllegalArgumentException("Could not parse client_id prefix: $clientId", error)
        }
        require(client is RedirectUri) {
            "Client Identifier '$clientId' cannot be authenticated as a plain request"
        }

        val embeddedUri = client.rawValue.substringAfter(':')
        val responseMode = parameters["response_mode"]
        val deliveryParameter = if (responseMode in setOf("direct_post", "direct_post.jwt")) {
            "response_uri"
        } else {
            "redirect_uri"
        }
        parameters[deliveryParameter]?.let { explicitUri ->
            require(explicitUri == embeddedUri) {
                "Authorization Request $deliveryParameter '$explicitUri' does not match the " +
                    "redirect_uri client_id '$embeddedUri'"
            }
        }
        return parseParameters(Parameters.build {
            appendAll(parameters)
            if (parameters[deliveryParameter] == null) append(deliveryParameter, embeddedUri)
        })
    }

    private fun parseParametersLegacy(parameters: Parameters): AuthorizationRequest =
        json.decodeFromJsonElement(
            AuthorizationRequest.serializer(),
            buildJsonObject {
                parameters.entries()
                    .mapNotNull { (key, values) -> values.lastOrNull()?.let { key to it } }
                    .forEach { (key, value) ->
                        put(key, AuthorizationRequestParameterCodec.parse(json, value))
                    }
            },
        )

    private suspend fun resolveFromRequestUri(
        requestUri: String,
        requestUriMethod: String?,
        outerClientId: String?,
        enforceFinalRequestObject: Boolean,
        unsignedRequestObjectPolicy: UnsignedRequestObjectPolicy,
        fetchRequestUri: suspend (requestUri: String, requestUriMethod: RequestUriHttpMethod?) -> RequestUriFetchResponse,
        trustConfiguration: ClientIdTrustConfiguration,
        expectedRequestObjectAudience: String,
    ): ResolvedAuthorizationRequest {
        log.trace { "Resolving AuthorizationRequest via request_uri" }

        val requestUriMethod = requestUriMethod?.let(::parseRequestUriMethod)
        log.trace { "Fetching AuthorizationRequest from request_uri using method ${requestUriMethod?.method ?: "get"}" }
        val response = fetchRequestUri(requestUri, requestUriMethod)
        response.status.run { check(isSuccess()) { "AuthorizationRequest cannot be retrieved ($this) from $requestUri: ${response.body}" } }

        if (enforceFinalRequestObject && requestUriMethod == RequestUriHttpMethod.POST) {
            requireNotNull(response.walletNonce) {
                "request_uri_method=post response is missing the wallet_nonce binding"
            }
        }

        val contentType = requireNotNull(response.contentType) { "AuthorizationRequest response does not define a content type" }
        log.trace { "Resolved AuthorizationRequest response with content type $contentType" }

        return when {
            contentType.match("application/oauth-authz-req+jwt") -> resolveFromRequestObject(
                requestObject = response.body,
                outerClientId = outerClientId,
                enforceFinalRequestObject = enforceFinalRequestObject,
                unsignedRequestObjectPolicy = unsignedRequestObjectPolicy,
                expectedWalletNonce = response.walletNonce,
                trustConfiguration = trustConfiguration,
                expectedRequestObjectAudience = expectedRequestObjectAudience,
                requireOuterClientId = true,
            )
            contentType.match(ContentType.Application.Json) -> resolveFromUnsignedJson(
                body = response.body,
                outerClientId = outerClientId,
                enforceFinalRequestObject = enforceFinalRequestObject,
                unsignedRequestObjectPolicy = unsignedRequestObjectPolicy,
            )
            else -> throw IllegalArgumentException("Unsupported AuthorizationRequest content type: $contentType")
        }
    }

    /**
     * Authenticates a compact Request Object that was not fetched from `request_uri`.
     *
     * Digital Credentials API signed requests carry only the JWT in `data.request`, with no outer
     * `client_id` query parameter. HTTP JAR still requires the outer `client_id` to match.
     */
    suspend fun resolveInlineRequestObject(
        requestObject: String,
        trustConfiguration: ClientIdTrustConfiguration,
        expectedRequestObjectAudience: String = DEFAULT_REQUEST_OBJECT_AUDIENCE,
        requireOuterClientId: Boolean = false,
        outerClientId: String? = null,
    ): ResolvedAuthorizationRequest = resolveFromRequestObject(
        requestObject = requestObject,
        outerClientId = outerClientId,
        enforceFinalRequestObject = true,
        unsignedRequestObjectPolicy = UnsignedRequestObjectPolicy.REQUIRE_SIGNED,
        trustConfiguration = trustConfiguration,
        expectedRequestObjectAudience = expectedRequestObjectAudience,
        requireOuterClientId = requireOuterClientId,
    )

    private suspend fun resolveFromRequestObject(
        requestObject: String,
        outerClientId: String?,
        enforceFinalRequestObject: Boolean,
        unsignedRequestObjectPolicy: UnsignedRequestObjectPolicy,
        expectedWalletNonce: String? = null,
        trustConfiguration: ClientIdTrustConfiguration = ClientIdTrustConfiguration(),
        expectedRequestObjectAudience: String = DEFAULT_REQUEST_OBJECT_AUDIENCE,
        requireOuterClientId: Boolean = true,
    ): ResolvedAuthorizationRequest {
        log.trace { "Resolving AuthorizationRequest via inline request object" }
        require(requestObject.isJwt()) { "AuthorizationRequest object must be a JWT" }

        val authReqJws = requestObject.decodeJws()
        val jwtAlg = authReqJws.header["alg"]?.jsonPrimitive?.contentOrNull
        val isUnsigned = jwtAlg.equals("none", ignoreCase = true)
        if (enforceFinalRequestObject) {
            requireRequestObjectType(authReqJws.header["typ"]?.jsonPrimitive?.contentOrNull, isUnsigned)
            val innerClientId = authReqJws.payload["client_id"]?.jsonPrimitive?.contentOrNull
            if (requireOuterClientId) {
                requireMatchingClientId(
                    outerClientId = outerClientId,
                    innerClientId = innerClientId,
                )
            } else {
                require(!innerClientId.isNullOrBlank()) {
                    "Authorization Request Object client_id is required"
                }
            }
            validateCommonRequestObjectClaims(
                payload = authReqJws.payload,
                expectedAudience = expectedRequestObjectAudience,
            )
        }
        expectedWalletNonce?.let { nonce ->
            val walletNonceClaim = authReqJws.payload["wallet_nonce"]?.jsonPrimitive?.contentOrNull
            require(walletNonceClaim == nonce) {
                "AuthorizationRequest object wallet_nonce mismatch for request_uri_method=post"
            }
        }
        if (isUnsigned) {
            requireUnsignedRequestObjectAllowed(
                clientId = authReqJws.payload["client_id"]?.jsonPrimitive?.contentOrNull,
                policy = unsignedRequestObjectPolicy,
            )
            return ResolvedAuthorizationRequest.UnsignedRequestObject(
                authorizationRequest = json.decodeFromJsonElement(
                    deserializer = AuthorizationRequest.serializer(),
                    element = applyRedirectUriPrefixBinding(authReqJws.payload),
                ),
                requestObject = requestObject,
            )
        }

        log.trace { "Authenticating signed AuthorizationRequest object" }
        val authentication = authenticateSignedRequestObject(
            requestObject = requestObject,
            payload = authReqJws.payload,
            algorithm = requireNotNull(jwtAlg) { "Signed AuthorizationRequest is missing alg" },
            keyId = authReqJws.header["kid"]?.jsonPrimitive?.contentOrNull,
            trustConfiguration = trustConfiguration,
        )

        return ResolvedAuthorizationRequest.AuthenticatedRequestObject(
            authorizationRequest = json.decodeFromJsonElement(
                deserializer = AuthorizationRequest.serializer(),
                element = applyRedirectUriPrefixBinding(authReqJws.payload),
            ).also { if (enforceFinalRequestObject) it.dcqlQuery?.precheck() },
            requestObject = requestObject,
            authentication = authentication,
        )
    }

    private fun validateCommonRequestObjectClaims(
        payload: JsonObject,
        expectedAudience: String,
    ) {
        val audience = payload["aud"]?.let { element ->
            when (element) {
                is JsonPrimitive -> listOfNotNull(element.contentOrNull)
                is JsonArray -> element.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                else -> emptyList()
            }
        }.orEmpty()
        require(expectedAudience in audience) {
            "Authorization Request Object aud must contain '$expectedAudience'"
        }

        val now = Clock.System.now().epochSeconds
        payload["exp"]?.let { element ->
            val expiration = (element as? JsonPrimitive)?.longOrNull
                ?: throw IllegalArgumentException("Authorization Request Object exp must be a NumericDate")
            require(expiration >= now - CLOCK_SKEW_SECONDS) {
                "Authorization Request Object is expired (exp=$expiration, now=$now)"
            }
        }
        payload["nbf"]?.let { element ->
            val notBefore = (element as? JsonPrimitive)?.longOrNull
                ?: throw IllegalArgumentException("Authorization Request Object nbf must be a NumericDate")
            require(notBefore <= now + CLOCK_SKEW_SECONDS) {
                "Authorization Request Object is not yet valid (nbf=$notBefore, now=$now)"
            }
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    private suspend fun authenticateSignedRequestObject(
        requestObject: String,
        payload: JsonObject,
        algorithm: String,
        keyId: String?,
        trustConfiguration: ClientIdTrustConfiguration,
    ): RequestObjectAuthentication {
        val clientId = requireNotNull(payload["client_id"]?.jsonPrimitive?.contentOrNull) {
            "Missing client_id for signed AuthorizationRequest"
        }
        val parsedClientId = ClientIdPrefixParser.parse(clientId)
            .getOrElse { error -> throw IllegalArgumentException("Could not parse client_id prefix: $clientId", error) }
        val clientMetadata = payload["client_metadata"]?.let {
            ClientMetadata.fromJson(it)
                .getOrElse { error -> throw IllegalArgumentException("Could not parse client metadata", error) }
        }

        val context = RequestContext(
            clientId = clientId,
            clientMetadata = clientMetadata,
            requestObjectJws = requestObject,
            redirectUri = payload["redirect_uri"]?.jsonPrimitive?.contentOrNull,
            responseUri = payload["response_uri"]?.jsonPrimitive?.contentOrNull,
        )

        when (val validationResult = ClientIdPrefixAuthenticator.authenticate(
            parsedClientId,
            context,
            preRegisteredMetadataProvider = { clientId ->
                trustConfiguration.preRegisteredClients[clientId]?.let {
                    json.encodeToString(ClientMetadata.serializer(), it)
                }
            },
            trustConfiguration = trustConfiguration,
        )) {
            is ClientValidationResult.Success -> {
                log.trace { "Signed AuthorizationRequest authentication succeeded for client_id scheme ${parsedClientId::class.simpleName}" }
            }

            is ClientValidationResult.Failure -> throw SignedAuthorizationRequestValidationException(validationResult.error)
        }
        return RequestObjectAuthentication(
            clientId = parsedClientId,
            algorithm = algorithm,
            keyId = keyId,
        )
    }

    private fun parseRequestUriMethod(value: String): RequestUriHttpMethod = when (value) {
        RequestUriHttpMethod.GET.method -> RequestUriHttpMethod.GET
        RequestUriHttpMethod.POST.method -> RequestUriHttpMethod.POST
        else -> throw IllegalArgumentException("invalid_request_uri_method: $value is neither 'get' nor 'post'")
    }

    /**
     * Unsigned JSON from `request_uri` is the Verifier2 bootstrap path. It is accepted when the
     * Client Identifier is `redirect_uri`. Signed-only wallets ([UnsignedRequestObjectPolicy.REQUIRE_SIGNED])
     * reject it.
     */
    private fun resolveFromUnsignedJson(
        body: String,
        outerClientId: String?,
        enforceFinalRequestObject: Boolean,
        unsignedRequestObjectPolicy: UnsignedRequestObjectPolicy,
    ): ResolvedAuthorizationRequest {
        if (!enforceFinalRequestObject) {
            return ResolvedAuthorizationRequest.Plain(json.decodeFromString(body))
        }
        requireUnsignedRequestObjectAllowed(outerClientId, unsignedRequestObjectPolicy)
        val payload = json.parseToJsonElement(body)
        require(payload is JsonObject) {
            "Unsigned authorization request from request_uri must be a JSON object"
        }
        val authorizationRequest = json.decodeFromJsonElement(
            deserializer = AuthorizationRequest.serializer(),
            element = applyRedirectUriPrefixBinding(payload),
        ).also { it.dcqlQuery?.precheck() }
        requireMatchingClientId(outerClientId, authorizationRequest.clientId)
        return ResolvedAuthorizationRequest.Plain(authorizationRequest)
    }

    /**
     * Unsigned Authorization Requests are JSON from `request_uri`, query parameters, or `alg: none`
     * JWTs. They are accepted when [UnsignedRequestObjectPolicy.ALLOW_UNSIGNED] is set **and**
     * the Client Identifier Prefix is `redirect_uri`.
     *
     * That is the unsigned Verifier2 bootstrap: JSON at `request_uri` named by `redirect_uri:<response
     * destination>`. Signed Request Objects remain accepted under either policy.
     * [UnsignedRequestObjectPolicy.REQUIRE_SIGNED] rejects `redirect_uri` entirely because
     * that prefix cannot carry a signature.
     *
     * Unsigned JSON or `alg: none` for a signable prefix (`x509_san_dns`, DID, attestation, …) is
     * always refused. Accepting it would let anyone claim, say, `x509_san_dns:bank.example.com`.
     */
    private fun requireUnsignedRequestObjectAllowed(clientId: String?, policy: UnsignedRequestObjectPolicy) {
        if (policy != UnsignedRequestObjectPolicy.ALLOW_UNSIGNED) {
            throw UnsignedAuthorizationRequestNotAllowedException(
                "Unsigned Authorization Request is not allowed",
            )
        }
        val isRedirectUri = clientId
            ?.let { ClientIdPrefixParser.parse(it).getOrNull() }
            ?.let { it is RedirectUri }
            ?: false
        if (!isRedirectUri) {
            throw UnsignedAuthorizationRequestNotAllowedException(
                "Unsigned Authorization Request is only allowed for the redirect_uri client identifier prefix",
            )
        }
    }

    /**
     * Enforce the identity the `redirect_uri` Client Identifier Prefix asserts, and fill in the
     * destination when the Verifier left it out.
     *
     * OpenID4VP 1.0 Section 5.9.3: "This prefix value indicates that the original Client Identifier
     * part (without the prefix `redirect_uri:`) **is** the Verifier's Redirect URI (or Response URI
     * when Response Mode `direct_post` is used). The Verifier MAY omit the `redirect_uri`
     * Authorization Request parameter (or `response_uri` when Response Mode `direct_post` is used)."
     *
     * Two consequences, and this prefix has no signature to lean on for either:
     * - Omitted: the destination is derived from the Client Identifier. Requiring it to be present
     *   instead rejected every Verifier that exercises the carve-out.
     * - Present but different: the request is claiming an identifier it does not own, and would have
     *   the wallet post a Presentation somewhere the Client Identifier does not authorise. Refused.
     *   Without this the wallet posted the VP Token to whatever `response_uri` it was handed, which is
     *   exactly the leak Section 14.3.1 requires the wallet to prevent.
     */
    private fun applyRedirectUriPrefixBinding(payload: JsonObject): JsonObject {
        val clientId = payload["client_id"]?.jsonPrimitive?.contentOrNull ?: return payload
        if (ClientIdPrefixParser.parse(clientId).getOrNull() !is RedirectUri) return payload
        val boundDestination = clientId.removePrefix("${ClientIdPrefix.REDIRECT_URI.value}:")

        // Response Mode direct_post (and direct_post.jwt, which builds on it) answers to response_uri;
        // every other mode answers to redirect_uri. Applied before deserialization because
        // AuthorizationRequest itself rejects a direct_post request whose response_uri is absent, so a
        // derived value has to be in place by then.
        val responseMode = payload["response_mode"]?.let { mode ->
            runCatching { json.decodeFromJsonElement(OpenID4VPResponseMode.serializer(), mode) }.getOrNull()
        }
        val destinationParameter =
            if (responseMode in OpenID4VPResponseMode.DIRECT_POST_RESPONSES) "response_uri" else "redirect_uri"
        val declared = payload[destinationParameter]?.jsonPrimitive?.contentOrNull
        require(declared == null || declared == boundDestination) {
            "Authorization Request $destinationParameter '$declared' does not match the " +
                    "redirect_uri client_id '$boundDestination'"
        }
        if (declared != null) return payload
        return JsonObject(payload + (destinationParameter to JsonPrimitive(boundDestination)))
    }

    private fun requireMatchingClientId(outerClientId: String?, innerClientId: String?) {
        require(!outerClientId.isNullOrBlank()) {
            "client_id is required alongside request or request_uri"
        }
        require(innerClientId == outerClientId) {
            "Authorization Request client_id mismatch between outer request and Request Object"
        }
    }

    /**
     * Check the Request Object's `typ` header.
     *
     * OpenID4VP 1.0 Section 5: "Verifiers MUST include the typ Header Parameter in Request Objects
     * with the value oauth-authz-req+jwt [...] Wallets MUST NOT process Request Objects where the typ
     * Header Parameter is not present or does not have the value oauth-authz-req+jwt."
     *
     * Enforced as written for signed Request Objects. For an unsigned one ([isUnsigned], `alg: none`)
     * an *absent* `typ` is tolerated, because the conformance suite emits exactly `{"alg":"none"}` for
     * `request_method=request_uri_unsigned` - see `AbstractSignClaimsWithNullAlgorithm` - and the suite
     * is treated as the arbiter of interoperability where it disagrees with the prose. A `typ` that is
     * present but wrong is still rejected in both cases.
     *
     * Confining the carve-out to unsigned objects keeps the requirement where it does real work. The
     * `typ` header guards against cross-JWT confusion, i.e. replaying a JWT that some other party
     * legitimately signed in a different context. An `alg: none` object carries no signature to
     * borrow - anyone can mint one with any `typ` - so demanding the header there buys nothing.
     */
    private fun requireRequestObjectType(typ: String?, isUnsigned: Boolean) {
        if (typ == null && isUnsigned) return
        require(typ == REQUEST_OBJECT_TYPE) {
            "Authorization Request Object typ must be '$REQUEST_OBJECT_TYPE'"
        }
    }

}
