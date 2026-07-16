package id.waltid.openid4vp.wallet.request

import id.walt.crypto.utils.UuidUtils
import id.walt.openid4vp.clientidprefix.ClientIdError
import id.walt.openid4vp.clientidprefix.ClientIdPrefix
import id.walt.openid4vp.clientidprefix.X509TrustPolicy
import id.walt.verifier.openid.models.authorization.AuthorizationRequest
import id.walt.verifier.openid.models.authorization.RequestUriHttpMethod
import id.walt.verifier.openid.models.openid.OpenID4VPResponseMode
import id.walt.verifier.openid.models.openid.OpenID4VPResponseType
import id.walt.webdatafetching.WebDataFetcher
import id.waltid.openid4vp.wallet.WalletPresentationFormatRegistry
import id.waltid.openid4vp.wallet.validation.SignedRequestValidator
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Wallet capabilities advertised in `wallet_metadata` for request_uri_method=post.
 *
 * Per OID4VP 1.0 §5.1, when the wallet fetches a signed request via POST,
 * it SHOULD include `wallet_metadata` with its supported VP formats and
 * encryption algorithms.
 *
 * @property vpFormatsSupported Supported VP formats (e.g., jwt_vc_json, vc+sd-jwt, mso_mdoc).
 * @property authorizationEncryptionAlgValuesSupported Key agreement algorithms (default: ECDH-ES).
 * @property authorizationEncryptionEncValuesSupported Content encryption algorithms (default: A128GCM, A256GCM).
 */
@Serializable
data class WalletCapabilities(
    val vpFormatsSupported: JsonObject = WalletPresentationFormatRegistry.buildVpFormatsSupported(),
    val authorizationEncryptionAlgValuesSupported: List<String> = listOf("ECDH-ES"),
    val authorizationEncryptionEncValuesSupported: List<String> = listOf("A128GCM", "A256GCM"),
)

object AuthorizationRequestResolver {
    private val log = KotlinLogging.logger { }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        isLenient = true
    }

    class SignedAuthorizationRequestValidationException(
        val clientIdError: ClientIdError,
        override val message: String,
    ) : IllegalArgumentException(message) {
        constructor(clientIdError: ClientIdError) : this(
            clientIdError,
            "Could not verify signed AuthorizationRequest with client id prefix: ${clientIdError::class.simpleName} - ${clientIdError.message}"
        )
    }

    data class RequestUriFetchResponse(
        val status: HttpStatusCode,
        val contentType: ContentType?,
        val body: String,
        val walletNonce: String? = null,
    )

    /**
     * Policy for handling unsigned (alg=none) authorization requests.
     * Maps to [SignedRequestValidator.UnsignedRequestObjectPolicy] for internal use.
     */
    enum class UnsignedRequestObjectPolicy {
        ALLOW_UNSIGNED,
        REQUIRE_SIGNED,
        ;

        internal fun toValidatorPolicy(): SignedRequestValidator.UnsignedRequestObjectPolicy = when (this) {
            ALLOW_UNSIGNED -> SignedRequestValidator.UnsignedRequestObjectPolicy.ALLOW_UNSIGNED
            REQUIRE_SIGNED -> SignedRequestValidator.UnsignedRequestObjectPolicy.REQUIRE_SIGNED
        }
    }

    class UnsignedAuthorizationRequestNotAllowedException :
        IllegalArgumentException("Unsigned AuthorizationRequest object (alg=none) is not allowed")

    private val defaultWalletCapabilities = WalletCapabilities()

    private val defaultRequestUriPostWalletMetadata: String by lazy {
        buildRequestUriPostWalletMetadata(defaultWalletCapabilities)
    }

    private val unsupportedResponseTypes = setOf(OpenID4VPResponseType.CODE)
    private val responseTypesSupported = (OpenID4VPResponseType.entries - unsupportedResponseTypes)
        .map { it.responseType }
    private val responseModesSupported = (OpenID4VPResponseMode.entries - OpenID4VPResponseMode.DC_API_RESPONSES)
        .map { json.encodeToJsonElement(OpenID4VPResponseMode.serializer(), it).jsonPrimitive.content }
    private val unsupportedClientIdPrefixes = setOf(
        ClientIdPrefix.PRE_REGISTERED,
        ClientIdPrefix.OPENID_FEDERATION,
    )
    private val clientIdPrefixesSupported = (ClientIdPrefix.entries - unsupportedClientIdPrefixes)
        .map { it.value }

    /**
     * Builds wallet_metadata JSON for request_uri_method=post requests.
     *
     * @param capabilities Wallet capabilities to advertise.
     * @return JSON-encoded wallet_metadata string.
     */
    fun buildRequestUriPostWalletMetadata(capabilities: WalletCapabilities): String = json.encodeToString(
        JsonObject.serializer(),
        buildJsonObject {
            put("response_types_supported", responseTypesSupported.toJsonArray())
            put("response_modes_supported", responseModesSupported.toJsonArray())
            put("client_id_prefixes_supported", clientIdPrefixesSupported.toJsonArray())
            put("vp_formats_supported", capabilities.vpFormatsSupported)
            put(
                "authorization_encryption_alg_values_supported",
                JsonArray(capabilities.authorizationEncryptionAlgValuesSupported.map { JsonPrimitive(it) })
            )
            put(
                "authorization_encryption_enc_values_supported",
                JsonArray(capabilities.authorizationEncryptionEncValuesSupported.map { JsonPrimitive(it) })
            )
        },
    )

    private fun Iterable<String>.toJsonArray(): JsonArray = JsonArray(map(::JsonPrimitive))

    /**
     * Legacy overload for backward compatibility.
     */
    @Deprecated(
        "Use buildRequestUriPostWalletMetadata(WalletCapabilities) instead",
        ReplaceWith("buildRequestUriPostWalletMetadata(WalletCapabilities(vpFormatsSupported = vpFormatsSupported))")
    )
    fun buildRequestUriPostWalletMetadata(vpFormatsSupported: JsonObject): String =
        buildRequestUriPostWalletMetadata(WalletCapabilities(vpFormatsSupported = vpFormatsSupported))

    /**
     * Shared transport mapping for retrieving Authorization Requests via `request_uri`.
     * Keeps GET/POST behavior and response conversion centralized for all wallet callers.
     */
    suspend fun fetchRequestUriWithWebDataFetcher(
        webResolveAuthReq: WebDataFetcher,
        requestUri: String,
        requestUriMethod: RequestUriHttpMethod?,
        requestUriPostWalletMetadata: String? = null,
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
                    Parameters.build {
                        append("wallet_metadata", requestUriPostWalletMetadata ?: defaultRequestUriPostWalletMetadata)
                        append("wallet_nonce", requireNotNull(walletNonce))
                    }.formUrlEncode(),
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

    /**
     * Resolves an authorization request from its URL representation.
     *
     * @param requestUrl The authorization request URL.
     * @param unsignedRequestObjectPolicy Policy for handling unsigned requests.
     * @param fetchRequestUri Function to fetch request_uri content.
     * @param expectedRequestObjectAudience Static Discovery uses `https://self-issued.me/v2`;
     *        Dynamic Discovery uses the discovered Wallet issuer.
     */
    suspend fun resolve(
        requestUrl: Url,
        unsignedRequestObjectPolicy: UnsignedRequestObjectPolicy,
        expectedRequestObjectAudience: String = "https://self-issued.me/v2",
        x509TrustPolicy: X509TrustPolicy? = null,
        fetchRequestUri: suspend (requestUri: String, requestUriMethod: RequestUriHttpMethod?) -> RequestUriFetchResponse,
    ): ResolvedAuthorizationRequest {
        val requestUri = requestUrl.parameters["request_uri"]
        val requestObject = requestUrl.parameters["request"]
        require(requestUri == null || requestObject == null) {
            "Authorization Request must not contain both request and request_uri"
        }
        require(requestUri != null || requestUrl.parameters["request_uri_method"] == null) {
            "request_uri_method must not be present without request_uri"
        }

        val outerClientId = requestUrl.parameters["client_id"]

        if (requestUri != null) {
            return resolveFromRequestUri(
                requestUri = requestUri,
                requestUriMethod = requestUrl.parameters["request_uri_method"],
                outerClientId = outerClientId,
                unsignedRequestObjectPolicy = unsignedRequestObjectPolicy,
                fetchRequestUri = fetchRequestUri,
                expectedRequestObjectAudience = expectedRequestObjectAudience,
                x509TrustPolicy = x509TrustPolicy,
            )
        }

        if (requestObject != null) {
            return resolveFromRequestObject(
                requestObject = requestObject,
                outerClientId = outerClientId,
                unsignedRequestObjectPolicy = unsignedRequestObjectPolicy,
                expectedRequestObjectAudience = expectedRequestObjectAudience,
                x509TrustPolicy = x509TrustPolicy,
            )
        }

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
        ).also { it.dcqlQuery?.precheck() }
    }

    private suspend fun resolveFromRequestUri(
        requestUri: String,
        requestUriMethod: String?,
        outerClientId: String?,
        unsignedRequestObjectPolicy: UnsignedRequestObjectPolicy,
        fetchRequestUri: suspend (requestUri: String, requestUriMethod: RequestUriHttpMethod?) -> RequestUriFetchResponse,
        expectedRequestObjectAudience: String,
        x509TrustPolicy: X509TrustPolicy?,
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
                unsignedRequestObjectPolicy = unsignedRequestObjectPolicy,
                expectedWalletNonce = response.walletNonce,
                expectedRequestObjectAudience = expectedRequestObjectAudience,
                x509TrustPolicy = x509TrustPolicy,
            )
            else -> throw IllegalArgumentException("Unsupported AuthorizationRequest content type: $contentType")
        }
    }

    /**
     * Resolves a signed authorization request object using [SignedRequestValidator].
     * Enforces OID4VP 1.0 §5 requirements: typ header, aud claim, client_id equality.
     */
    private suspend fun resolveFromRequestObject(
        requestObject: String,
        outerClientId: String?,
        unsignedRequestObjectPolicy: UnsignedRequestObjectPolicy,
        expectedWalletNonce: String? = null,
        expectedRequestObjectAudience: String = "https://self-issued.me/v2",
        x509TrustPolicy: X509TrustPolicy? = null,
    ): ResolvedAuthorizationRequest {
        log.trace { "Resolving AuthorizationRequest via inline request object" }

        // Delegate to SignedRequestValidator for full OID4VP 1.0 §5 compliance
        val requiredOuterClientId = requireNotNull(outerClientId) {
            "Missing client_id in outer Authorization Request"
        }
        val validationResult = SignedRequestValidator.validate(
            requestObjectJwt = requestObject,
            outerClientId = requiredOuterClientId,
            expectedWalletNonce = expectedWalletNonce,
            expectedAudience = expectedRequestObjectAudience,
            x509TrustPolicy = x509TrustPolicy,
            unsignedPolicy = unsignedRequestObjectPolicy.toValidatorPolicy(),
        )

        return when (validationResult) {
            is SignedRequestValidator.ValidationResult.Success -> {
                log.trace { "Signed AuthorizationRequest validation succeeded" }
                ResolvedAuthorizationRequest.WithRequestObject(
                    authorizationRequest = validationResult.authorizationRequest,
                    requestObject = requestObject,
                )
            }
            is SignedRequestValidator.ValidationResult.Failure -> {
                if (validationResult.message.contains("alg=none")) {
                    throw UnsignedAuthorizationRequestNotAllowedException()
                }
                val error = validationResult.error
                if (error != null) {
                    throw SignedAuthorizationRequestValidationException(error, validationResult.message)
                } else {
                    throw IllegalArgumentException(validationResult.message)
                }
            }
        }
    }

    private fun parseRequestUriMethod(value: String): RequestUriHttpMethod = when (value) {
        RequestUriHttpMethod.GET.method -> RequestUriHttpMethod.GET
        RequestUriHttpMethod.POST.method -> RequestUriHttpMethod.POST
        else -> throw IllegalArgumentException("invalid_request_uri_method: $value is neither 'get' nor 'post'")
    }

}
