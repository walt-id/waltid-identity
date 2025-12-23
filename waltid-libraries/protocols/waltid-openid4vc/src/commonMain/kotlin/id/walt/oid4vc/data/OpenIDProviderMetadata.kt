package id.walt.oid4vc.data

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*

/**
 * OpenID Provider metadata object, according to
 * https://openid.net/specs/openid-connect-discovery-1_0.html,
 * https://openid.net/specs/openid-4-verifiable-credential-issuance-1_0.html#name-metadata
 * https://datatracker.ietf.org/doc/html/draft-lodderstedt-oauth-par-00
 * @param authorizationEndpoint REQUIRED. URL of the OP's OAuth 2.0 Authorization Endpoint [OpenID.Core].
 * @param tokenEndpoint URL of the OP's OAuth 2.0 Token Endpoint [OpenID.Core]. This is REQUIRED unless only the Implicit Flow is used.
 * @param jwksUri REQUIRED. URL of the OP's JSON Web Key Set [JWK] document. This contains the signing key(s) the RP uses to validate signatures from the OP. The JWK Set MAY also contain the Server's encryption key(s), which are used by RPs to encrypt requests to the Server. When both signing and encryption keys are made available, a use (Key Use) parameter value is REQUIRED for all keys in the referenced JWK Set to indicate each key's intended usage. Although some algorithms allow the same key to be used for both signatures and encryption, doing so is NOT RECOMMENDED, as it is less secure. The JWK x5c parameter MAY be used to provide X.509 representations of keys provided. When used, the bare key values MUST still be present and MUST match those in the certificate.
 * @param pushedAuthorizationRequestEndpoint The URL of the pushed authorization request endpoint at which the client can exchange a request object for a request URI. (https://datatracker.ietf.org/doc/html/draft-lodderstedt-oauth-par-00)
 * @param userinfoEndpoint RECOMMENDED. URL of the OP's UserInfo Endpoint [OpenID.Core]. This URL MUST use the https scheme and MAY contain port, path, and query parameter components.
 * @param registrationEndpoint RECOMMENDED. URL of the OP's Dynamic Client Registration Endpoint [OpenID.Registration].
 * @param issuer REQUIRED. URL using the https scheme with no query or fragment component that the OP asserts as its Issuer Identifier. If Issuer discovery is supported (see Section 2), this value MUST be identical to the issuer value returned by WebFinger. This also MUST be identical to the iss Claim value in ID Tokens issued from this Issuer.
 * @param scopesSupported RECOMMENDED. JSON array containing a list of the OAuth 2.0 [RFC6749] scope values that this server supports. The server MUST support the openid scope value. Servers MAY choose not to advertise some supported scope values even when this parameter is used, although those defined in [OpenID.Core] SHOULD be listed, if supported.
 * @param responseTypesSupported REQUIRED. JSON array containing a list of the OAuth 2.0 response_type values that this OP supports. Dynamic OpenID Providers MUST support the code, id_token, and the token id_token Response Type values.
 * @param responseModesSupported OPTIONAL. JSON array containing a list of the OAuth 2.0 response_mode values that this OP supports, as specified in OAuth 2.0 Multiple Response Type Encoding Practices [OAuth.Responses]. If omitted, the default for Dynamic OpenID Providers is ["query", "fragment"].
 * @param grantTypesSupported OPTIONAL. JSON array containing a list of the OAuth 2.0 Grant Type values that this OP supports. Dynamic OpenID Providers MUST support the authorization_code and implicit Grant Type values and MAY support other Grant Types. If omitted, the default value is ["authorization_code", "implicit"]. For support of the pre-authorized OID4VCI flow, add "urn:ietf:params:oauth:grant-type:pre-authorized_code"
 * @param acrValuesSupported OPTIONAL. JSON array containing a list of the Authentication Context Class References that this OP supports.
 * @param subjectTypesSupported REQUIRED. JSON array containing a list of the Subject Identifier types that this OP supports. Valid types include pairwise and public.
 * @param credentialIssuer REQUIRED. The Credential Issuer's identifier.
 * @param credentialEndpoint REQUIRED. URL of the Credential Issuer's Credential Endpoint. This URL MUST use the https scheme and MAY contain port, path and query parameter components.
 * @param credentialsSupported REQUIRED. A JSON array containing a list of JSON objects, each of them representing metadata about a separate credential type that the Credential Issuer can issue. The JSON objects in the array MUST conform to the structure of the Section 10.2.3.1.
 * @param batchCredentialEndpoint OPTIONAL. URL of the Credential Issuer's Batch Credential Endpoint. This URL MUST use the https scheme and MAY contain port, path and query parameter components. If omitted, the Credential Issuer does not support the Batch Credential Endpoint.
 * @param authorizationServer OPTIONAL. Identifier of the OAuth 2.0 Authorization Server (as defined in [RFC8414]) the Credential Issuer relies on for authorization. If this element is omitted, the entity providing the Credential Issuer is also acting as the AS, i.e. the Credential Issuer's identifier is used as the OAuth 2.0 Issuer value to obtain the Authorization Server metadata as per [RFC8414].
 * @param display OPTIONAL. An array of objects, where each object contains display properties of a Credential Issuer for a certain language
 * @param idTokenSigningAlgValuesSupported REQUIRED. JSON array containing a list of the JWS signing algorithms (alg values) supported by the OP for the ID Token to encode the Claims in a JWT [JWT]. The algorithm RS256 MUST be included. The value none MAY be supported, but MUST NOT be used unless the Response Type used returns no ID Token from the Authorization Endpoint (such as when using the Authorization Code Flow).
 * @param idTokenEncryptionAlgValuesSupported OPTIONAL. JSON array containing a list of the JWE encryption algorithms (alg values) supported by the OP for the ID Token to encode the Claims in a JWT [JWT].
 * @param idTokenEncryptionEncValuesSupported OPTIONAL. JSON array containing a list of the JWE encryption algorithms (enc values) supported by the OP for the ID Token to encode the Claims in a JWT [JWT].
 * @param userinfoSigningAlgValuesSupported OPTIONAL. JSON array containing a list of the JWS [JWS] signing algorithms (alg values) [JWA] supported by the UserInfo Endpoint to encode the Claims in a JWT [JWT]. The value none MAY be included.
 * @param userinfoEncryptionAlgValuesSupported OPTIONAL. JSON array containing a list of the JWE [JWE] encryption algorithms (alg values) [JWA] supported by the UserInfo Endpoint to encode the Claims in a JWT [JWT].
 * @param userinfoEncryptionEncValuesSupported OPTIONAL. JSON array containing a list of the JWE encryption algorithms (enc values) [JWA] supported by the UserInfo Endpoint to encode the Claims in a JWT [JWT].
 * @param requestObjectSigningAlgValuesSupported OPTIONAL. JSON array containing a list of the JWS signing algorithms (alg values) supported by the OP for Request Objects, which are described in Section 6.1 of OpenID Connect Core 1.0 [OpenID.Core]. These algorithms are used both when the Request Object is passed by value (using the request parameter) and when it is passed by reference (using the request_uri parameter). Servers SHOULD support none and RS256.
 * @param requestObjectEncryptionAlgValuesSupported OPTIONAL. JSON array containing a list of the JWE encryption algorithms (alg values) supported by the OP for Request Objects. These algorithms are used both when the Request Object is passed by value and when it is passed by reference.
 * @param requestObjectEncryptionEncValuesSupported OPTIONAL. JSON array containing a list of the JWE encryption algorithms (enc values) supported by the OP for Request Objects. These algorithms are used both when the Request Object is passed by value and when it is passed by reference.
 * @param tokenEndpointAuthMethodsSupported OPTIONAL. JSON array containing a list of Client Authentication methods supported by this Token Endpoint. The options are client_secret_post, client_secret_basic, client_secret_jwt, and private_key_jwt, as described in Section 9 of OpenID Connect Core 1.0 [OpenID.Core]. Other authentication methods MAY be defined by extensions. If omitted, the default is client_secret_basic -- the HTTP Basic Authentication Scheme specified in Section 2.3.1 of OAuth 2.0 [RFC6749].
 * @param tokenEndpointAuthSigningAlgValuesSupported OPTIONAL. JSON array containing a list of the JWS signing algorithms (alg values) supported by the Token Endpoint for the signature on the JWT [JWT] used to authenticate the Client at the Token Endpoint for the private_key_jwt and client_secret_jwt authentication methods. Servers SHOULD support RS256. The value none MUST NOT be used.
 * @param displayValuesSupported OPTIONAL. JSON array containing a list of the display parameter values that the OpenID Provider supports. These values are described in Section 3.1.2.1 of OpenID Connect Core 1.0 [OpenID.Core].
 * @param claimTypesSupported OPTIONAL. JSON array containing a list of the Claim Types that the OpenID Provider supports. These Claim Types are described in Section 5.6 of OpenID Connect Core 1.0 [OpenID.Core]. Values defined by this specification are normal, aggregated, and distributed. If omitted, the implementation supports only normal Claims.
 * @param claimsSupported RECOMMENDED. JSON array containing a list of the Claim Names of the Claims that the OpenID Provider MAY be able to supply values for. Note that for privacy or other reasons, this might not be an exhaustive list.
 * @param serviceDocumentation OPTIONAL. URL of a page containing human-readable information that developers might want or need to know when using the OpenID Provider. In particular, if the OpenID Provider does not support Dynamic Client Registration, then information on how to register Clients needs to be provided in this documentation.
 * @param claimsLocalesSupported OPTIONAL. Languages and scripts supported for values in Claims being returned, represented as a JSON array of BCP47 [RFC5646] language tag values. Not all languages and scripts are necessarily supported for all Claim values.
 * @param uiLocalesSupported OPTIONAL. Languages and scripts supported for the user interface, represented as a JSON array of BCP47 [RFC5646] language tag values.
 * @param claimsParameterSupported OPTIONAL. Boolean value specifying whether the OP supports use of the claims parameter, with true indicating support. If omitted, the default value is false.
 * @param requestParameterSupported OPTIONAL. Boolean value specifying whether the OP supports use of the request parameter, with true indicating support. If omitted, the default value is false.
 * @param requestUriParameterSupported OPTIONAL. Boolean value specifying whether the OP supports use of the request_uri parameter, with true indicating support. If omitted, the default value is true.
 * @param requireRequestUriRegistration OPTIONAL. Boolean value specifying whether the OP requires any request_uri values used to be pre-registered using the request_uris registration parameter. Pre-registration is REQUIRED when the value is true. If omitted, the default value is false.
 * @param opPolicyUri OPTIONAL. URL that the OpenID Provider provides to the person registering the Client to read about the OP's requirements on how the Relying Party can use the data provided by the OP. The registration process SHOULD display this URL to the person registering the Client if it is given.
 * @param opTosUri OPTIONAL. URL that the OpenID Provider provides to the person registering the Client to read about OpenID Provider's terms of service. The registration process SHOULD display this URL to the person registering the Client if it is given.
 */
@Serializable(with = OpenIDProviderMetadataSerializer::class)
sealed class OpenIDProviderMetadata : JsonDataObject() {
    abstract val issuer: String?
    abstract val authorizationEndpoint: String?
    abstract val pushedAuthorizationRequestEndpoint: String?
    abstract val tokenEndpoint: String?
    abstract val userinfoEndpoint: String?
    abstract val jwksUri: String?
    abstract val registrationEndpoint: String?
    abstract val scopesSupported: Set<String>
    abstract val responseTypesSupported: Set<String>?
    abstract val responseModesSupported: Set<ResponseMode>
    abstract val grantTypesSupported: Set<GrantType>
    abstract val acrValuesSupported: Set<String>?
    abstract val subjectTypesSupported: Set<SubjectType>?
    abstract val idTokenSigningAlgValuesSupported: Set<String>?
    abstract val idTokenEncryptionAlgValuesSupported: Set<String>?
    abstract val idTokenEncryptionEncValuesSupported: Set<String>?
    abstract val userinfoSigningAlgValuesSupported: Set<String>?
    abstract val userinfoEncryptionAlgValuesSupported: Set<String>?
    abstract val userinfoEncryptionEncValuesSupported: Set<String>?
    abstract val requestObjectSigningAlgValuesSupported: Set<String>?
    abstract val requestObjectEncryptionAlgValuesSupported: Set<String>?
    abstract val requestObjectEncryptionEncValuesSupported: Set<String>?
    abstract val tokenEndpointAuthMethodsSupported: Set<String>?
    abstract val tokenEndpointAuthSigningAlgValuesSupported: Set<String>?
    abstract val displayValuesSupported: Set<String>?
    abstract val claimTypesSupported: Set<String>?
    abstract val claimsSupported: Set<String>?
    abstract val serviceDocumentation: String?
    abstract val claimsLocalesSupported: Set<String>?
    abstract val uiLocalesSupported: Set<String>?
    abstract val claimsParameterSupported: Boolean
    abstract val requestParameterSupported: Boolean
    abstract val requestUriParameterSupported: Boolean
    abstract val requireRequestUriRegistration: Boolean
    abstract val opPolicyUri: String?
    abstract val opTosUri: String?
    abstract val revocationEndpoint: String?
    abstract val revocationEndpointAuthMethodsSupported: Set<String>?
    abstract val revocationEndpointAuthSigningAlgValuesSupported: Set<String>?
    abstract val introspectionEndpoint: String?
    abstract val introspectionEndpointAuthMethodsSupported: Set<String>?
    abstract val introspectionEndpointAuthSigningAlgValuesSupported: Set<String>?
    abstract val codeChallengeMethodsSupported: List<String>?


    // OID4VCI Draft 11 and Draft 13 properties
    abstract val credentialIssuer: String?
    abstract val credentialEndpoint: String?
    abstract val batchCredentialEndpoint: String?
    abstract val deferredCredentialEndpoint: String?
    abstract val display: List<DisplayProperties>?
    abstract val presentationDefinitionUriSupported: Boolean?
    abstract val clientIdSchemesSupported: List<String>?
    abstract val requirePushedAuthorizationRequests: Boolean?
    abstract val dpopSigningAlgValuesSupported: Set<String>?

    @Transient
    val draft11: Draft11?
        inline get() = this as? Draft11

    @Transient
    val draft13: Draft13?
        inline get() = this as? Draft13

    @OptIn(ExperimentalSerializationApi::class)
    @KeepGeneratedSerializer
    @Serializable(with = Draft11OpenIDProviderMetadataSerializer::class)
    data class Draft11(
        @SerialName("issuer") override val issuer: String? = null,
        @SerialName("authorization_endpoint") override val authorizationEndpoint: String? = null,
        @SerialName("pushed_authorization_request_endpoint") override val pushedAuthorizationRequestEndpoint: String? = null,
        @SerialName("token_endpoint") override val tokenEndpoint: String? = null,
        @SerialName("userinfo_endpoint") override val userinfoEndpoint: String? = null,
        @SerialName("jwks_uri") override val jwksUri: String? = null,
        @SerialName("registration_endpoint") override val registrationEndpoint: String? = null,
        @EncodeDefault @SerialName("scopes_supported") override val scopesSupported: Set<String> = setOf("openid"),
        @SerialName("response_types_supported") override val responseTypesSupported: Set<String>? = null,
        @EncodeDefault @SerialName("response_modes_supported") override val responseModesSupported: Set<ResponseMode> = setOf(
            ResponseMode.query,
            ResponseMode.fragment
        ),
        @EncodeDefault @SerialName("grant_types_supported") @Serializable(GrantTypeSetSerializer::class) override val grantTypesSupported: Set<GrantType> = setOf(
            GrantType.authorization_code,
            GrantType.pre_authorized_code
        ),
        @SerialName("acr_values_supported") override val acrValuesSupported: Set<String>? = null,
        @SerialName("subject_types_supported") override val subjectTypesSupported: Set<SubjectType>? = null,
        @SerialName("id_token_signing_alg_values_supported") override val idTokenSigningAlgValuesSupported: Set<String>? = null,
        @SerialName("id_token_encryption_alg_values_supported") override val idTokenEncryptionAlgValuesSupported: Set<String>? = null,
        @SerialName("id_token_encryption_enc_values_supported") override val idTokenEncryptionEncValuesSupported: Set<String>? = null,
        @SerialName("userinfo_signing_alg_values_supported") override val userinfoSigningAlgValuesSupported: Set<String>? = null,
        @SerialName("userinfo_encryption_alg_values_supported") override val userinfoEncryptionAlgValuesSupported: Set<String>? = null,
        @SerialName("userinfo_encryption_enc_values_supported") override val userinfoEncryptionEncValuesSupported: Set<String>? = null,
        @SerialName("request_object_signing_alg_values_supported") override val requestObjectSigningAlgValuesSupported: Set<String>? = null,
        @SerialName("request_object_encryption_alg_values_supported") override val requestObjectEncryptionAlgValuesSupported: Set<String>? = null,
        @SerialName("request_object_encryption_enc_values_supported") override val requestObjectEncryptionEncValuesSupported: Set<String>? = null,
        @SerialName("token_endpoint_auth_methods_supported") override val tokenEndpointAuthMethodsSupported: Set<String>? = null,
        @SerialName("token_endpoint_auth_signing_alg_values_supported") override val tokenEndpointAuthSigningAlgValuesSupported: Set<String>? = null,
        @SerialName("display_values_supported") override val displayValuesSupported: Set<String>? = null,
        @SerialName("claim_types_supported") override val claimTypesSupported: Set<String>? = null,
        @SerialName("claims_supported") override val claimsSupported: Set<String>? = null,
        @SerialName("service_documentation") override val serviceDocumentation: String? = null,
        @SerialName("claims_locales_supported") override val claimsLocalesSupported: Set<String>? = null,
        @SerialName("ui_locales_supported") override val uiLocalesSupported: Set<String>? = null,
        @SerialName("claims_parameter_supported") override val claimsParameterSupported: Boolean = false,
        @SerialName("request_parameter_supported") override val requestParameterSupported: Boolean = false,
        @SerialName("request_uri_parameter_supported") override val requestUriParameterSupported: Boolean = true,
        @SerialName("require_request_uri_registration") override val requireRequestUriRegistration: Boolean = false,
        @SerialName("op_policy_uri") override val opPolicyUri: String? = null,
        @SerialName("op_tos_uri") override val opTosUri: String? = null,
        @SerialName("code_challenge_methods_supported") override val codeChallengeMethodsSupported: List<String>? = null,
        @SerialName("revocation_endpoint") override val revocationEndpoint: String? = null,
        @SerialName("revocation_endpoint_auth_methods_supported") override val revocationEndpointAuthMethodsSupported: Set<String>? = null,
        @SerialName("revocation_endpoint_auth_signing_alg_values_supported") override val revocationEndpointAuthSigningAlgValuesSupported: Set<String>? = null,
        @SerialName("introspection_endpoint") override val introspectionEndpoint: String? = null,
        @SerialName("introspection_endpoint_auth_methods_supported") override val introspectionEndpointAuthMethodsSupported: Set<String>? = null,
        @SerialName("introspection_endpoint_auth_signing_alg_values_supported") override val introspectionEndpointAuthSigningAlgValuesSupported: Set<String>? = null,


        // OID4VCI properties
        @SerialName("credential_issuer") override val credentialIssuer: String? = null,
        @SerialName("credential_endpoint") override val credentialEndpoint: String? = null,
        @SerialName("batch_credential_endpoint") override val batchCredentialEndpoint: String? = null,
        @SerialName("deferred_credential_endpoint") override val deferredCredentialEndpoint: String? = null,
        @SerialName("display") @Serializable(DisplayPropertiesListSerializer::class) override val display: List<DisplayProperties>? = null,
        @SerialName("presentation_definition_uri_supported") override val presentationDefinitionUriSupported: Boolean? = null,
        @SerialName("client_id_schemes_supported") override val clientIdSchemesSupported: List<String>? = null,
        @SerialName("require_pushed_authorization_requests") override val requirePushedAuthorizationRequests: Boolean? = null,
        @SerialName("dpop_signing_alg_values_supported") override val dpopSigningAlgValuesSupported: Set<String>? = null,

        // OID4VCI 11
        @SerialName("credentials_supported") @Serializable(CredentialSupportedArraySerializer::class) val credentialSupported: Map<String, CredentialSupported>? = null,
        @SerialName("authorization_server") val authorizationServer: String? = null,

        override val customParameters: Map<String, JsonElement>? = mapOf()

    ) : OpenIDProviderMetadata() {

        override fun toJSON(): JsonObject =
            Json.encodeToJsonElement(Draft11OpenIDProviderMetadataSerializer, this).jsonObject

        companion object {

            /**
             * Factory method is needed, because a transformation of credentialSupported (set id in credential)
             * needs to be done.
             */
            fun create(
                issuer: String? = null,
                authorizationEndpoint: String? = null,
                pushedAuthorizationRequestEndpoint: String? = null,
                tokenEndpoint: String? = null,
                userinfoEndpoint: String? = null,
                jwksUri: String? = null,
                registrationEndpoint: String? = null,
                scopesSupported: Set<String> = setOf("openid"),
                responseTypesSupported: Set<String>? = null,
                responseModesSupported: Set<ResponseMode> = setOf(
                    ResponseMode.query,
                    ResponseMode.fragment
                ),
                grantTypesSupported: Set<GrantType> = setOf(
                    GrantType.authorization_code,
                    GrantType.pre_authorized_code
                ),
                acrValuesSupported: Set<String>? = null,
                subjectTypesSupported: Set<SubjectType>? = null,
                idTokenSigningAlgValuesSupported: Set<String>? = null,
                idTokenEncryptionAlgValuesSupported: Set<String>? = null,
                idTokenEncryptionEncValuesSupported: Set<String>? = null,
                userinfoSigningAlgValuesSupported: Set<String>? = null,
                userinfoEncryptionAlgValuesSupported: Set<String>? = null,
                userinfoEncryptionEncValuesSupported: Set<String>? = null,
                requestObjectSigningAlgValuesSupported: Set<String>? = null,
                requestObjectEncryptionAlgValuesSupported: Set<String>? = null,
                requestObjectEncryptionEncValuesSupported: Set<String>? = null,
                tokenEndpointAuthMethodsSupported: Set<String>? = null,
                tokenEndpointAuthSigningAlgValuesSupported: Set<String>? = null,
                displayValuesSupported: Set<String>? = null,
                claimTypesSupported: Set<String>? = null,
                claimsSupported: Set<String>? = null,
                serviceDocumentation: String? = null,
                claimsLocalesSupported: Set<String>? = null,
                uiLocalesSupported: Set<String>? = null,
                claimsParameterSupported: Boolean = false,
                requestParameterSupported: Boolean = false,
                requestUriParameterSupported: Boolean = true,
                requireRequestUriRegistration: Boolean = false,
                opPolicyUri: String? = null,
                opTosUri: String? = null,
                codeChallengeMethodsSupported: List<String>? = null,
                revocationEndpoint: String? = null,
                revocationEndpointAuthMethodsSupported: Set<String>? = null,
                revocationEndpointAuthSigningAlgValuesSupported: Set<String>? = null,
                introspectionEndpoint: String? = null,
                introspectionEndpointAuthMethodsSupported: Set<String>? = null,
                introspectionEndpointAuthSigningAlgValuesSupported: Set<String>? = null,

                // OID4VCI properties
                credentialIssuer: String? = null,
                credentialEndpoint: String? = null,
                batchCredentialEndpoint: String? = null,
                deferredCredentialEndpoint: String? = null,
                display: List<DisplayProperties>? = null,
                presentationDefinitionUriSupported: Boolean? = null,
                clientIdSchemesSupported: List<String>? = null,
                requirePushedAuthorizationRequests: Boolean? = null,
                dpopSigningAlgValuesSupported: Set<String>? = null,

                // OID4VCI 11
                credentialSupported: Map<String, CredentialSupported>? = null,
                authorizationServer: String? = null,

                customParameters: Map<String, JsonElement>? = mapOf()


            ): Draft11 {
                return Draft11(
                    issuer,
                    authorizationEndpoint,
                    pushedAuthorizationRequestEndpoint,
                    tokenEndpoint,
                    userinfoEndpoint,
                    jwksUri,
                    registrationEndpoint,
                    scopesSupported,
                    responseTypesSupported,
                    responseModesSupported,
                    grantTypesSupported,
                    acrValuesSupported,
                    subjectTypesSupported,
                    idTokenSigningAlgValuesSupported,
                    idTokenEncryptionAlgValuesSupported,
                    idTokenEncryptionEncValuesSupported,
                    userinfoSigningAlgValuesSupported,
                    userinfoEncryptionAlgValuesSupported,
                    userinfoEncryptionEncValuesSupported,
                    requestObjectSigningAlgValuesSupported,
                    requestObjectEncryptionAlgValuesSupported,
                    requestObjectEncryptionEncValuesSupported,
                    tokenEndpointAuthMethodsSupported,
                    tokenEndpointAuthSigningAlgValuesSupported,
                    displayValuesSupported,
                    claimTypesSupported,
                    claimsSupported,
                    serviceDocumentation,
                    claimsLocalesSupported,
                    uiLocalesSupported,
                    claimsParameterSupported,
                    requestParameterSupported,
                    requestUriParameterSupported,
                    requireRequestUriRegistration,
                    opPolicyUri,
                    opTosUri,
                    codeChallengeMethodsSupported,
                    revocationEndpoint,
                    revocationEndpointAuthMethodsSupported,
                    revocationEndpointAuthSigningAlgValuesSupported,
                    introspectionEndpoint,
                    introspectionEndpointAuthMethodsSupported,
                    introspectionEndpointAuthSigningAlgValuesSupported,

                    // OID4VCI properties
                    credentialIssuer,
                    credentialEndpoint,
                    batchCredentialEndpoint,
                    deferredCredentialEndpoint,
                    display,
                    presentationDefinitionUriSupported,
                    clientIdSchemesSupported,
                    requirePushedAuthorizationRequests,
                    dpopSigningAlgValuesSupported,

                    // OID4VCI 11
                    credentialSupported?.map { (id, cs) ->
                        id to when (cs.id.isNullOrEmpty()) {
                            true -> CredentialSupported.withId(id, cs)
                            else -> cs
                        }
                    }?.toMap(),
                    authorizationServer,

                    customParameters
                )
            }
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    @KeepGeneratedSerializer
    @Serializable(with = Draft13OpenIDProviderMetadataSerializer::class)
    data class Draft13(
        @SerialName("issuer") override val issuer: String? = null,
        @SerialName("authorization_endpoint") override val authorizationEndpoint: String? = null,
        @SerialName("pushed_authorization_request_endpoint") override val pushedAuthorizationRequestEndpoint: String? = null,
        @SerialName("token_endpoint") override val tokenEndpoint: String? = null,
        @SerialName("userinfo_endpoint") override val userinfoEndpoint: String? = null,
        @SerialName("jwks_uri") override val jwksUri: String? = null,
        @SerialName("registration_endpoint") override val registrationEndpoint: String? = null,
        @EncodeDefault @SerialName("scopes_supported") override val scopesSupported: Set<String> = setOf("openid", "org.iso.23220.photoid.1"),
        @SerialName("response_types_supported") override val responseTypesSupported: Set<String>? = null,
        @EncodeDefault @SerialName("response_modes_supported") override val responseModesSupported: Set<ResponseMode> = setOf(
            ResponseMode.query,
            ResponseMode.fragment
        ),
        @EncodeDefault @SerialName("grant_types_supported") @Serializable(GrantTypeSetSerializer::class) override val grantTypesSupported: Set<GrantType> = setOf(
            GrantType.authorization_code,
            GrantType.pre_authorized_code
        ),
        @SerialName("acr_values_supported") override val acrValuesSupported: Set<String>? = null,
        @SerialName("subject_types_supported") override val subjectTypesSupported: Set<SubjectType>? = null,
        @SerialName("id_token_signing_alg_values_supported") override val idTokenSigningAlgValuesSupported: Set<String>? = null,
        @SerialName("id_token_encryption_alg_values_supported") override val idTokenEncryptionAlgValuesSupported: Set<String>? = null,
        @SerialName("id_token_encryption_enc_values_supported") override val idTokenEncryptionEncValuesSupported: Set<String>? = null,
        @SerialName("userinfo_signing_alg_values_supported") override val userinfoSigningAlgValuesSupported: Set<String>? = null,
        @SerialName("userinfo_encryption_alg_values_supported") override val userinfoEncryptionAlgValuesSupported: Set<String>? = null,
        @SerialName("userinfo_encryption_enc_values_supported") override val userinfoEncryptionEncValuesSupported: Set<String>? = null,
        @SerialName("request_object_signing_alg_values_supported") override val requestObjectSigningAlgValuesSupported: Set<String>? = null,
        @SerialName("request_object_encryption_alg_values_supported") override val requestObjectEncryptionAlgValuesSupported: Set<String>? = null,
        @SerialName("request_object_encryption_enc_values_supported") override val requestObjectEncryptionEncValuesSupported: Set<String>? = null,
        @SerialName("token_endpoint_auth_methods_supported") override val tokenEndpointAuthMethodsSupported: Set<String>? = null,
        @SerialName("token_endpoint_auth_signing_alg_values_supported") override val tokenEndpointAuthSigningAlgValuesSupported: Set<String>? = null,
        @SerialName("display_values_supported") override val displayValuesSupported: Set<String>? = null,
        @SerialName("claim_types_supported") override val claimTypesSupported: Set<String>? = null,
        @SerialName("claims_supported") override val claimsSupported: Set<String>? = null,
        @SerialName("service_documentation") override val serviceDocumentation: String? = null,
        @SerialName("claims_locales_supported") override val claimsLocalesSupported: Set<String>? = null,
        @SerialName("ui_locales_supported") override val uiLocalesSupported: Set<String>? = null,
        @SerialName("claims_parameter_supported") override val claimsParameterSupported: Boolean = false,
        @SerialName("request_parameter_supported") override val requestParameterSupported: Boolean = false,
        @SerialName("request_uri_parameter_supported") override val requestUriParameterSupported: Boolean = true,
        @SerialName("require_request_uri_registration") override val requireRequestUriRegistration: Boolean = false,
        @SerialName("op_policy_uri") override val opPolicyUri: String? = null,
        @SerialName("op_tos_uri") override val opTosUri: String? = null,
        @SerialName("require_pushed_authorization_requests") override val requirePushedAuthorizationRequests: Boolean? = null,
        @SerialName("code_challenge_methods_supported") override val codeChallengeMethodsSupported: List<String>? = null,
        @SerialName("revocation_endpoint") override val revocationEndpoint: String? = null,
        @SerialName("revocation_endpoint_auth_methods_supported") override val revocationEndpointAuthMethodsSupported: Set<String>? = null,
        @SerialName("revocation_endpoint_auth_signing_alg_values_supported") override val revocationEndpointAuthSigningAlgValuesSupported: Set<String>? = null,
        @SerialName("introspection_endpoint") override val introspectionEndpoint: String? = null,
        @SerialName("introspection_endpoint_auth_methods_supported") override val introspectionEndpointAuthMethodsSupported: Set<String>? = null,
        @SerialName("introspection_endpoint_auth_signing_alg_values_supported") override val introspectionEndpointAuthSigningAlgValuesSupported: Set<String>? = null,


        // OID4VCI properties
        @SerialName("credential_issuer") override val credentialIssuer: String? = null,
        @SerialName("credential_endpoint") override val credentialEndpoint: String? = null,
        @SerialName("batch_credential_endpoint") override val batchCredentialEndpoint: String? = null,
        @SerialName("deferred_credential_endpoint") override val deferredCredentialEndpoint: String? = null,
        @SerialName("display") @Serializable(DisplayPropertiesListSerializer::class) override val display: List<DisplayProperties>? = null,
        @SerialName("presentation_definition_uri_supported") override val presentationDefinitionUriSupported: Boolean? = null,
        @SerialName("client_id_schemes_supported") override val clientIdSchemesSupported: List<String>? = null,
        @SerialName("dpop_signing_alg_values_supported") override val dpopSigningAlgValuesSupported: Set<String>? = null,

        // OID4VCI 13
        @SerialName("credential_configurations_supported") @Serializable(CredentialSupportedMapSerializer::class) val credentialConfigurationsSupported: Map<String, CredentialSupported>? = null,
        @SerialName("authorization_servers") val authorizationServers: Set<String>? = null,
        @SerialName("pre-authorized_grant_anonymous_access_supported") val preAuthorizedGrantAnonymousAccessSupport: Boolean? = null,
        @SerialName("nonce_endpoint") val nonceEndpoint: String? = null,

        override val customParameters: Map<String, JsonElement>? = mapOf()

    ) : OpenIDProviderMetadata() {
        fun getVctByCredentialConfigurationId(credentialConfigurationId: String) =
            credentialConfigurationsSupported?.get(credentialConfigurationId)?.vct

        fun getVctBySupportedCredentialConfiguration(
            baseUrl: String,
            credType: String
        ): CredentialSupported {
            val expectedVct = "$baseUrl/$credType"

            credentialConfigurationsSupported?.entries?.forEach { entry ->
                if (getVctByCredentialConfigurationId(entry.key) == expectedVct) {
                    return entry.value
                }
            }

            throw IllegalArgumentException("Invalid type value: $credType. The $credType type is not supported")
        }

        override fun toJSON(): JsonObject {
            return Json.encodeToJsonElement(Draft13OpenIDProviderMetadataSerializer, this).jsonObject
        }
    }

    companion object : JsonDataObjectFactory<OpenIDProviderMetadata>() {
        override fun fromJSON(jsonObject: JsonObject): OpenIDProviderMetadata =
            Json.decodeFromJsonElement(OpenIDProviderMetadataSerializer, jsonObject)
    }
}

object OpenIDProviderMetadataSerializer : KSerializer<OpenIDProviderMetadata> {

    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("id.walt.oid4vc.data.OpenIDProviderMetadata") {}

    override fun serialize(encoder: Encoder, value: OpenIDProviderMetadata) {
        when (value) {
            is OpenIDProviderMetadata.Draft11 -> Draft11OpenIDProviderMetadataSerializer.serialize(encoder, value)
            is OpenIDProviderMetadata.Draft13 -> Draft13OpenIDProviderMetadataSerializer.serialize(encoder, value)
        }
    }

    override fun deserialize(decoder: Decoder): OpenIDProviderMetadata {
        val jsonDecoder = decoder as? JsonDecoder
            ?: throw IllegalStateException("This serializer can only be used with a JSON decoder")

        val rawJsonElement = jsonDecoder.decodeJsonElement()

        return when {
            "credentials_supported" in rawJsonElement.jsonObject -> Json.decodeFromJsonElement(
                Draft11OpenIDProviderMetadataSerializer,
                rawJsonElement
            )

            else -> Json.decodeFromJsonElement(
                Draft13OpenIDProviderMetadataSerializer,
                rawJsonElement
            )
        }
    }
}

fun OpenIDProviderMetadata.getSupportedProofTypes(): List<ProofType>? {
    return when (this) {
        is OpenIDProviderMetadata.Draft11 -> credentialSupported?.values?.flatMap {
            it.proofTypesSupported?.keys ?: emptyList()
        }

        is OpenIDProviderMetadata.Draft13 -> credentialConfigurationsSupported?.values?.flatMap {
            it.proofTypesSupported?.keys ?: emptyList()
        }
    }
}

private object Draft11OpenIDProviderMetadataSerializer :
    JsonDataObjectSerializer<OpenIDProviderMetadata.Draft11>(OpenIDProviderMetadata.Draft11.generatedSerializer())

private object Draft13OpenIDProviderMetadataSerializer :
    JsonDataObjectSerializer<OpenIDProviderMetadata.Draft13>(OpenIDProviderMetadata.Draft13.generatedSerializer())
