package id.waltid.openid4vp.wallet

import id.walt.openid4vp.clientidprefix.X509TrustPolicy
import id.walt.crypto.utils.JwsUtils.decodeJws
import id.walt.verifier.openid.models.authorization.AuthorizationRequest
import id.walt.verifier.openid.models.openid.OpenID4VPResponseMode
import id.waltid.openid4vp.wallet.request.AuthorizationRequestResolver
import id.waltid.openid4vp.wallet.response.ResponseEncryptionHandler
import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive

/** OpenID4VP 1.0 Appendix A exchange protocol values. */
public enum class DcApiRequestProtocol(public val value: String) {
    OPENID4VP_V1_UNSIGNED("openid4vp-v1-unsigned"),
    OPENID4VP_V1_SIGNED("openid4vp-v1-signed"),
    OPENID4VP_V1_MULTISIGNED("openid4vp-v1-multisigned"),
    ;

    public companion object {
        public fun fromValue(value: String): DcApiRequestProtocol =
            entries.firstOrNull { it.value == value }
                ?: throw UnsupportedDcApiProtocolException(value)
    }
}

public class UnsupportedDcApiProtocolException(protocol: String) :
    IllegalArgumentException("Unsupported Digital Credentials API protocol: $protocol")

public class DcApiOriginMismatchException(
    origin: String,
) : IllegalArgumentException("The platform-asserted origin is not listed in expected_origins: $origin")

public class UnsupportedDcApiMultisignedRequestException : UnsupportedOperationException(
    "openid4vp-v1-multisigned requires JWS JSON Serialization verification, which is not available in this wallet runtime",
)

/**
 * A platform-authenticated DC API request after OpenID4VP request validation.
 *
 * [origin] is supplied by the operating system adapter and is never taken from request JSON.
 */
public data class ResolvedDcApiRequest(
    public val protocol: DcApiRequestProtocol,
    public val origin: String,
    public val authorizationRequest: AuthorizationRequest,
) {
    public val holderBindingAudience: String = "origin:$origin"
}

/** A DigitalCredential response returned to the operating system. */
@Serializable
public data class DcApiCredentialResponse(
    public val protocol: String,
    public val data: JsonObject,
)

/**
 * OpenID4VP 1.0 Appendix A request and response semantics independent of any OS API.
 */
public object DcApiWallet {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        explicitNulls = false
    }

    private const val ANDROID_APP_ORIGIN_PREFIX = "android:apk-key-hash:"

    /**
     * Resolves a DC API request using only the origin authenticated by the platform adapter.
     *
     * Unsigned requests ignore request-supplied `client_id` and `expected_origins`. Signed compact
     * JWS requests reuse the normal Request Object signature, audience, and client authentication
     * validation before enforcing an exact `expected_origins` match.
     */
    public suspend fun resolveRequest(
        protocol: String,
        data: JsonObject,
        origin: String,
        expectedRequestObjectAudience: String = "https://self-issued.me/v2",
        x509TrustPolicy: X509TrustPolicy? = null,
    ): ResolvedDcApiRequest {
        val validatedOrigin = validatePlatformOrigin(origin)
        val requestProtocol = DcApiRequestProtocol.fromValue(protocol)
        val authorizationRequest = when (requestProtocol) {
            DcApiRequestProtocol.OPENID4VP_V1_UNSIGNED -> resolveUnsignedRequest(data)
            DcApiRequestProtocol.OPENID4VP_V1_SIGNED -> resolveSignedRequest(
                data = data,
                expectedRequestObjectAudience = expectedRequestObjectAudience,
                x509TrustPolicy = x509TrustPolicy,
            )

            DcApiRequestProtocol.OPENID4VP_V1_MULTISIGNED ->
                throw UnsupportedDcApiMultisignedRequestException()
        }

        validateAuthorizationRequest(
            request = authorizationRequest,
            signed = requestProtocol != DcApiRequestProtocol.OPENID4VP_V1_UNSIGNED,
            origin = validatedOrigin,
        )
        return ResolvedDcApiRequest(requestProtocol, validatedOrigin, authorizationRequest)
    }

    /**
     * Builds the successful DigitalCredential payload. No network transport is performed.
     */
    public suspend fun buildResponse(
        request: ResolvedDcApiRequest,
        vpToken: String,
        idToken: String? = null,
        encryptionConfig: ResponseEncryptionHandler.EncryptionConfig? = null,
    ): DcApiCredentialResponse {
        val authorizationRequest = request.authorizationRequest
        val payload = buildJsonObject {
            put("vp_token", Json.parseToJsonElement(vpToken))
            idToken?.let { put("id_token", JsonPrimitive(it)) }
        }
        val data = when (authorizationRequest.responseMode) {
            OpenID4VPResponseMode.DC_API -> payload
            OpenID4VPResponseMode.DC_API_JWT -> {
                val encryption = requireNotNull(
                    encryptionConfig ?: ResponseEncryptionHandler
                        .extractEncryptionConfig(authorizationRequest)
                        .getOrThrow()
                ) { "response_mode=dc_api.jwt requires response encryption metadata" }
                buildJsonObject {
                    put("response", JsonPrimitive(ResponseEncryptionHandler.encryptResponse(payload, encryption)))
                }
            }

            else -> throw IllegalArgumentException(
                "DC API response builder cannot handle response_mode=${authorizationRequest.responseMode}",
            )
        }
        return DcApiCredentialResponse(request.protocol.value, data)
    }

    /** OpenID4VP protocol errors are fulfilled DC API responses, not transport failures. */
    public fun buildErrorResponse(
        protocol: DcApiRequestProtocol,
        error: WalletPresentFunctionality2.OID4VPErrorCode,
    ): DcApiCredentialResponse = DcApiCredentialResponse(
        protocol = protocol.value,
        data = buildJsonObject { put("error", JsonPrimitive(error.code)) },
    )

    public fun encodeResponse(response: DcApiCredentialResponse): String =
        json.encodeToString(DcApiCredentialResponse.serializer(), response)

    public fun validatePlatformOrigin(origin: String): String {
        require(origin.isNotBlank() && origin == origin.trim()) {
            "The platform-asserted origin must be a non-blank, canonical string"
        }
        if (origin.startsWith(ANDROID_APP_ORIGIN_PREFIX)) {
            require(
                origin.removePrefix(ANDROID_APP_ORIGIN_PREFIX)
                    .matches(Regex("[A-Za-z0-9_-]+")),
            ) {
                "Android app origin must contain one base64url signing-certificate hash"
            }
            return origin
        }

        val url = runCatching { Url(origin) }.getOrElse {
            throw IllegalArgumentException("The platform-asserted web origin is invalid", it)
        }
        require(url.protocol == URLProtocol.HTTPS || isLocalhostHttp(url)) {
            "The platform-asserted web origin must be HTTPS or an HTTP localhost origin"
        }
        require(
            url.user == null &&
                url.password == null &&
                url.fragment.isEmpty() &&
                url.parameters.isEmpty() &&
                (url.encodedPath.isEmpty() || url.encodedPath == "/")
        ) { "The platform-asserted origin must not contain credentials, a path, query, or fragment" }
        val canonicalOrigin = URLBuilder().apply {
            protocol = url.protocol
            host = url.host
            if (url.port != url.protocol.defaultPort) port = url.port
        }.buildString().removeSuffix("/")
        require(origin == canonicalOrigin) {
            "The platform-asserted web origin must use canonical scheme, host, and port serialization"
        }
        return origin
    }

    internal fun validateAuthorizationRequest(
        request: AuthorizationRequest,
        signed: Boolean,
        origin: String,
    ) {
        require(request.responseType?.responseType?.contains("vp_token") == true) {
            "DC API Authorization Request must request vp_token"
        }
        require(request.responseMode in OpenID4VPResponseMode.DC_API_RESPONSES) {
            "DC API Authorization Request response_mode must be dc_api or dc_api.jwt"
        }
        require(!request.nonce.isNullOrBlank()) { "DC API Authorization Request nonce is required" }
        require(request.dcqlQuery != null) { "DC API Authorization Request must contain dcql_query" }
        if (signed) {
            require(!request.clientId.isNullOrBlank()) {
                "Signed DC API Authorization Request client_id is required"
            }
            val expectedOrigins = request.expectedOrigins
            require(!expectedOrigins.isNullOrEmpty() && expectedOrigins.none(String::isBlank)) {
                "Signed DC API Authorization Request expected_origins must be a non-empty string array"
            }
            if (origin !in expectedOrigins) throw DcApiOriginMismatchException(origin)
        }
    }

    private fun resolveUnsignedRequest(data: JsonObject): AuthorizationRequest {
        require(data["request"] == null || data["request"] == JsonNull) {
            "openid4vp-v1-unsigned must contain request parameters, not a Request Object"
        }
        val effectiveData = JsonObject(
            data.filterKeys { it != "client_id" && it != "expected_origins" && it != "request" },
        )
        return json.decodeFromJsonElement(AuthorizationRequest.serializer(), effectiveData)
            .also { it.dcqlQuery?.precheck() }
    }

    private suspend fun resolveSignedRequest(
        data: JsonObject,
        expectedRequestObjectAudience: String,
        x509TrustPolicy: X509TrustPolicy?,
    ): AuthorizationRequest {
        val requestObject = data["request"]?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("openid4vp-v1-signed requires a compact JWS in data.request")
        require(requestObject.split('.').size == 3) {
            "openid4vp-v1-signed data.request must use JWS Compact Serialization"
        }
        // Appendix A carries only the Request Object; there is no outer Authorization Request
        // from which the generic resolver can read client_id. Extract the comparison value from
        // the untrusted payload, then let the resolver authenticate the signature, client-id
        // mechanism, audience, and the resulting inner/outer equality in one shared path.
        val requestObjectClientId = runCatching {
            requestObject.decodeJws().payload["client_id"]?.jsonPrimitive?.content
        }.getOrElse { cause ->
            throw IllegalArgumentException("Could not decode signed DC API Request Object", cause)
        } ?: throw IllegalArgumentException("Signed DC API Request Object client_id is required")

        val requestUrl = URLBuilder("openid4vp://authorize").apply {
            parameters.append("client_id", requestObjectClientId)
            parameters.append("request", requestObject)
        }.build()
        return AuthorizationRequestResolver.resolve(
            requestUrl = requestUrl,
            unsignedRequestObjectPolicy = AuthorizationRequestResolver.UnsignedRequestObjectPolicy.REQUIRE_SIGNED,
            expectedRequestObjectAudience = expectedRequestObjectAudience,
            x509TrustPolicy = x509TrustPolicy,
            fetchRequestUri = { _, _ -> error("request_uri is not a DC API request transport") },
        ).authorizationRequest
    }

    private fun isLocalhostHttp(url: Url): Boolean =
        url.protocol == URLProtocol.HTTP &&
            (url.host == "localhost" || url.host == "127.0.0.1" || url.host == "::1" || url.host.endsWith(".localhost"))
}
