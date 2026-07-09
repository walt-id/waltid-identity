package id.waltid.openid4vp.wallet.request

import id.walt.credentials.utils.JwtUtils.isJwt
import id.walt.crypto.utils.UuidUtils
import id.walt.crypto.utils.JwsUtils.decodeJws
import id.walt.openid4vp.clientidprefix.ClientIdError
import id.walt.openid4vp.clientidprefix.ClientIdPrefixAuthenticator
import id.walt.openid4vp.clientidprefix.ClientIdPrefixParser
import id.walt.openid4vp.clientidprefix.ClientValidationResult
import id.walt.openid4vp.clientidprefix.RequestContext
import id.walt.verifier.openid.models.authorization.AuthorizationRequest
import id.walt.verifier.openid.models.authorization.ClientMetadata
import id.walt.verifier.openid.models.authorization.RequestUriHttpMethod
import id.walt.webdatafetching.WebDataFetcher
import id.waltid.openid4vp.wallet.WalletPresentationFormatRegistry
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
 * @property encryptedResponseAlgValuesSupported Key agreement algorithms (default: ECDH-ES).
 * @property encryptedResponseEncValuesSupported Content encryption algorithms (default: A128GCM, A256GCM).
 */
@Serializable
data class WalletCapabilities(
    val vpFormatsSupported: JsonObject = WalletPresentationFormatRegistry.buildVpFormatsSupported(),
    val encryptedResponseAlgValuesSupported: List<String> = listOf("ECDH-ES"),
    val encryptedResponseEncValuesSupported: List<String> = listOf("A128GCM", "A256GCM"),
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

    private val defaultWalletCapabilities = WalletCapabilities()

    private val defaultRequestUriPostWalletMetadata = buildRequestUriPostWalletMetadata(defaultWalletCapabilities)

    /**
     * Builds wallet_metadata JSON for request_uri_method=post requests.
     *
     * @param capabilities Wallet capabilities to advertise.
     * @return JSON-encoded wallet_metadata string.
     */
    fun buildRequestUriPostWalletMetadata(capabilities: WalletCapabilities): String = json.encodeToString(
        JsonObject.serializer(),
        buildJsonObject {
            put("vp_formats_supported", capabilities.vpFormatsSupported)
            put(
                "encrypted_response_alg_values_supported",
                JsonArray(capabilities.encryptedResponseAlgValuesSupported.map { JsonPrimitive(it) })
            )
            put(
                "encrypted_response_enc_values_supported",
                JsonArray(capabilities.encryptedResponseEncValuesSupported.map { JsonPrimitive(it) })
            )
        },
    )

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

    suspend fun resolve(
        requestUrl: Url,
        unsignedRequestObjectPolicy: UnsignedRequestObjectPolicy,
        fetchRequestUri: suspend (requestUri: String, requestUriMethod: RequestUriHttpMethod?) -> RequestUriFetchResponse,
    ): ResolvedAuthorizationRequest {
        val requestUri = requestUrl.parameters["request_uri"]
        if (requestUri != null) {
            return resolveFromRequestUri(
                requestUri = requestUri,
                requestUriMethod = requestUrl.parameters["request_uri_method"],
                unsignedRequestObjectPolicy = unsignedRequestObjectPolicy,
                fetchRequestUri = fetchRequestUri,
            )
        }

        val requestObject = requestUrl.parameters["request"]
        if (requestObject != null) return resolveFromRequestObject(requestObject, unsignedRequestObjectPolicy)

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
        unsignedRequestObjectPolicy: UnsignedRequestObjectPolicy,
        fetchRequestUri: suspend (requestUri: String, requestUriMethod: RequestUriHttpMethod?) -> RequestUriFetchResponse,
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
                unsignedRequestObjectPolicy = unsignedRequestObjectPolicy,
                expectedWalletNonce = response.walletNonce,
            )
            contentType.match(ContentType.Application.Json) -> ResolvedAuthorizationRequest.Plain(
                authorizationRequest = json.decodeFromString<AuthorizationRequest>(response.body),
            )
            else -> throw IllegalArgumentException("Unsupported AuthorizationRequest content type: $contentType")
        }
    }

    private suspend fun resolveFromRequestObject(
        requestObject: String,
        unsignedRequestObjectPolicy: UnsignedRequestObjectPolicy,
        expectedWalletNonce: String? = null,
    ): ResolvedAuthorizationRequest {
        log.trace { "Resolving AuthorizationRequest via inline request object" }
        require(requestObject.isJwt()) { "AuthorizationRequest object must be a JWT" }

        val authReqJws = requestObject.decodeJws()
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
            authenticateSignedRequestObject(requestObject, authReqJws.payload)
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
    private suspend fun authenticateSignedRequestObject(requestObject: String, payload: JsonObject) {
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

        when (val validationResult = ClientIdPrefixAuthenticator.authenticate(clientIdPrefix, context)) {
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

}
