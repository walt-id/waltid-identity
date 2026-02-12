package id.walt.openid4vci.metadata.oidc

import id.walt.openid4vci.GrantType
import id.walt.openid4vci.ResponseMode
import id.walt.openid4vci.ResponseType
import io.ktor.http.Url
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
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
 * OpenID Provider Metadata (OpenID Connect Discovery 1.0).
 */
@OptIn(ExperimentalSerializationApi::class)
@KeepGeneratedSerializer
@Serializable(with = OpenIDProviderMetadataSerializer::class)
data class OpenIDProviderMetadata(
    @SerialName("issuer")
    val issuer: String,
    @SerialName("authorization_endpoint")
    val authorizationEndpoint: String,
    @SerialName("token_endpoint")
    val tokenEndpoint: String,
    @SerialName("jwks_uri")
    val jwksUri: String,
    @SerialName("userinfo_endpoint")
    val userinfoEndpoint: String? = null,
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
    @SerialName("acr_values_supported")
    val acrValuesSupported: Set<String>? = null,
    @SerialName("subject_types_supported")
    val subjectTypesSupported: Set<String>,
    @SerialName("id_token_signing_alg_values_supported")
    val idTokenSigningAlgValuesSupported: Set<String>,
    @SerialName("id_token_encryption_alg_values_supported")
    val idTokenEncryptionAlgValuesSupported: Set<String>? = null,
    @SerialName("id_token_encryption_enc_values_supported")
    val idTokenEncryptionEncValuesSupported: Set<String>? = null,
    @SerialName("userinfo_signing_alg_values_supported")
    val userinfoSigningAlgValuesSupported: Set<String>? = null,
    @SerialName("userinfo_encryption_alg_values_supported")
    val userinfoEncryptionAlgValuesSupported: Set<String>? = null,
    @SerialName("userinfo_encryption_enc_values_supported")
    val userinfoEncryptionEncValuesSupported: Set<String>? = null,
    @SerialName("request_object_signing_alg_values_supported")
    val requestObjectSigningAlgValuesSupported: Set<String>? = null,
    @SerialName("request_object_encryption_alg_values_supported")
    val requestObjectEncryptionAlgValuesSupported: Set<String>? = null,
    @SerialName("request_object_encryption_enc_values_supported")
    val requestObjectEncryptionEncValuesSupported: Set<String>? = null,
    @SerialName("token_endpoint_auth_methods_supported")
    val tokenEndpointAuthMethodsSupported: Set<String>? = null,
    @SerialName("token_endpoint_auth_signing_alg_values_supported")
    val tokenEndpointAuthSigningAlgValuesSupported: Set<String>? = null,
    @SerialName("display_values_supported")
    val displayValuesSupported: Set<String>? = null,
    @SerialName("claim_types_supported")
    val claimTypesSupported: Set<String>? = null,
    @SerialName("claims_supported")
    val claimsSupported: Set<String>? = null,
    @SerialName("service_documentation")
    val serviceDocumentation: String? = null,
    @SerialName("claims_locales_supported")
    val claimsLocalesSupported: Set<String>? = null,
    @SerialName("ui_locales_supported")
    val uiLocalesSupported: Set<String>? = null,
    @SerialName("claims_parameter_supported")
    val claimsParameterSupported: Boolean? = null,
    @SerialName("request_parameter_supported")
    val requestParameterSupported: Boolean? = null,
    @SerialName("request_uri_parameter_supported")
    val requestUriParameterSupported: Boolean? = null,
    @SerialName("require_request_uri_registration")
    val requireRequestUriRegistration: Boolean? = null,
    @SerialName("op_policy_uri")
    val opPolicyUri: String? = null,
    @SerialName("op_tos_uri")
    val opTosUri: String? = null,
    val customParameters: Map<String, JsonElement>? = null,
) {
    init {
        // OpenID Connect Discovery §3: issuer is REQUIRED and must be a URL without query/fragment.
        require(issuer.isNotBlank()) { "OpenID issuer must not be blank" }
        validateIssuerUrl(issuer)
        validateEndpointUrl("authorization_endpoint", authorizationEndpoint)
        validateEndpointUrl("token_endpoint", tokenEndpoint)
        validateEndpointUrl("jwks_uri", jwksUri)
        userinfoEndpoint?.let { validateEndpointUrl("userinfo_endpoint", it) }
        registrationEndpoint?.let { validateEndpointUrl("registration_endpoint", it) }

        require(responseTypesSupported.isNotEmpty()) {
            "OpenID response_types_supported must not be empty"
        }
        require(responseTypesSupported.none { it.isBlank() }) {
            "OpenID response_types_supported must not contain blank entries"
        }

        require(subjectTypesSupported.isNotEmpty()) {
            "OpenID subject_types_supported must not be empty"
        }
        require(subjectTypesSupported.none { it.isBlank() }) {
            "OpenID subject_types_supported must not contain blank entries"
        }
        require(subjectTypesSupported.all { it == "pairwise" || it == "public" }) {
            "OpenID subject_types_supported must only include \"pairwise\" or \"public\""
        }

        require(idTokenSigningAlgValuesSupported.isNotEmpty()) {
            "OpenID id_token_signing_alg_values_supported must not be empty"
        }
        require(idTokenSigningAlgValuesSupported.none { it.isBlank() }) {
            "OpenID id_token_signing_alg_values_supported must not contain blank entries"
        }
        require(idTokenSigningAlgValuesSupported.contains("RS256")) {
            "OpenID id_token_signing_alg_values_supported must include RS256"
        }

        // OpenID Connect Discovery §3: claims with zero elements MUST be omitted.
        requireNotEmptyIfPresent("scopes_supported", scopesSupported)
        requireNotEmptyIfPresent("response_modes_supported", responseModesSupported)
        requireNotEmptyIfPresent("grant_types_supported", grantTypesSupported)
        requireNotEmptyIfPresent("acr_values_supported", acrValuesSupported)
        requireNotEmptyIfPresent("id_token_encryption_alg_values_supported", idTokenEncryptionAlgValuesSupported)
        requireNotEmptyIfPresent("id_token_encryption_enc_values_supported", idTokenEncryptionEncValuesSupported)
        requireNotEmptyIfPresent("userinfo_signing_alg_values_supported", userinfoSigningAlgValuesSupported)
        requireNotEmptyIfPresent("userinfo_encryption_alg_values_supported", userinfoEncryptionAlgValuesSupported)
        requireNotEmptyIfPresent("userinfo_encryption_enc_values_supported", userinfoEncryptionEncValuesSupported)
        requireNotEmptyIfPresent("request_object_signing_alg_values_supported", requestObjectSigningAlgValuesSupported)
        requireNotEmptyIfPresent(
            "request_object_encryption_alg_values_supported",
            requestObjectEncryptionAlgValuesSupported
        )
        requireNotEmptyIfPresent(
            "request_object_encryption_enc_values_supported",
            requestObjectEncryptionEncValuesSupported
        )
        requireNotEmptyIfPresent("token_endpoint_auth_methods_supported", tokenEndpointAuthMethodsSupported)
        requireNotEmptyIfPresent(
            "token_endpoint_auth_signing_alg_values_supported",
            tokenEndpointAuthSigningAlgValuesSupported
        )
        requireNotEmptyIfPresent("display_values_supported", displayValuesSupported)
        requireNotEmptyIfPresent("claim_types_supported", claimTypesSupported)
        requireNotEmptyIfPresent("claims_supported", claimsSupported)
        requireNotEmptyIfPresent("claims_locales_supported", claimsLocalesSupported)
        requireNotEmptyIfPresent("ui_locales_supported", uiLocalesSupported)

        scopesSupported?.let { scopes ->
            require(scopes.contains("openid")) {
                "OpenID scopes_supported must include openid when present"
            }
        }

        tokenEndpointAuthSigningAlgValuesSupported?.let { algorithms ->
            require(!algorithms.contains("none")) {
                "OpenID token_endpoint_auth_signing_alg_values_supported must not include none"
            }
        }

        tokenEndpointAuthMethodsSupported?.let { methods ->
            val requiresSigningAlgs = methods.contains("private_key_jwt") || methods.contains("client_secret_jwt")
            require(!requiresSigningAlgs || !tokenEndpointAuthSigningAlgValuesSupported.isNullOrEmpty()) {
                "OpenID token_endpoint_auth_signing_alg_values_supported is required when using JWT auth methods"
            }
        }

        customParameters?.let { params ->
            require(params.keys.none { it in OpenIDProviderMetadataSerializer.knownKeys }) {
                "customParameters must not override standard OpenID provider metadata fields"
            }
        }
    }

    companion object {
        fun fromBaseUrl(
            baseUrl: String,
            authorizationEndpointPath: String = "/authorize",
            tokenEndpointPath: String = "/token",
            jwksUriPath: String = "/jwks",
            scopesSupported: Set<String>? = setOf("openid"),
            responseTypesSupported: Set<String> = setOf(ResponseType.CODE.value),
            responseModesSupported: Set<String>? = setOf(ResponseMode.QUERY.value, ResponseMode.FRAGMENT.value),
            grantTypesSupported: Set<String>? = setOf(
                GrantType.AuthorizationCode.value,
            ),
            subjectTypesSupported: Set<String> = setOf("public"),
            idTokenSigningAlgValuesSupported: Set<String> = setOf("RS256"),
        ): OpenIDProviderMetadata {
            val normalized = baseUrl.trimEnd('/')
            return OpenIDProviderMetadata(
                issuer = normalized,
                authorizationEndpoint = normalized + authorizationEndpointPath,
                tokenEndpoint = normalized + tokenEndpointPath,
                jwksUri = normalized + jwksUriPath,
                scopesSupported = scopesSupported,
                responseTypesSupported = responseTypesSupported,
                responseModesSupported = responseModesSupported,
                grantTypesSupported = grantTypesSupported,
                subjectTypesSupported = subjectTypesSupported,
                idTokenSigningAlgValuesSupported = idTokenSigningAlgValuesSupported,
            )
        }

        private fun validateIssuerUrl(issuer: String) {
            val url = Url(issuer)
            require(url.host.isNotBlank()) {
                "OpenID issuer must include a host"
            }
            require(url.parameters.isEmpty()) {
                "OpenID issuer must not include query parameters"
            }
            require(url.fragment.isEmpty()) {
                "OpenID issuer must not include fragment components"
            }
        }

        private fun validateEndpointUrl(fieldName: String, value: String) {
            require(value.isNotBlank()) {
                "OpenID $fieldName must not be blank"
            }
            val url = Url(value)
            require(url.host.isNotBlank()) {
                "OpenID $fieldName must include a host"
            }
        }

        private fun requireNotEmptyIfPresent(fieldName: String, values: Collection<*>?) {
            require(values == null || values.isNotEmpty()) {
                "OpenID $fieldName must be omitted when empty"
            }
        }
    }
}

internal object OpenIDProviderMetadataSerializer : KSerializer<OpenIDProviderMetadata> {
    override val descriptor = OpenIDProviderMetadata.generatedSerializer().descriptor

    internal val knownKeys =
        descriptor.elementNames
            .filter { it != "customParameters" }
            .toSet()

    override fun serialize(encoder: Encoder, value: OpenIDProviderMetadata) {
        val jsonEncoder = encoder as? JsonEncoder
            ?: error("OpenIDProviderMetadataSerializer only supports JSON")
        val base = buildJsonObject {
            put("issuer", JsonPrimitive(value.issuer))
            put("authorization_endpoint", JsonPrimitive(value.authorizationEndpoint))
            put("token_endpoint", JsonPrimitive(value.tokenEndpoint))
            put("jwks_uri", JsonPrimitive(value.jwksUri))
            value.userinfoEndpoint?.let { put("userinfo_endpoint", JsonPrimitive(it)) }
            value.registrationEndpoint?.let { put("registration_endpoint", JsonPrimitive(it)) }
            value.scopesSupported?.takeIf { it.isNotEmpty() }?.let { put("scopes_supported", it.toJsonArray()) }
            put("response_types_supported", value.responseTypesSupported.toJsonArray())
            value.responseModesSupported?.takeIf { it.isNotEmpty() }
                ?.let { put("response_modes_supported", it.toJsonArray()) }
            value.grantTypesSupported?.takeIf { it.isNotEmpty() }
                ?.let { put("grant_types_supported", it.toJsonArray()) }
            value.acrValuesSupported?.takeIf { it.isNotEmpty() }?.let { put("acr_values_supported", it.toJsonArray()) }
            put("subject_types_supported", value.subjectTypesSupported.toJsonArray())
            put("id_token_signing_alg_values_supported", value.idTokenSigningAlgValuesSupported.toJsonArray())
            value.idTokenEncryptionAlgValuesSupported?.takeIf { it.isNotEmpty() }
                ?.let { put("id_token_encryption_alg_values_supported", it.toJsonArray()) }
            value.idTokenEncryptionEncValuesSupported?.takeIf { it.isNotEmpty() }
                ?.let { put("id_token_encryption_enc_values_supported", it.toJsonArray()) }
            value.userinfoSigningAlgValuesSupported?.takeIf { it.isNotEmpty() }
                ?.let { put("userinfo_signing_alg_values_supported", it.toJsonArray()) }
            value.userinfoEncryptionAlgValuesSupported?.takeIf { it.isNotEmpty() }
                ?.let { put("userinfo_encryption_alg_values_supported", it.toJsonArray()) }
            value.userinfoEncryptionEncValuesSupported?.takeIf { it.isNotEmpty() }
                ?.let { put("userinfo_encryption_enc_values_supported", it.toJsonArray()) }
            value.requestObjectSigningAlgValuesSupported?.takeIf { it.isNotEmpty() }
                ?.let { put("request_object_signing_alg_values_supported", it.toJsonArray()) }
            value.requestObjectEncryptionAlgValuesSupported?.takeIf { it.isNotEmpty() }
                ?.let { put("request_object_encryption_alg_values_supported", it.toJsonArray()) }
            value.requestObjectEncryptionEncValuesSupported?.takeIf { it.isNotEmpty() }
                ?.let { put("request_object_encryption_enc_values_supported", it.toJsonArray()) }
            value.tokenEndpointAuthMethodsSupported?.takeIf { it.isNotEmpty() }
                ?.let { put("token_endpoint_auth_methods_supported", it.toJsonArray()) }
            value.tokenEndpointAuthSigningAlgValuesSupported?.takeIf { it.isNotEmpty() }
                ?.let { put("token_endpoint_auth_signing_alg_values_supported", it.toJsonArray()) }
            value.displayValuesSupported?.takeIf { it.isNotEmpty() }
                ?.let { put("display_values_supported", it.toJsonArray()) }
            value.claimTypesSupported?.takeIf { it.isNotEmpty() }
                ?.let { put("claim_types_supported", it.toJsonArray()) }
            value.claimsSupported?.takeIf { it.isNotEmpty() }
                ?.let { put("claims_supported", it.toJsonArray()) }
            value.serviceDocumentation?.let { put("service_documentation", JsonPrimitive(it)) }
            value.claimsLocalesSupported?.takeIf { it.isNotEmpty() }
                ?.let { put("claims_locales_supported", it.toJsonArray()) }
            value.uiLocalesSupported?.takeIf { it.isNotEmpty() }
                ?.let { put("ui_locales_supported", it.toJsonArray()) }
            value.claimsParameterSupported?.let { put("claims_parameter_supported", JsonPrimitive(it)) }
            value.requestParameterSupported?.let { put("request_parameter_supported", JsonPrimitive(it)) }
            value.requestUriParameterSupported?.let { put("request_uri_parameter_supported", JsonPrimitive(it)) }
            value.requireRequestUriRegistration?.let { put("require_request_uri_registration", JsonPrimitive(it)) }
            value.opPolicyUri?.let { put("op_policy_uri", JsonPrimitive(it)) }
            value.opTosUri?.let { put("op_tos_uri", JsonPrimitive(it)) }
        }
        val merged = value.customParameters?.let { extras ->
            buildJsonObject {
                base.forEach { (key, jsonValue) -> put(key, jsonValue) }
                extras.forEach { (key, jsonValue) -> put(key, jsonValue) }
            }
        } ?: base
        jsonEncoder.encodeJsonElement(merged)
    }

    override fun deserialize(decoder: Decoder): OpenIDProviderMetadata {
        val jsonDecoder = decoder as? JsonDecoder
            ?: error("OpenIDProviderMetadataSerializer only supports JSON")
        val element = jsonDecoder.decodeJsonElement().jsonObject
        val responseTypes = element.stringSet("response_types_supported")
            ?: error("OpenID response_types_supported is required")
        val subjectTypes = element.stringSet("subject_types_supported")
            ?: error("OpenID subject_types_supported is required")
        val idTokenSigningAlgorithms = element.stringSet("id_token_signing_alg_values_supported")
            ?: error("OpenID id_token_signing_alg_values_supported is required")
        val customParameters = element.filterKeys { it !in knownKeys }.takeIf { it.isNotEmpty() }
        return OpenIDProviderMetadata(
            issuer = element.string("issuer") ?: error("OpenID issuer is required"),
            authorizationEndpoint = element.string("authorization_endpoint")
                ?: error("OpenID authorization_endpoint is required"),
            tokenEndpoint = element.string("token_endpoint")
                ?: error("OpenID token_endpoint is required"),
            jwksUri = element.string("jwks_uri")
                ?: error("OpenID jwks_uri is required"),
            userinfoEndpoint = element.string("userinfo_endpoint"),
            registrationEndpoint = element.string("registration_endpoint"),
            scopesSupported = element.stringSet("scopes_supported"),
            responseTypesSupported = responseTypes,
            responseModesSupported = element.stringSet("response_modes_supported"),
            grantTypesSupported = element.stringSet("grant_types_supported"),
            acrValuesSupported = element.stringSet("acr_values_supported"),
            subjectTypesSupported = subjectTypes,
            idTokenSigningAlgValuesSupported = idTokenSigningAlgorithms,
            idTokenEncryptionAlgValuesSupported = element.stringSet("id_token_encryption_alg_values_supported"),
            idTokenEncryptionEncValuesSupported = element.stringSet("id_token_encryption_enc_values_supported"),
            userinfoSigningAlgValuesSupported = element.stringSet("userinfo_signing_alg_values_supported"),
            userinfoEncryptionAlgValuesSupported = element.stringSet("userinfo_encryption_alg_values_supported"),
            userinfoEncryptionEncValuesSupported = element.stringSet("userinfo_encryption_enc_values_supported"),
            requestObjectSigningAlgValuesSupported = element.stringSet("request_object_signing_alg_values_supported"),
            requestObjectEncryptionAlgValuesSupported = element.stringSet("request_object_encryption_alg_values_supported"),
            requestObjectEncryptionEncValuesSupported = element.stringSet("request_object_encryption_enc_values_supported"),
            tokenEndpointAuthMethodsSupported = element.stringSet("token_endpoint_auth_methods_supported"),
            tokenEndpointAuthSigningAlgValuesSupported = element.stringSet("token_endpoint_auth_signing_alg_values_supported"),
            displayValuesSupported = element.stringSet("display_values_supported"),
            claimTypesSupported = element.stringSet("claim_types_supported"),
            claimsSupported = element.stringSet("claims_supported"),
            serviceDocumentation = element.string("service_documentation"),
            claimsLocalesSupported = element.stringSet("claims_locales_supported"),
            uiLocalesSupported = element.stringSet("ui_locales_supported"),
            claimsParameterSupported = element.bool("claims_parameter_supported"),
            requestParameterSupported = element.bool("request_parameter_supported"),
            requestUriParameterSupported = element.bool("request_uri_parameter_supported"),
            requireRequestUriRegistration = element.bool("require_request_uri_registration"),
            opPolicyUri = element.string("op_policy_uri"),
            opTosUri = element.string("op_tos_uri"),
            customParameters = customParameters,
        )
    }
}

private fun JsonObject.string(name: String): String? =
    this[name]?.jsonPrimitive?.contentOrNull

private fun JsonObject.bool(name: String): Boolean? =
    this[name]?.jsonPrimitive?.booleanOrNull

private fun JsonObject.stringSet(name: String): Set<String>? =
    this[name]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }?.toSet()

private fun Collection<String>.toJsonArray(): JsonArray =
    JsonArray(map { JsonPrimitive(it) })
