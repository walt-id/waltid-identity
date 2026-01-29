package id.walt.openid4vci.metadata.oauth

import id.walt.openid4vci.GrantType
import id.walt.openid4vci.ResponseMode
import id.walt.openid4vci.ResponseType
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive


/**
 * OAuth 2.0 Authorization Server Metadata (RFC 8414).
 */
@Serializable(with = AuthorizationServerMetadataSerializer::class)
data class AuthorizationServerMetadata(
    @SerialName("issuer")
    val issuer: String,
    @SerialName("authorization_endpoint")
    val authorizationEndpoint: String? = null,
    @SerialName("token_endpoint")
    val tokenEndpoint: String? = null,
    @SerialName("jwks_uri")
    val jwksUri: String? = null,
    @SerialName("registration_endpoint")
    val registrationEndpoint: String? = null,
    @SerialName("scopes_supported")
    val scopesSupported: Set<String>? = null,
    @SerialName("response_types_supported")
    val responseTypesSupported: Set<String>,
    @SerialName("response_modes_supported")
    val responseModesSupported: Set<String>? = null,
    @SerialName("grant_types_supported")
    val grantTypesSupported: Set<String>? = null,
    @SerialName("token_endpoint_auth_methods_supported")
    val tokenEndpointAuthMethodsSupported: Set<String>? = null,
    @SerialName("token_endpoint_auth_signing_alg_values_supported")
    val tokenEndpointAuthSigningAlgValuesSupported: Set<String>? = null,
    @SerialName("pushed_authorization_request_endpoint")
    val pushedAuthorizationRequestEndpoint: String? = null,
    @SerialName("require_pushed_authorization_requests")
    val requirePushedAuthorizationRequests: Boolean? = null,
    @SerialName("service_documentation")
    val serviceDocumentation: String? = null,
    @SerialName("ui_locales_supported")
    val uiLocalesSupported: Set<String>? = null,
    @SerialName("op_policy_uri")
    val opPolicyUri: String? = null,
    @SerialName("op_tos_uri")
    val opTosUri: String? = null,
    @SerialName("revocation_endpoint")
    val revocationEndpoint: String? = null,
    @SerialName("revocation_endpoint_auth_methods_supported")
    val revocationEndpointAuthMethodsSupported: Set<String>? = null,
    @SerialName("revocation_endpoint_auth_signing_alg_values_supported")
    val revocationEndpointAuthSigningAlgValuesSupported: Set<String>? = null,
    @SerialName("introspection_endpoint")
    val introspectionEndpoint: String? = null,
    @SerialName("introspection_endpoint_auth_methods_supported")
    val introspectionEndpointAuthMethodsSupported: Set<String>? = null,
    @SerialName("introspection_endpoint_auth_signing_alg_values_supported")
    val introspectionEndpointAuthSigningAlgValuesSupported: Set<String>? = null,
    @SerialName("code_challenge_methods_supported")
    val codeChallengeMethodsSupported: List<String>? = null,
) {
    init {
        // RFC 8414 §2: issuer is REQUIRED
        require(issuer.isNotBlank()) {
            "Authorization server issuer must not be blank"
        }

        // RFC 8414 §2: issuer must be a valid https URL without query/fragment.
        validateIssuerUrl(issuer)

        // RFC 8414 §2: response_types_supported is REQUIRED.
        require(responseTypesSupported.isNotEmpty()) {
            "Authorization server response_types_supported is required"
        }
        require(responseTypesSupported.none { it.isBlank() }) {
            "Authorization server response_types_supported must not contain blank entries"
        }

        // RFC 8414 §3.2: claims with zero elements MUST be omitted (no empty arrays).
        requireNotEmptyIfPresent("scopes_supported", scopesSupported)
        requireNotEmptyIfPresent("response_modes_supported", responseModesSupported)
        requireNotEmptyIfPresent("grant_types_supported", grantTypesSupported)
        requireNotEmptyIfPresent("token_endpoint_auth_methods_supported", tokenEndpointAuthMethodsSupported)
        requireNotEmptyIfPresent(
            "token_endpoint_auth_signing_alg_values_supported",
            tokenEndpointAuthSigningAlgValuesSupported,
        )
        requireNotEmptyIfPresent("ui_locales_supported", uiLocalesSupported)
        requireNotEmptyIfPresent(
            "revocation_endpoint_auth_methods_supported",
            revocationEndpointAuthMethodsSupported,
        )
        requireNotEmptyIfPresent(
            "revocation_endpoint_auth_signing_alg_values_supported",
            revocationEndpointAuthSigningAlgValuesSupported,
        )
        requireNotEmptyIfPresent(
            "introspection_endpoint_auth_methods_supported",
            introspectionEndpointAuthMethodsSupported,
        )
        requireNotEmptyIfPresent(
            "introspection_endpoint_auth_signing_alg_values_supported",
            introspectionEndpointAuthSigningAlgValuesSupported,
        )
        requireNotEmptyIfPresent("code_challenge_methods_supported", codeChallengeMethodsSupported)

        // RFC 8414 §2: authorization_endpoint REQUIRED unless no grant type uses it.
        if (grantTypesSupported == null || grantTypesSupported.contains(GrantType.AuthorizationCode.value)) {
            require(!authorizationEndpoint.isNullOrBlank()) {
                "Authorization endpoint is required for the supported grant types"
            }
        }
        // RFC 8414 §2: token_endpoint REQUIRED unless only implicit is supported.
        if (
            grantTypesSupported == null ||
            grantTypesSupported.contains(GrantType.AuthorizationCode.value) ||
            grantTypesSupported.contains(GrantType.PreAuthorizedCode.value)
        ) {
            require(!tokenEndpoint.isNullOrBlank()) {
                "Token endpoint is required for the supported grant types"
            }
        }

        // RFC 8414 §2: Validate HTTPS scheme in the jwks_uri
        jwksUri?.let { validateHttpsUrl("jwks_uri", it) }
    }

    companion object {
        fun fromBaseUrl(
            baseUrl: String,
            authorizationEndpointPath: String = "/authorize",
            tokenEndpointPath: String = "/token",
            jwksUriPath: String = "/jwks",
            responseTypesSupported: Set<String> = setOf(ResponseType.CODE.value),
            responseModesSupported: Set<String>? = setOf(ResponseMode.QUERY.value, ResponseMode.FRAGMENT.value),
            grantTypesSupported: Set<String>? = setOf(
                GrantType.AuthorizationCode.value,
                GrantType.PreAuthorizedCode.value,
            ),
            requirePushedAuthorizationRequests: Boolean? = null,
            pushedAuthorizationRequestEndpointPath: String? = null,
        ): AuthorizationServerMetadata {
            val normalized = baseUrl.trimEnd('/')
            val parEndpoint = pushedAuthorizationRequestEndpointPath?.let { normalized + it }
            return AuthorizationServerMetadata(
                issuer = normalized,
                authorizationEndpoint = normalized + authorizationEndpointPath,
                tokenEndpoint = normalized + tokenEndpointPath,
                jwksUri = normalized + jwksUriPath,
                responseTypesSupported = responseTypesSupported,
                responseModesSupported = responseModesSupported,
                grantTypesSupported = grantTypesSupported,
                requirePushedAuthorizationRequests = requirePushedAuthorizationRequests,
                pushedAuthorizationRequestEndpoint = parEndpoint,
            )
        }

        private fun validateIssuerUrl(issuer: String) {
            val url = Url(issuer)
            require(url.protocol == URLProtocol.HTTPS) {
                "Authorization server issuer must use https scheme"
            }
            require(url.host.isNotBlank()) {
                "Authorization server issuer must include a host"
            }
            require(url.parameters.isEmpty()) {
                "Authorization server issuer must not include query parameters"
            }
            require(url.fragment.isEmpty()) {
                "Authorization server issuer must not include fragment components"
            }
        }

        private fun requireNotEmptyIfPresent(fieldName: String, values: Collection<*>?) {
            require(values == null || values.isNotEmpty()) {
                "Authorization server $fieldName must be omitted when empty"
            }
        }

        private fun validateHttpsUrl(fieldName: String, value: String) {
            val url = Url(value)
            require(url.protocol == URLProtocol.HTTPS) {
                "Authorization server $fieldName must use https scheme"
            }
            require(url.host.isNotBlank()) {
                "Authorization server $fieldName must include a host"
            }
            require(url.fragment.isEmpty()) {
                "Authorization server $fieldName must not include fragment components"
            }
        }
    }
}

internal object AuthorizationServerMetadataSerializer : KSerializer<AuthorizationServerMetadata> {
    override val descriptor = buildClassSerialDescriptor("AuthorizationServerMetadata") {}

    override fun serialize(encoder: Encoder, value: AuthorizationServerMetadata) {
        val jsonEncoder = encoder as? JsonEncoder
            ?: error("AuthorizationServerMetadataSerializer only supports JSON")
        val element = buildJsonObject {
            put("issuer", JsonPrimitive(value.issuer))
            value.authorizationEndpoint?.let { put("authorization_endpoint", JsonPrimitive(it)) }
            value.tokenEndpoint?.let { put("token_endpoint", JsonPrimitive(it)) }
            value.jwksUri?.let { put("jwks_uri", JsonPrimitive(it)) }
            put("response_types_supported", value.responseTypesSupported.toJsonArray())
            value.responseModesSupported?.takeIf { it.isNotEmpty() }
                ?.let { put("response_modes_supported", it.toJsonArray()) }
            value.tokenEndpointAuthMethodsSupported?.takeIf { it.isNotEmpty() }
                ?.let { put("token_endpoint_auth_methods_supported", it.toJsonArray()) }
            value.grantTypesSupported?.takeIf { it.isNotEmpty() }?.let { put("grant_types_supported", it.toJsonArray()) }
            value.tokenEndpointAuthSigningAlgValuesSupported?.takeIf { it.isNotEmpty() }
                ?.let { put("token_endpoint_auth_signing_alg_values_supported", it.toJsonArray()) }
            value.pushedAuthorizationRequestEndpoint?.let { put("pushed_authorization_request_endpoint", JsonPrimitive(it)) }
            value.registrationEndpoint?.let { put("registration_endpoint", JsonPrimitive(it)) }
            value.requirePushedAuthorizationRequests?.let { put("require_pushed_authorization_requests", JsonPrimitive(it)) }
            value.scopesSupported?.takeIf { it.isNotEmpty() }?.let { put("scopes_supported", it.toJsonArray()) }
            value.serviceDocumentation?.let { put("service_documentation", JsonPrimitive(it)) }
            value.uiLocalesSupported?.takeIf { it.isNotEmpty() }?.let { put("ui_locales_supported", it.toJsonArray()) }
            value.opPolicyUri?.let { put("op_policy_uri", JsonPrimitive(it)) }
            value.opTosUri?.let { put("op_tos_uri", JsonPrimitive(it)) }
            value.revocationEndpoint?.let { put("revocation_endpoint", JsonPrimitive(it)) }
            value.revocationEndpointAuthMethodsSupported?.takeIf { it.isNotEmpty() }
                ?.let { put("revocation_endpoint_auth_methods_supported", it.toJsonArray()) }
            value.revocationEndpointAuthSigningAlgValuesSupported?.takeIf { it.isNotEmpty() }
                ?.let { put("revocation_endpoint_auth_signing_alg_values_supported", it.toJsonArray()) }
            value.introspectionEndpoint?.let { put("introspection_endpoint", JsonPrimitive(it)) }
            value.introspectionEndpointAuthMethodsSupported?.takeIf { it.isNotEmpty() }
                ?.let { put("introspection_endpoint_auth_methods_supported", it.toJsonArray()) }
            value.introspectionEndpointAuthSigningAlgValuesSupported?.takeIf { it.isNotEmpty() }
                ?.let { put("introspection_endpoint_auth_signing_alg_values_supported", it.toJsonArray()) }
            value.codeChallengeMethodsSupported?.takeIf { it.isNotEmpty() }
                ?.let { put("code_challenge_methods_supported", it.toJsonArray()) }
        }
        jsonEncoder.encodeJsonElement(element)
    }

    override fun deserialize(decoder: Decoder): AuthorizationServerMetadata {
        val jsonDecoder = decoder as? JsonDecoder
            ?: error("AuthorizationServerMetadataSerializer only supports JSON")
        val element = jsonDecoder.decodeJsonElement().jsonObject
        val responseTypes = element.stringSet("response_types_supported")
            ?: error("Authorization server response_types_supported is required")
        return AuthorizationServerMetadata(
            issuer = element.string("issuer") ?: error("Authorization server issuer is required"),
            authorizationEndpoint = element.string("authorization_endpoint"),
            tokenEndpoint = element.string("token_endpoint"),
            jwksUri = element.string("jwks_uri"),
            registrationEndpoint = element.string("registration_endpoint"),
            scopesSupported = element.stringSet("scopes_supported"),
            responseTypesSupported = responseTypes,
            responseModesSupported = element.stringSet("response_modes_supported"),
            grantTypesSupported = element.stringSet("grant_types_supported"),
            tokenEndpointAuthMethodsSupported = element.stringSet("token_endpoint_auth_methods_supported"),
            tokenEndpointAuthSigningAlgValuesSupported =
                element.stringSet("token_endpoint_auth_signing_alg_values_supported"),
            pushedAuthorizationRequestEndpoint = element.string("pushed_authorization_request_endpoint"),
            requirePushedAuthorizationRequests = element.bool("require_pushed_authorization_requests"),
            serviceDocumentation = element.string("service_documentation"),
            uiLocalesSupported = element.stringSet("ui_locales_supported"),
            opPolicyUri = element.string("op_policy_uri"),
            opTosUri = element.string("op_tos_uri"),
            revocationEndpoint = element.string("revocation_endpoint"),
            revocationEndpointAuthMethodsSupported = element.stringSet("revocation_endpoint_auth_methods_supported"),
            revocationEndpointAuthSigningAlgValuesSupported =
                element.stringSet("revocation_endpoint_auth_signing_alg_values_supported"),
            introspectionEndpoint = element.string("introspection_endpoint"),
            introspectionEndpointAuthMethodsSupported = element.stringSet("introspection_endpoint_auth_methods_supported"),
            introspectionEndpointAuthSigningAlgValuesSupported =
                element.stringSet("introspection_endpoint_auth_signing_alg_values_supported"),
            codeChallengeMethodsSupported = element.stringList("code_challenge_methods_supported"),
        )
    }
}

private fun JsonObject.string(name: String): String? =
    this[name]?.jsonPrimitive?.contentOrNull

private fun JsonObject.bool(name: String): Boolean? =
    this[name]?.jsonPrimitive?.booleanOrNull

private fun JsonObject.stringSet(name: String): Set<String>? =
    this[name]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }?.toSet()

private fun JsonObject.stringList(name: String): List<String>? =
    this[name]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }

private fun Collection<String>.toJsonArray(): JsonArray =
    JsonArray(map { JsonPrimitive(it) })
