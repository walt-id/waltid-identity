package id.waltid.openid4vp.wallet

import id.walt.verifier.openid.models.authorization.AuthorizationRequest
import id.walt.verifier.openid.models.openid.OpenID4VPResponseMode
import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * OpenID4VP 1.0 Appendix A exchange protocol values accepted by this wallet.
 *
 * Only the unsigned protocol is implemented, so this enum has one entry. Any other protocol value —
 * including the `openid4vp-v1-signed` and `openid4vp-v1-multisigned` values defined by Appendix A —
 * fails closed with [UnsupportedDcApiProtocolException].
 */
public enum class DcApiRequestProtocol(public val value: String) {
    OPENID4VP_V1_UNSIGNED("openid4vp-v1-unsigned"),
    ;

    public companion object {
        public fun fromValue(value: String): DcApiRequestProtocol =
            entries.firstOrNull { it.value == value }
                ?: throw UnsupportedDcApiProtocolException(value)
    }
}

public class UnsupportedDcApiProtocolException(protocol: String) :
    IllegalArgumentException("Unsupported Digital Credentials API protocol: $protocol")

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

    /** base64url, unpadded - the encoding Credential Manager uses for the signing certificate hash. */
    private val ANDROID_APP_ORIGIN_HASH = Regex("[A-Za-z0-9_-]+")

    /**
     * Resolves a DC API request using only the origin authenticated by the platform adapter.
     *
     * Request-supplied `client_id` and `expected_origins` are ignored: the platform-asserted origin
     * is the sole requester identity for the unsigned protocol.
     */
    public fun resolveRequest(
        protocol: String,
        data: JsonObject,
        origin: String,
    ): ResolvedDcApiRequest {
        val validatedOrigin = canonicalizePlatformOrigin(origin)
        val requestProtocol = DcApiRequestProtocol.fromValue(protocol)
        val authorizationRequest = when (requestProtocol) {
            DcApiRequestProtocol.OPENID4VP_V1_UNSIGNED -> resolveUnsignedRequest(data)
        }

        validateAuthorizationRequest(authorizationRequest)
        return ResolvedDcApiRequest(requestProtocol, validatedOrigin, authorizationRequest)
    }

    /**
     * Builds the successful DigitalCredential payload. No network transport is performed.
     */
    public fun buildResponse(
        request: ResolvedDcApiRequest,
        vpToken: String,
        idToken: String? = null,
    ): DcApiCredentialResponse {
        val responseMode = request.authorizationRequest.responseMode
        require(responseMode == OpenID4VPResponseMode.DC_API) {
            "DC API response builder cannot handle response_mode=$responseMode"
        }
        return DcApiCredentialResponse(
            protocol = request.protocol.value,
            data = buildJsonObject {
                put("vp_token", Json.parseToJsonElement(vpToken))
                idToken?.let { put("id_token", JsonPrimitive(it)) }
            },
        )
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

    /**
     * The single origin canonicalizer for every DC API platform adapter.
     *
     * The result is hashed into the mdoc session transcript, which the verifier reconstructs from its
     * own `expected_origins` entry without ever receiving the wallet's copy. Two adapters that
     * normalize differently - one lowercasing the host, one not - therefore produce a device
     * signature no verifier can reproduce, and the failure surfaces as an opaque `device-auth`
     * rejection. So platform code must pass the raw OS-asserted value straight to this function and
     * use what it returns; it must not pre-normalize.
     *
     * @return the canonical origin: an Android app origin verbatim, or a web origin as
     *   `scheme://lowercased-host[:non-default-port]`.
     */
    public fun canonicalizePlatformOrigin(origin: String): String {
        require(origin.isNotBlank() && origin == origin.trim()) {
            "The platform-asserted origin must be a non-blank, untrimmed-free string"
        }
        if (origin.startsWith(ANDROID_APP_ORIGIN_PREFIX)) {
            require(origin.removePrefix(ANDROID_APP_ORIGIN_PREFIX).matches(ANDROID_APP_ORIGIN_HASH)) {
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
        // ktor preserves host case, so this lowercasing is what makes the result canonical rather
        // than merely validated. It is also why callers must not lowercase beforehand: an IPv6
        // literal keeps the brackets ktor's `host` reports, and `Url` would reject a mangled one.
        return URLBuilder().apply {
            protocol = url.protocol
            host = url.host.lowercase()
            if (url.port != url.protocol.defaultPort) port = url.port
        }.buildString().removeSuffix("/")
    }

    /**
     * Validates the Appendix A request members. The requester identity is not validated here: for
     * the unsigned protocol it is the platform-asserted origin alone, which [resolveRequest]
     * canonicalises, and no request claim is trusted against it.
     */
    internal fun validateAuthorizationRequest(request: AuthorizationRequest) {
        require(request.responseType?.responseType?.contains("vp_token") == true) {
            "DC API Authorization Request must request vp_token"
        }
        // Encrypted responses (response_mode=dc_api.jwt) are not implemented; reject them at
        // resolution time rather than after the user has already consented to disclosure.
        require(request.responseMode == OpenID4VPResponseMode.DC_API) {
            "DC API Authorization Request response_mode must be dc_api"
        }
        require(!request.nonce.isNullOrBlank()) { "DC API Authorization Request nonce is required" }
        require(request.dcqlQuery != null) { "DC API Authorization Request must contain dcql_query" }
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

    /**
     * Loopback origins browsers treat as secure contexts, so a local development verifier works.
     *
     * `[::1]` carries the brackets because that is how ktor's [Url.host] reports an IPv6 literal;
     * comparing against a bare `::1` silently never matches.
     */
    private fun isLocalhostHttp(url: Url): Boolean {
        if (url.protocol != URLProtocol.HTTP) return false
        val host = url.host.lowercase()
        return host == "localhost" || host == "127.0.0.1" || host == "[::1]" || host.endsWith(".localhost")
    }
}
