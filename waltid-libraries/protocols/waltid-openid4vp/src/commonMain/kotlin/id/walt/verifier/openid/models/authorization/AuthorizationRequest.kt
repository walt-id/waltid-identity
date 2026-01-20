package id.walt.verifier.openid.models.authorization

import id.walt.dcql.models.DcqlQuery
import id.walt.verifier.openid.models.openid.OpenID4VPResponseMode
import id.walt.verifier.openid.models.openid.OpenID4VPResponseType
import io.ktor.http.*
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

/**
 * Represents an OpenID4VP Authorization Request.
 * Compliant with OpenID for Verifiable Presentations - draft 28.
 * See: Section 5 of the specification.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class AuthorizationRequest(
    // OAuth 2.0 Parameters (Section 5 and 5.2)
    /**
     * REQUIRED. OAuth 2.0 Response Type value.
     * For OpenID4VP, common values are "vp_token", "vp_token id_token", "code".
     *
     * Is "vp_token" in 95% of cases ("id_token" requires SIOPv2, "code" requires wallet to have HTTP endpoint (backend) accessible by Verifier)
     */
    @SerialName("response_type")
    @EncodeDefault
    val responseType: OpenID4VPResponseType? = OpenID4VPResponseType.VP_TOKEN,

    /**
     * REQUIRED. OAuth 2.0 Client Identifier.
     * May include a Client Identifier Prefix as per Section 5.9.
     */
    @SerialName("client_id")
    val clientId: String? = null,

    /**
     * Conditional. OAuth 2.0 Redirection URI.
     * REQUIRED for flows that involve a redirect back to the Verifier (e.g., response_mode=fragment/query, or after code exchange).
     */
    @SerialName("redirect_uri")
    val redirectUri: String? = null,

    /**
     * OPTIONAL. OAuth 2.0 Scope value.
     * Can be used for pre-defined DCQL queries or OpenID Connect scopes (e.g., "openid").
     */
    val scope: String? = null,

    /**
     * RECOMMENDED. Opaque value used by the Verifier to maintain state between the request and callback.
     * REQUIRED under conditions defined in Section 5.3 (Requesting Presentations without Holder Binding Proofs).
     */
    val state: String? = null,

    /**
     * OPTIONAL. OAuth 2.0 Response Mode.
     * Specifies how the Authorization Response is to be returned.
     * E.g., "fragment", "query", "direct_post", "direct_post.jwt", "dc_api", "dc_api.jwt".
     */
    @SerialName("response_mode")
    var responseMode: OpenID4VPResponseMode? = null,

    /**
     * REQUIRED. String value used to mitigate replay attacks.
     * See Section 5.2 and 14.1.
     *
     * Also used to establish holder binding.
     */
    val nonce: String? = null,

    // JAR (RFC 9101) Parameters (Section 5)
    /**
     * OPTIONAL. The Authorization Request parameters are represented as a JWT [RFC7519].
     * If present, this JWT contains all other Authorization Request parameters as claims.
     */
    val request: String? = null, // This would be the compact JWT string

    /**
     * OPTIONAL. A URL that points to a JWT [RFC7519] containing the Authorization Request parameters.
     */
    @SerialName("request_uri")
    val requestUri: String? = null,

    /**
     * When `response_mode` is `DIRECT_POST` or `DIRECT_POST_JWT`, the `response_uri` MUST be present in the
     * Authorization Request (as per Section 5.1 of draft 28 for `response_uri` and Section 8.2 for `direct_post`).
     */
    @SerialName("response_uri")
    val responseUri: String? = null,

    // OpenID4VP New Parameters (Section 5.1)
    /**
     * REQUIRED (unless 'scope' parameter represents a DCQL Query).
     * A JSON object containing a DCQL query as defined in Section 6.
     */
    @SerialName("dcql_query")
    val dcqlQuery: DcqlQuery? = null, // Made nullable to support scope-defined queries

    /**
     * OPTIONAL. A JSON object containing the Verifier metadata values.
     * See Section 5.1 for defined sub-parameters like 'jwks', 'vp_formats_supported'.
     */
    @SerialName("client_metadata")
    val clientMetadata: ClientMetadata? = null,

    /**
     * OPTIONAL. A string determining the HTTP method to be used when 'request_uri' is present.
     * Valid values: "get", "post". Defaults to "get" if not present.
     * MUST NOT be present if 'request_uri' is not present.
     */
    @SerialName("request_uri_method")
    val requestUriMethod: RequestUriHttpMethod? = null,

    /**
     * OPTIONAL. Array of strings, where each string is a base64url encoded JSON object
     * containing details about the transaction the Verifier is requesting the End-User to authorize.
     * The decoded JSON object structure is represented by [TransactionDataItem].
     */
    @SerialName("transaction_data")
    val transactionData: List<String>? = null, // List of base64url encoded JSON strings

    /**
     * OPTIONAL. An array of attestations about the Verifier relevant to the Credential Request.
     * Each object structure is represented by [VerifierInfoItem].
     */
    @SerialName("verifier_info")
    val verifierInfo: List<VerifierInfoItem>? = null,

    // SIOPv2 specific parameters (if scope includes "openid") - common but technically from SIOPv2
    /**
     * OPTIONAL (but common with SIOPv2). Specifies the type of ID Token the RP wants.
     * E.g., "subject_signed", "attester_signed".
     */
    @SerialName("id_token_type")
    val idTokenType: String? = null,

    // DC API specific parameter (Appendix A.2 of draft 28)
    /**
     * REQUIRED when signed requests (Appendix A.3.2) are used with the Digital Credentials API (DC API).
     * An array of strings, each string representing an Origin of the Verifier that is making the request.
     * Not for use in unsigned requests.
     */
    @SerialName("expected_origins")
    val expectedOrigins: List<String>? = null,
) {

    fun toHttpUrl(url: URLBuilder = URLBuilder("openid4vp://authorize")): Url {
        val json = Json { encodeDefaults = false }
        val values = json.encodeToJsonElement(this).jsonObject
            .entries
            .filterNot { it.value == JsonNull }

        values.forEach { (key, value) ->
            val paramValue = when (value) {
                is JsonPrimitive -> value.content
                is JsonObject -> json.encodeToString(JsonObject.serializer(), value)
                is JsonArray -> json.encodeToString(JsonArray.serializer(), value)
                else -> value.toString()
            }
            url.parameters.append(key, paramValue)
        }
        return url.build()
    }


    init {
        // Basic validation based on spec requirements
        if (requestUri == null && requestUriMethod != null) {
            throw IllegalArgumentException("request_uri_method MUST NOT be present if request_uri is not present.")
        }
        if (scope == null && dcqlQuery == null) {
            // The spec says "Either a dcql_query or a scope parameter representing a DCQL Query MUST be present"
            // This check is a simplification; a scope might not always represent a DCQL query.
            // More robust validation would check if the specific scope value is known to map to a DCQL query.
            // For now, we'll assume one of them must be non-null.
            // throw IllegalArgumentException("Either dcql_query or a scope representing a DCQL Query MUST be present.")
            // Relaxing this for now as scope's meaning can be broad.
        }
        if (requestUriMethod != null && requestUriMethod.method !in listOf("get", "post")) {
            throw IllegalArgumentException("request_uri_method must be 'get' or 'post'.")
        }
        if (responseMode in OpenID4VPResponseMode.DIRECT_POST_RESPONSES) {
            requireNotNull(responseUri) { "response_uri must not be null if response_mode is direct_post / direct_post_jwt" }
        }
    }
}
