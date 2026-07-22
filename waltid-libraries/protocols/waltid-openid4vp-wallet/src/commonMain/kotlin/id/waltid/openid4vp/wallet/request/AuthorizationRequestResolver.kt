package id.waltid.openid4vp.wallet.request

import id.walt.credentials.utils.JwtUtils.isJwt
import id.walt.crypto.utils.UuidUtils
import id.walt.crypto.utils.JwsUtils.decodeJws
import id.walt.openid4vp.clientidprefix.ClientIdError
import id.walt.openid4vp.clientidprefix.ClientIdTrustConfiguration
import id.walt.openid4vp.clientidprefix.ClientIdPrefix
import id.walt.openid4vp.clientidprefix.ClientIdPrefixAuthenticator
import id.walt.openid4vp.clientidprefix.ClientIdPrefixParser
import id.walt.openid4vp.clientidprefix.ClientValidationResult
import id.walt.openid4vp.clientidprefix.RequestContext
import id.walt.verifier.openid.models.authorization.AuthorizationRequest
import id.walt.verifier.openid.models.authorization.ClientMetadata
import id.walt.verifier.openid.models.authorization.RequestUriHttpMethod
import id.walt.verifier.openid.models.openid.OpenID4VPResponseMode
import id.walt.verifier.openid.models.openid.OpenID4VPResponseType
import id.walt.webdatafetching.WebDataFetcher
import id.waltid.openid4vp.wallet.WalletPresentationFormatRegistry
import io.github.oshai.kotlinlogging.KotlinLogging
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

object AuthorizationRequestResolver {
    private val log = KotlinLogging.logger { }
    private const val REQUEST_OBJECT_TYPE = "oauth-authz-req+jwt"

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

    enum class UnsignedRequestObjectPolicy {
        ALLOW_UNSIGNED,
        REQUIRE_SIGNED,
    }

    class UnsignedAuthorizationRequestNotAllowedException :
        IllegalArgumentException("Unsigned AuthorizationRequest object (alg=none) is not allowed")

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
            // Pre-registered clients require a verifier metadata provider; this resolver uses the authenticator default.
            ClientIdPrefix.PRE_REGISTERED,
            // OpenID Federation parsing exists, but trust chain resolution is not implemented yet.
            ClientIdPrefix.OPENID_FEDERATION,
        )

        fun build(
            vpFormatsSupported: JsonObject,
            trustConfiguration: ClientIdTrustConfiguration = ClientIdTrustConfiguration(),
        ): String = json.encodeToString(
            serializer = JsonObject.serializer(),
            value = buildJsonObject {
                put("response_types_supported", responseTypesSupported.toJsonArray())
                put("response_modes_supported", responseModesSupported.toJsonArray())
                val unsupported = unsupportedClientIdPrefixes + buildSet {
                    if (trustConfiguration.x509TrustAnchors.isEmpty()) {
                        add(ClientIdPrefix.X509_SAN_DNS)
                        add(ClientIdPrefix.X509_HASH)
                    }
                    if (trustConfiguration.trustedVerifierAttestationIssuers.isEmpty()) {
                        add(ClientIdPrefix.VERIFIER_ATTESTATION)
                    }
                }
                put("client_id_prefixes_supported", (ClientIdPrefix.entries - unsupported).map { it.value }.toJsonArray())
                put("vp_formats_supported", vpFormatsSupported)
            },
        )

        private fun Iterable<String>.toJsonArray(): JsonArray =
            JsonArray(map(::JsonPrimitive))
    }

    fun buildRequestUriPostWalletMetadata(vpFormatsSupported: JsonObject): String =
        RequestUriPostWalletMetadata.build(vpFormatsSupported)

    val defaultRequestUriPostWalletMetadata: String
        get() = RequestUriPostWalletMetadata.default

    fun buildRequestUriPostWalletMetadata(
        vpFormatsSupported: JsonObject,
        trustConfiguration: ClientIdTrustConfiguration,
    ): String = RequestUriPostWalletMetadata.build(vpFormatsSupported, trustConfiguration)

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
        unsignedRequestObjectPolicy: UnsignedRequestObjectPolicy,
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
        unsignedRequestObjectPolicy: UnsignedRequestObjectPolicy,
        enforceFinalRequestObject: Boolean = true,
        fetchRequestUri: suspend (requestUri: String, requestUriMethod: RequestUriHttpMethod?) -> RequestUriFetchResponse,
        trustConfiguration: ClientIdTrustConfiguration,
    ): ResolvedAuthorizationRequest {
        val requestUri = requestUrl.parameters["request_uri"]
        if (requestUri != null) {
            return resolveFromRequestUri(
                requestUri = requestUri,
                requestUriMethod = requestUrl.parameters["request_uri_method"],
                outerClientId = requestUrl.parameters["client_id"],
                enforceFinalRequestObject = enforceFinalRequestObject,
                unsignedRequestObjectPolicy = unsignedRequestObjectPolicy,
                fetchRequestUri = fetchRequestUri,
                trustConfiguration = trustConfiguration,
            )
        }

        val requestObject = requestUrl.parameters["request"]
        if (requestObject != null) return resolveFromRequestObject(
            requestObject = requestObject,
            outerClientId = requestUrl.parameters["client_id"],
            enforceFinalRequestObject = enforceFinalRequestObject,
            unsignedRequestObjectPolicy = unsignedRequestObjectPolicy,
            trustConfiguration = trustConfiguration,
        )

        return ResolvedAuthorizationRequest.Plain(parseParameters(requestUrl.parameters))
    }

    fun parseParameters(parameters: Parameters): AuthorizationRequest {
        log.trace { "Resolving AuthorizationRequest from direct request parameters" }
        return json.decodeFromJsonElement(
            AuthorizationRequest.serializer(),
            buildJsonObject {
                parameters.entries()
                    .mapNotNull { (key, values) -> values.lastOrNull()?.let { key to it } }
                    .forEach { (key, value) ->
                        put(
                            key,
                            AuthorizationRequestParameterCodec.parse(json, value)
                        )
                    }
            },
        )
    }

    private suspend fun resolveFromRequestUri(
        requestUri: String,
        requestUriMethod: String?,
        outerClientId: String?,
        enforceFinalRequestObject: Boolean,
        unsignedRequestObjectPolicy: UnsignedRequestObjectPolicy,
        fetchRequestUri: suspend (requestUri: String, requestUriMethod: RequestUriHttpMethod?) -> RequestUriFetchResponse,
        trustConfiguration: ClientIdTrustConfiguration,
    ): ResolvedAuthorizationRequest {
        log.trace { "Resolving AuthorizationRequest via request_uri" }

        val requestUriMethod = requestUriMethod?.let(::parseRequestUriMethod)
        log.trace { "Fetching AuthorizationRequest from request_uri using method ${requestUriMethod?.method ?: "get"}" }
        val response = fetchRequestUri(requestUri, requestUriMethod)
        response.status.run { check(isSuccess()) { "AuthorizationRequest cannot be retrieved ($this) from $requestUri: ${response.body}" } }

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
            )
            contentType.match(ContentType.Application.Json) -> {
                val authorizationRequest = json.decodeFromString<AuthorizationRequest>(response.body)
                if (enforceFinalRequestObject) requireMatchingClientId(outerClientId, authorizationRequest.clientId)
                ResolvedAuthorizationRequest.Plain(authorizationRequest)
            }
            else -> throw IllegalArgumentException("Unsupported AuthorizationRequest content type: $contentType")
        }
    }

    private suspend fun resolveFromRequestObject(
        requestObject: String,
        outerClientId: String?,
        enforceFinalRequestObject: Boolean,
        unsignedRequestObjectPolicy: UnsignedRequestObjectPolicy,
        expectedWalletNonce: String? = null,
        trustConfiguration: ClientIdTrustConfiguration = ClientIdTrustConfiguration(),
    ): ResolvedAuthorizationRequest {
        log.trace { "Resolving AuthorizationRequest via inline request object" }
        require(requestObject.isJwt()) { "AuthorizationRequest object must be a JWT" }

        val authReqJws = requestObject.decodeJws()
        if (enforceFinalRequestObject) {
            require(authReqJws.header["typ"]?.jsonPrimitive?.contentOrNull == REQUEST_OBJECT_TYPE) {
                "Authorization Request Object typ must be '$REQUEST_OBJECT_TYPE'"
            }
            requireMatchingClientId(
                outerClientId = outerClientId,
                innerClientId = authReqJws.payload["client_id"]?.jsonPrimitive?.contentOrNull,
            )
        }
        expectedWalletNonce?.let { nonce ->
            val walletNonceClaim = authReqJws.payload["wallet_nonce"]?.jsonPrimitive?.contentOrNull
            require(walletNonceClaim == nonce) {
                "AuthorizationRequest object wallet_nonce mismatch for request_uri_method=post"
            }
        }
        val jwtAlg = authReqJws.header["alg"]?.jsonPrimitive?.contentOrNull
        if (jwtAlg.equals("none", ignoreCase = true)) {
            if (unsignedRequestObjectPolicy != UnsignedRequestObjectPolicy.ALLOW_UNSIGNED) {
                throw UnsignedAuthorizationRequestNotAllowedException()
            }
        } else {
            log.trace { "Authenticating signed AuthorizationRequest object" }
            authenticateSignedRequestObject(requestObject, authReqJws.payload, trustConfiguration)
        }

        return ResolvedAuthorizationRequest.WithRequestObject(
            authorizationRequest = json.decodeFromJsonElement(
                deserializer = AuthorizationRequest.serializer(),
                element = authReqJws.payload,
            ),
            requestObject = requestObject,
        )
    }

    @OptIn(ExperimentalSerializationApi::class)
    private suspend fun authenticateSignedRequestObject(
        requestObject: String,
        payload: JsonObject,
        trustConfiguration: ClientIdTrustConfiguration,
    ) {
        val clientId = requireNotNull(payload["client_id"]?.jsonPrimitive?.contentOrNull) {
            "Missing client_id for signed AuthorizationRequest"
        }
        val clientIdPrefix = ClientIdPrefixParser.parse(clientId)
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
            clientIdPrefix,
            context,
            preRegisteredMetadataProvider = { clientId ->
                trustConfiguration.preRegisteredClients[clientId]?.let {
                    json.encodeToString(ClientMetadata.serializer(), it)
                }
            },
            trustConfiguration = trustConfiguration,
        )) {
            is ClientValidationResult.Success -> {
                log.trace { "Signed AuthorizationRequest authentication succeeded for client_id prefix ${clientIdPrefix::class.simpleName}" }
            }

            is ClientValidationResult.Failure -> throw SignedAuthorizationRequestValidationException(validationResult.error)
        }
    }

    private fun parseRequestUriMethod(value: String): RequestUriHttpMethod = when (value) {
        RequestUriHttpMethod.GET.method -> RequestUriHttpMethod.GET
        RequestUriHttpMethod.POST.method -> RequestUriHttpMethod.POST
        else -> throw IllegalArgumentException("invalid_request_uri_method: $value is neither 'get' nor 'post'")
    }

    private fun requireMatchingClientId(outerClientId: String?, innerClientId: String?) {
        require(!outerClientId.isNullOrBlank()) {
            "client_id is required alongside request or request_uri"
        }
        require(innerClientId == outerClientId) {
            "Authorization Request client_id mismatch between outer request and Request Object"
        }
    }

}
