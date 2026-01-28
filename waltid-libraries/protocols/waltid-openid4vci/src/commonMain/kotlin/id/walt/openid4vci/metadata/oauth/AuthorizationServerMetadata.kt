package id.walt.openid4vci.metadata.oauth

import id.walt.openid4vci.GrantType
import id.walt.openid4vci.ResponseMode
import id.walt.openid4vci.ResponseType
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder


/**
 * OAuth 2.0 Authorization Server Metadata (RFC 8414).
 */
@OptIn(ExperimentalSerializationApi::class)
@KeepGeneratedSerializer
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

internal object AuthorizationServerMetadataSerializer : kotlinx.serialization.KSerializer<AuthorizationServerMetadata> {
    override val descriptor = AuthorizationServerMetadata.generatedSerializer().descriptor

    override fun serialize(encoder: kotlinx.serialization.encoding.Encoder, value: AuthorizationServerMetadata) {
        val jsonEncoder = encoder as? JsonEncoder
            ?: error("AuthorizationServerMetadataSerializer only supports JSON")
        val normalized = value.copy(
            scopesSupported = value.scopesSupported?.takeIf { it.isNotEmpty() },
            responseTypesSupported = value.responseTypesSupported,
            responseModesSupported = value.responseModesSupported?.takeIf { it.isNotEmpty() },
            grantTypesSupported = value.grantTypesSupported?.takeIf { it.isNotEmpty() },
            tokenEndpointAuthMethodsSupported = value.tokenEndpointAuthMethodsSupported?.takeIf { it.isNotEmpty() },
            tokenEndpointAuthSigningAlgValuesSupported =
                value.tokenEndpointAuthSigningAlgValuesSupported?.takeIf { it.isNotEmpty() },
            uiLocalesSupported = value.uiLocalesSupported?.takeIf { it.isNotEmpty() },
            revocationEndpointAuthMethodsSupported =
                value.revocationEndpointAuthMethodsSupported?.takeIf { it.isNotEmpty() },
            revocationEndpointAuthSigningAlgValuesSupported =
                value.revocationEndpointAuthSigningAlgValuesSupported?.takeIf { it.isNotEmpty() },
            introspectionEndpointAuthMethodsSupported =
                value.introspectionEndpointAuthMethodsSupported?.takeIf { it.isNotEmpty() },
            introspectionEndpointAuthSigningAlgValuesSupported =
                value.introspectionEndpointAuthSigningAlgValuesSupported?.takeIf { it.isNotEmpty() },
            codeChallengeMethodsSupported = value.codeChallengeMethodsSupported?.takeIf { it.isNotEmpty() },
        )
        val element: JsonElement = authorizationServerMetadataJson.encodeToJsonElement(
            AuthorizationServerMetadata.generatedSerializer(),
            normalized,
        )
        jsonEncoder.encodeJsonElement(element)
    }

    override fun deserialize(decoder: kotlinx.serialization.encoding.Decoder): AuthorizationServerMetadata {
        val jsonDecoder = decoder as? JsonDecoder
            ?: error("AuthorizationServerMetadataSerializer only supports JSON")
        val element = jsonDecoder.decodeJsonElement()
        return authorizationServerMetadataJson.decodeFromJsonElement(
            AuthorizationServerMetadata.generatedSerializer(),
            element,
        )
    }
}

private val authorizationServerMetadataJson = Json {
    encodeDefaults = false
    explicitNulls = false
}
