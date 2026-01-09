package id.walt.oid4vc

import cbor.Cbor
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.Base64Utils.base64UrlDecode
import id.walt.crypto.utils.JsonUtils.toJsonElement
import id.walt.crypto.utils.JsonUtils.toJsonObject
import id.walt.did.dids.DidUtils
import id.walt.mdoc.cose.COSESign1
import id.walt.mdoc.dataelement.ByteStringElement
import id.walt.mdoc.dataelement.MapKey
import id.walt.mdoc.dataelement.StringElement
import id.walt.oid4vc.data.*
import id.walt.oid4vc.data.ResponseType.Companion.getResponseTypeString
import id.walt.oid4vc.data.dif.PresentationDefinition
import id.walt.oid4vc.data.dif.PresentationSubmission
import id.walt.oid4vc.definitions.CROSS_DEVICE_CREDENTIAL_OFFER_URL
import id.walt.oid4vc.definitions.JWTClaims
import id.walt.oid4vc.errors.CredentialError
import id.walt.oid4vc.errors.TokenError
import id.walt.oid4vc.providers.TokenTarget
import id.walt.oid4vc.requests.*
import id.walt.oid4vc.responses.*
import id.walt.oid4vc.util.JwtUtils
import id.walt.oid4vc.util.http
import id.walt.policies.Verifier
import id.walt.policies.models.PolicyRequest.Companion.parsePolicyRequests
import id.walt.sdjwt.SDJwt
import id.walt.sdjwt.SDJwt.Companion.SEPARATOR_STR
import id.walt.sdjwt.SDJwtVC
import id.walt.sdjwt.SDJwtVC.Companion.SD_JWT_VC_TYPE_HEADER
import id.walt.sdjwt.SDJwtVC.Companion.defaultPayloadProperties
import id.walt.sdjwt.SDMap
import id.walt.sdjwt.SDPayload
import id.walt.w3c.issuance.Issuer.getKidHeader
import id.walt.w3c.issuance.Issuer.mergingJwtIssue
import id.walt.w3c.issuance.Issuer.mergingSdJwtIssue
import id.walt.w3c.issuance.dataFunctions
import id.walt.w3c.utils.CredentialDataMergeUtils.mergeSDJwtVCPayloadWithMapping
import id.walt.w3c.utils.VCFormat
import id.walt.w3c.vc.vcs.W3CVC
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.*
import io.ktor.utils.io.core.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.json.*

object OpenID4VCI {
    private val log = KotlinLogging.logger { }

    class CredentialRequestValidationResult(
        val success: Boolean,
        errorCode: CredentialErrorCode? = null,
        val message: String? = null
    )

    fun getCredentialOfferRequestUrl(
        credOffer: CredentialOffer,
        credentialOfferEndpoint: String = CROSS_DEVICE_CREDENTIAL_OFFER_URL,
        customReqParams: Map<String, List<String>> = mapOf()
    ): String {
        return URLBuilder(credentialOfferEndpoint).apply {
            parameters.appendAll(
                parametersOf(
                    CredentialOfferRequest(
                        credentialOffer = credOffer,
                        customParameters = customReqParams
                    ).toHttpParameters()
                )
            )
        }.buildString().replace("localhost/", "")
    }

    fun getCredentialOfferRequestUrl(
        credOfferReq: CredentialOfferRequest,
        credentialOfferEndpoint: String = CROSS_DEVICE_CREDENTIAL_OFFER_URL
    ): String {
        return URLBuilder(credentialOfferEndpoint).apply {
            parameters.appendAll(parametersOf(credOfferReq.toHttpParameters()))
        }.buildString().replace("localhost/", "")
    }

    fun parseCredentialOfferRequestUrl(credOfferReqUrl: String): CredentialOfferRequest {
        return CredentialOfferRequest.fromHttpParameters(Url(credOfferReqUrl).parameters.toMap())
    }

    private fun Throwable?.causeName() = this?.let { ex -> ex::class.simpleName }

    @Serializable
    data class UnresolvableCredentialOfferException(
        val url: String,
        @Transient
        override val cause: Throwable? = null
    ) : IllegalArgumentException(
        "Could not resolve credential offer from URL${cause.causeName().let { " ($it)" }}: $url",
        cause
    )

    @Serializable
    data class CouldNotParseCredentialOfferException(
        val url: String,
        val text: String,
        @Transient
        override val cause: Throwable? = null
    ) : IllegalArgumentException(
        "Could not parse credential offer from URL (\"$url\") result: \"$text\"",
        cause
    )

    suspend fun parseAndResolveCredentialOfferRequestUrl(credOfferReqUrl: String): CredentialOffer {
        val offerReq = parseCredentialOfferRequestUrl(credOfferReqUrl)

        return when {

            offerReq.credentialOfferUri != null -> {
                runCatching {
                    http.get(offerReq.credentialOfferUri)
                }.getOrElse { ex ->
                    throw UnresolvableCredentialOfferException(offerReq.credentialOfferUri, ex)
                }.bodyAsText().let { text ->
                    runCatching { CredentialOffer.fromJSONString(text) }.getOrElse { ex ->
                        throw CouldNotParseCredentialOfferException(url = offerReq.credentialOfferUri, text = text, ex)
                    }
                }
            }

            offerReq.credentialOffer != null -> offerReq.credentialOffer

            else -> throw IllegalStateException("Credential Offer does not contain a Credential Offer Object nor a Credential Offer URI")
        }
    }

    fun getCIProviderMetadataUrl(credOffer: CredentialOffer): String {
        return getCIProviderMetadataUrl(credOffer.credentialIssuer)
    }

    fun getCIProviderMetadataUrl(baseUrl: String) = URLBuilder(baseUrl).apply {
        appendPathSegments(".well-known", "openid-credential-issuer")
    }.buildString()

    fun getOpenIdProviderMetadataUrl(baseUrl: String) = URLBuilder(baseUrl).apply {
        appendPathSegments(".well-known", "openid-configuration")
    }.buildString()

    fun getOAuthProviderMetadataUrl(baseUrl: String) = URLBuilder(baseUrl).apply {
        appendPathSegments(".well-known", "oauth-authorization-server")
    }.buildString()

    fun getJWTVCIssuerProviderMetadataUrl(baseUrl: String) = Url(baseUrl).let {
        URLBuilder(it.protocolWithAuthority).apply {
            appendPathSegments(".well-known", "jwt-vc-issuer")

            if (it.fullPath.isNotEmpty())
                appendPathSegments(it.fullPath.trim('/'))

        }.buildString()
    }

    suspend fun resolveCIProviderMetadata(credOffer: CredentialOffer) =
        resolveCIProviderMetadata(credOffer.credentialIssuer)

    suspend fun resolveCIProviderMetadata(issuerBaseUrl: String) =
        http.get(getCIProviderMetadataUrl(issuerBaseUrl)).bodyAsText().let {
            OpenIDProviderMetadata.fromJSONString(it)
        }

    fun resolveOfferedCredentials(
        credentialOffer: CredentialOffer,
        providerMetadata: OpenIDProviderMetadata
    ): List<OfferedCredential> {
        val supportedCredentials = when (providerMetadata) {
            is OpenIDProviderMetadata.Draft13 -> providerMetadata.credentialConfigurationsSupported ?: mapOf()
            is OpenIDProviderMetadata.Draft11 -> providerMetadata.credentialSupported?.values?.associateBy { it.id }
                ?: mapOf()
        }

        val credentialsOffered = when (credentialOffer) {
            is CredentialOffer.Draft13 -> credentialOffer.credentialConfigurationIds
            is CredentialOffer.Draft11 -> credentialOffer.credentials
        }

        return credentialsOffered.mapNotNull { credentialOffered ->
            when (credentialOffered) {
                is JsonPrimitive if credentialOffered.isString -> {
                    supportedCredentials[credentialOffered.content]?.let { OfferedCredential.fromProviderMetadata(it) }
                }

                is JsonObject -> {
                    OfferedCredential.fromJSON(credentialOffered)
                }

                else -> {
                    throw IllegalArgumentException("Entries of offer's offered credentials array can be either strings, or objects, but in this case they were neither")
                }
            }
        }

    }

    fun validateAuthorizationRequestQueryString(authorizationRequestQueryString: String): AuthorizationRequest {
        // Todo validate authorization request params e.g. client_id, etc
        return parseAuthorizationRequestQueryString(authorizationRequestQueryString)
    }

    private fun parseAuthorizationRequestQueryString(authorizationRequestQueryString: String): AuthorizationRequest {
        return AuthorizationRequest.fromHttpQueryString(authorizationRequestQueryString)
    }

    suspend fun sendTokenRequest(
        providerMetadata: OpenIDProviderMetadata,
        tokenRequest: TokenRequest,
    ): TokenResponse {

        val tokenEndpoint = providerMetadata.tokenEndpoint ?: when (providerMetadata) {
            is OpenIDProviderMetadata.Draft11 -> {
                resolveOAuthServersTokenEndpoint(listOf(providerMetadata.authorizationServer!!))
            }

            is OpenIDProviderMetadata.Draft13 -> {
                resolveOAuthServersTokenEndpoint(providerMetadata.authorizationServers!!.toList())
            }

        }

        val response = http.submitForm(
            url = tokenEndpoint,
            formParameters = parametersOf(tokenRequest.toHttpParameters())
        )

        if (!response.status.isSuccess()) {
            throw IllegalArgumentException("Failed to get token: ${response.status.value} - ${response.bodyAsText()}")
        }

        return response.body<JsonObject>().let { TokenResponse.fromJSON(it) }
    }

    private suspend fun resolveOAuthServersTokenEndpoint(
        authServerUrl: List<String>,
    ): String {
        val urls = authServerUrl.flatMap {
            listOf(
                getOAuthProviderMetadataUrl(it),
                getOpenIdProviderMetadataUrl(it),
            )
        }

        for (url in urls) {
            val response = http.get(url)
            if (response.status.isSuccess()) {
                return response.body<JsonObject>()["token_endpoint"]!!.jsonPrimitive.content
            }
        }

        throw IllegalStateException("Unable to resolve token_endpoint either directly, or indirectly, from the issuer's metadata")
    }

    suspend fun sendCredentialRequest(
        providerMetadata: OpenIDProviderMetadata,
        accessToken: String,
        credentialRequest: CredentialRequest
    ): CredentialResponse {
        val credentialEndpoint = providerMetadata.credentialEndpoint
            ?: throw IllegalArgumentException("Missing credential endpoint in issuer metadata.")

        val response = http.post(credentialEndpoint) {
            headers {
                appendAll(Headers.build { set(HttpHeaders.Authorization, "Bearer $accessToken") })
            }
            contentType(ContentType.Application.Json)
            setBody(credentialRequest.toJSON())
        }

        if (!response.status.isSuccess()) {
            throw IllegalArgumentException("Failed to send credential request: ${response.status.value} - ${response.bodyAsText()}")
        }

        return response.body<JsonObject>().let { CredentialResponse.fromJSON(it) }
    }

    suspend fun sendBatchCredentialRequest(
        providerMetadata: OpenIDProviderMetadata,
        accessToken: String,
        batchCredentialRequest: BatchCredentialRequest,
    ): BatchCredentialResponse {
        val batchCredentialEndpoint = providerMetadata.batchCredentialEndpoint
            ?: throw IllegalArgumentException("Missing batch credential endpoint in issuer metadata.")

        val response = http.post(batchCredentialEndpoint) {
            headers {
                appendAll(Headers.build { set(HttpHeaders.Authorization, "Bearer $accessToken") })
            }
            contentType(ContentType.Application.Json)
            setBody(batchCredentialRequest.toJSON())
        }

        if (!response.status.isSuccess()) {
            throw IllegalArgumentException("Failed to send batch credential request: ${response.status.value} - ${response.bodyAsText()}")
        }

        return response.body<JsonObject>().let { BatchCredentialResponse.fromJSON(it) }
    }

    fun validateTokenRequestRaw(
        tokenRequestRaw: Map<String, List<String>>,
        authorizationCode: String
    ): TokenRequest {
        val tokenRequest = parseTokenRequest(tokenRequestRaw)

        validateAuthorizationCode(
            tokenRequest = tokenRequest,
            authorizationCode = authorizationCode
        ).let {
            if (!it) throw TokenError(
                tokenRequest = tokenRequest,
                errorCode = TokenErrorCode.invalid_request,
                message = "Code is not valid"
            )
        }

        return tokenRequest
    }

    private fun parseTokenRequest(tokenRequestRaw: Map<String, List<String>>): TokenRequest {
        return TokenRequest.fromHttpParameters(tokenRequestRaw)
    }

    private fun validateAuthorizationCode(
        tokenRequest: TokenRequest,
        authorizationCode: String
    ): Boolean {
        val code = when (tokenRequest) {
            is TokenRequest.AuthorizationCode -> tokenRequest.code
            is TokenRequest.PreAuthorizedCode -> tokenRequest.preAuthorizedCode
        }

        return code == authorizationCode
    }

    suspend fun signToken(
        privateKey: Key,
        payload: JsonObject,
        headers: Map<String, JsonElement>? = null
    ): String {
        return privateKey.signJws(
            plaintext = payload.toString().toByteArray(),
            headers = headers ?: emptyMap()
        )
    }

    fun validateTokenResponse(
        tokenResponse: TokenResponse,
    ) {
        require(tokenResponse.isSuccess) {
            "token request failed: ${tokenResponse.error} ${tokenResponse.errorDescription}"
        }

        requireNotNull(tokenResponse.accessToken) {
            "invalid Authorization Server token response: no access token included in the response: $tokenResponse "
        }
    }

    fun isCryptographicBindingProofRequired(providerMetadata: OpenIDProviderMetadata): Boolean {
        return providerMetadata.getSupportedProofTypes().isNullOrEmpty()
    }

    suspend fun verifyToken(token: String, key: Key): Result<JsonElement> {
        return key.verifyJws(token)
    }

    suspend fun generateAuthorizationRequest(
        authorizationRequest: AuthorizationRequest,
        clientId: String,
        privKey: Key,
        responseType: ResponseType,
        state: String,
        nonce: String,
        isJar: Boolean? = true,
        presentationDefinition: PresentationDefinition? = null
    ): AuthorizationCodeWithAuthorizationRequestResponse {
        val authorizationResponseServerMode = ResponseMode.direct_post
        val redirectUri = "$clientId/direct_post"
        val scope = setOf("openid")

        return AuthorizationCodeWithAuthorizationRequestResponse.success(
            state = state,
            clientId = clientId,
            redirectUri = redirectUri,
            responseType = getResponseTypeString(responseType),
            responseMode = authorizationResponseServerMode,
            scope = scope,
            nonce = nonce,
            requestUri = null,
            request = when (isJar!!) {
                // Create a jwt as a request object as defined in JAR OAuth2.0 specification
                true -> signToken(
                    privKey,
                    buildJsonObject {
                        put(JWTClaims.Payload.issuer, clientId)
                        put(JWTClaims.Payload.audience, authorizationRequest.clientId)
                        put(JWTClaims.Payload.nonce, nonce)
                        put("state", state)
                        put("client_id", clientId)
                        put("redirect_uri", redirectUri)
                        put("response_type", getResponseTypeString(responseType))
                        put("response_mode", authorizationResponseServerMode.name)
                        put("scope", "openid")
                        when (responseType) {
                            ResponseType.VpToken -> put("presentation_definition", presentationDefinition!!.toJSON())
                            else -> null
                        }
                    }
                )

                false -> null
            },
            presentationDefinition = when (responseType) {
                ResponseType.VpToken -> presentationDefinition!!.toJSONString()
                else -> null
            }
        )
    }

    suspend fun validateAuthorizationRequestToken(
        token: String,
        presentationSubmission: PresentationSubmission? = null
    ): JsonObject {
        // 1. Validate Header
        val header = JwtUtils.parseJWTHeader(token)

        if (!header.keys.containsAll(
                setOf(
                    JWTClaims.Header.type,
                    JWTClaims.Header.keyID,
                    JWTClaims.Header.algorithm,
                )
            )
        ) {
            throw IllegalStateException("Invalid header in token")
        }

        // 2. Validate Payload
        val payload = JwtUtils.parseJWTPayload(token)
        if (!payload.keys.containsAll(
                setOf(
                    JWTClaims.Payload.issuer,
                    JWTClaims.Payload.audience,
                    JWTClaims.Payload.issuedAtTime,
                    JWTClaims.Payload.nonce,
                )
            )
        ) {
            throw IllegalArgumentException("Invalid payload in token")
        }

        // 3. Verify iss = sub = did
        val sub = payload[JWTClaims.Payload.subject]!!.jsonPrimitive.content
        val iss = payload[JWTClaims.Payload.issuer]!!.jsonPrimitive.content
        val kid = header[JWTClaims.Header.keyID]!!.jsonPrimitive.content
        val did = kid.substringBefore("#")

        if (iss != sub || iss != did || sub != did) {
            log.debug { "$sub $iss $did" }
            throw IllegalArgumentException("Invalid payload in token. sub != iss != did")
        }

        // 4. Verify VP or ID Token
        val policies =
            Json.parseToJsonElement("""["signature", "expired", "not-before"]""").jsonArray.parsePolicyRequests()

        val presentationFormat = presentationSubmission?.descriptorMap?.firstOrNull()?.format ?: VCFormat.jwt

        Verifier.verifyPresentation(
            format = presentationFormat,
            vpToken = token,
            vpPolicies = policies,
            globalVcPolicies = policies,
            specificCredentialPolicies = emptyMap(),
            presentationContext = when (presentationSubmission != null) {
                true -> mapOf("presentationSubmission" to presentationSubmission)
                else -> emptyMap()
            }
        )

        return payload
    }

    fun createDefaultProviderMetadata(
        baseUrl: String,
        credentialSupported: Map<String, CredentialSupported>? = null,
        version: OpenID4VCIVersion,
        customParameters: Map<String, JsonElement>? = emptyMap()
    ): OpenIDProviderMetadata {

        return when (version) {
            OpenID4VCIVersion.DRAFT13 -> OpenIDProviderMetadata.Draft13(
                issuer = baseUrl,
                authorizationServers = setOf(baseUrl),
                authorizationEndpoint = "$baseUrl/authorize",
                tokenEndpoint = "$baseUrl/token",
                credentialEndpoint = "$baseUrl/credential",
                batchCredentialEndpoint = "$baseUrl/batch_credential",
                deferredCredentialEndpoint = "$baseUrl/credential_deferred",
                jwksUri = "$baseUrl/jwks",
                grantTypesSupported = setOf(GrantType.authorization_code, GrantType.pre_authorized_code),
                requestUriParameterSupported = true,
                subjectTypesSupported = setOf(SubjectType.public),
                credentialIssuer = baseUrl, // (EBSI) this should be just "$baseUrl"  https://openid.net/specs/openid-4-verifiable-credential-issuance-1_0.html#section-11.2.1
                responseTypesSupported = setOf(
                    ResponseType.Code.value,
                    ResponseType.VpToken.value,
                    ResponseType.IdToken.value
                ),  // (EBSI) this is required one  https://www.rfc-editor.org/rfc/rfc8414.html#section-2
                idTokenSigningAlgValuesSupported = setOf("ES256"), // (EBSI) https://openid.net/specs/openid-connect-self-issued-v2-1_0.html#name-self-issued-openid-provider-
                codeChallengeMethodsSupported = listOf("S256"),
                credentialConfigurationsSupported = credentialSupported,
                customParameters = customParameters!!
            )

            OpenID4VCIVersion.DRAFT11 -> OpenIDProviderMetadata.Draft11.create(
                issuer = baseUrl,
                authorizationServer = baseUrl,
                authorizationEndpoint = "$baseUrl/authorize",
                tokenEndpoint = "$baseUrl/token",
                credentialEndpoint = "$baseUrl/credential",
                batchCredentialEndpoint = "$baseUrl/batch_credential",
                deferredCredentialEndpoint = "$baseUrl/credential_deferred",
                jwksUri = "$baseUrl/jwks",
                grantTypesSupported = setOf(GrantType.authorization_code, GrantType.pre_authorized_code),
                requestUriParameterSupported = true,
                subjectTypesSupported = setOf(SubjectType.public),
                credentialIssuer = baseUrl, // (EBSI) this should be just "$baseUrl"  https://openid.net/specs/openid-4-verifiable-credential-issuance-1_0.html#section-11.2.1
                responseTypesSupported = setOf(
                    ResponseType.Code.value,
                    ResponseType.VpToken.value,
                    ResponseType.IdToken.value
                ),  // (EBSI) this is required one  https://www.rfc-editor.org/rfc/rfc8414.html#section-2
                idTokenSigningAlgValuesSupported = setOf("ES256"), // (EBSI) https://openid.net/specs/openid-connect-self-issued-v2-1_0.html#name-self-issued-openid-provider-
                codeChallengeMethodsSupported = listOf("S256"),
                credentialSupported = credentialSupported?.filterValues { credential ->
                    credential.format == CredentialFormat.jwt_vc || credential.format == CredentialFormat.jwt_vc_json
                }?.mapValues { (_, credential) ->
                    credential.copy(types = credential.credentialDefinition?.type, credentialDefinition = null)
                },
                customParameters = customParameters!!
            )

            OpenID4VCIVersion.V1 -> OpenIDProviderMetadata.Draft13(
                issuer = baseUrl,
                authorizationEndpoint = "$baseUrl/authorize",
                tokenEndpoint = "$baseUrl/token",
                credentialEndpoint = "$baseUrl/credential",
                deferredCredentialEndpoint = "$baseUrl/credential_deferred",
                jwksUri = "$baseUrl/jwks",
                grantTypesSupported = setOf(GrantType.authorization_code),
                credentialIssuer = baseUrl,
                responseTypesSupported = setOf(
                    ResponseType.Code.value,
                ),
                codeChallengeMethodsSupported = listOf("S256"),
                credentialConfigurationsSupported = credentialSupported,
                customParameters = customParameters!!,
//                authorizationServers = setOf(baseUrl),
                nonceEndpoint =  "$baseUrl/nonce",

            )
        }
    }

    fun getNonceFromProof(proofOfPossession: ProofOfPossession) = when (proofOfPossession.proofType) {
        ProofType.jwt -> JwtUtils.parseJWTPayload(proofOfPossession.jwt!!)[JWTClaims.Payload.nonce]?.jsonPrimitive?.content
        ProofType.cwt -> Cbor.decodeFromByteArray<COSESign1>(proofOfPossession.cwt!!.base64UrlDecode()).decodePayload()
            ?.let { payload ->
                payload.value[MapKey(ProofOfPossession.CWTProofBuilder.LABEL_NONCE)].let {
                    when (it) {
                        is ByteStringElement -> it.value.decodeToString(0, 0 + it.value.size)
                        is StringElement -> it.value
                        else -> throw Error("Invalid nonce type")
                    }
                }
            }

        else -> null
    }

    suspend fun validateProofOfPossession(credentialRequest: CredentialRequest, nonce: String): Boolean {
        log.debug { "VALIDATING: ${credentialRequest.proof} with nonce $nonce" }
        log.debug { "VERIFYING ITS SIGNATURE" }

        if (credentialRequest.proof == null) return false

        return when {
            credentialRequest.proof.isJwtProofType -> {
                OpenID4VC.verifyTokenSignature(
                    target = TokenTarget.PROOF_OF_POSSESSION,
                    token = credentialRequest.proof.jwt!!
                ) && getNonceFromProof(credentialRequest.proof) == nonce
            }

            credentialRequest.proof.isCwtProofType -> {
                OpenID4VC.verifyCOSESign1Signature(
                    target = TokenTarget.PROOF_OF_POSSESSION,
                    token = credentialRequest.proof.cwt!!
                ) && getNonceFromProof(credentialRequest.proof) == nonce
            }

            else -> false
        }
    }

    suspend fun validateCredentialRequest(
        credentialRequest: CredentialRequest,
        nonce: String,
        openIDProviderMetadata: OpenIDProviderMetadata
    ): CredentialRequestValidationResult {
        log.debug { "Credential request to validate: $credentialRequest" }
        if (credentialRequest.proof == null || !validateProofOfPossession(credentialRequest, nonce)) {
            return CredentialRequestValidationResult(
                success = false,
                errorCode = CredentialErrorCode.invalid_or_missing_proof,
                message = "Invalid proof of possession"
            )
        }

        val supportedCredentialFormats = when (openIDProviderMetadata) {
            is OpenIDProviderMetadata.Draft13 -> openIDProviderMetadata.credentialConfigurationsSupported?.values?.map { it.format }
                ?.toSet() ?: setOf()

            is OpenIDProviderMetadata.Draft11 -> openIDProviderMetadata.credentialSupported?.values?.map { it.format }
                ?.toSet() ?: setOf()
        }

        if (!supportedCredentialFormats.contains(credentialRequest.format))
            return CredentialRequestValidationResult(
                success = false,
                errorCode = CredentialErrorCode.unsupported_credential_format,
                message = "Credential format not supported"
            )

        return CredentialRequestValidationResult(true)
    }

    suspend fun generateDeferredCredentialToken(
        sessionId: String,
        issuer: String,
        credentialId: String,
        tokenKey: Key
    ): String {
        return OpenID4VC.generateToken(
            sub = sessionId,
            issuer = issuer,
            audience = TokenTarget.DEFERRED_CREDENTIAL,
            tokenId = credentialId,
            tokenKey = tokenKey
        )
    }

    suspend fun generateSdJwtVC(
        credentialRequest: CredentialRequest,
        credentialData: JsonObject,
        issuerId: String,
        issuerKey: Key,
        selectiveDisclosure: SDMap? = null,
        dataMapping: JsonObject? = null,
        x5Chain: List<String>? = null,
        display: List<DisplayProperties>? = null,
    ): String {
        val proofHeader = credentialRequest.proof?.jwt?.let { JwtUtils.parseJWTHeader(it) }
            ?: throw CredentialError(
                credentialRequest = credentialRequest,
                errorCode = CredentialErrorCode.invalid_or_missing_proof,
                message = "Proof must be JWT proof"
            )

        val holderKid = proofHeader[JWTClaims.Header.keyID]?.jsonPrimitive?.content

        val holderKey = proofHeader[JWTClaims.Header.jwk]?.jsonObject

        if (holderKey.isNullOrEmpty() && holderKid.isNullOrEmpty()) throw CredentialError(
            credentialRequest = credentialRequest,
            errorCode = CredentialErrorCode.invalid_or_missing_proof,
            message = "Proof JWT header must contain kid or jwk claim"
        )

        val holderDid =
            if (!holderKid.isNullOrEmpty() && DidUtils.isDidUrl(holderKid)) holderKid.substringBefore("#") else null

        val holderKeyJWK = JWKKey.importJWK(holderKey.toString()).getOrNull()?.exportJWKObject()
            ?.plus(JWTClaims.Header.keyID to JWKKey.importJWK(holderKey.toString()).getOrThrow().getKeyId())
            ?.toJsonObject()

        val sdPayload = SDPayload.createSDPayload(
            fullPayload = credentialData.mergeSDJwtVCPayloadWithMapping(
                mapping = dataMapping ?: JsonObject(emptyMap()),
                context = mapOf(
                    "subjectDid" to holderDid,
                    "display" to Json.encodeToJsonElement(display ?: emptyList()).jsonArray,
                ).filterValues {
                    when (it) {
                        is JsonElement -> it !is JsonNull && (it !is JsonObject || it.jsonObject.isNotEmpty()) && (it !is JsonArray || it.jsonArray.isNotEmpty())
                        else -> it.toString().isNotEmpty()
                    }
                }.mapValues { (_, value) ->
                    when (value) {
                        is JsonElement -> value
                        else -> JsonPrimitive(value.toString())
                    }
                },
                data = dataFunctions
            ),
            disclosureMap = selectiveDisclosure ?: SDMap(mapOf())
        )

        val cnf = holderDid?.let { buildJsonObject { put(JWTClaims.Header.keyID, holderDid) } }
            ?: holderKeyJWK?.let { buildJsonObject { put("jwk", holderKeyJWK) } }
            ?: throw IllegalArgumentException("Either holderKey or holderDid must be given")

        val defaultPayloadProperties = defaultPayloadProperties(
            issuerId = issuerId,
            cnf = cnf,
            vct = credentialRequest.vct
                ?: throw CredentialError(
                    credentialRequest = credentialRequest,
                    errorCode = CredentialErrorCode.invalid_request,
                    message = "VCT must be set on credential request"
                )
        ).let { defPayloadProps ->
            display?.takeIf { it.isNotEmpty() }?.let { dis ->
                defPayloadProps.plus(
                    "display" to Json.encodeToJsonElement(dis)
                )
            } ?: defPayloadProps
        }


        val undisclosedPayload = sdPayload.undisclosedPayload.plus(defaultPayloadProperties).let { JsonObject(it) }

        val fullPayload = sdPayload.fullPayload.plus(defaultPayloadProperties).let { JsonObject(it) }

        val issuerDid = if (DidUtils.isDidUrl(issuerId)) issuerId else null

        val headers = mapOf(
            JWTClaims.Header.keyID to getKidHeader(issuerKey, issuerDid),
            JWTClaims.Header.type to SD_JWT_VC_TYPE_HEADER
        ).plus(x5Chain?.let {
            mapOf(JWTClaims.Header.x5c to JsonArray(it.map { cert -> cert.toJsonElement() }))
        } ?: mapOf())

        val finalSdPayload = SDPayload.createSDPayload(
            fullPayload = fullPayload,
            undisclosedPayload = undisclosedPayload
        )

        val jwt = issuerKey.signJws(
            plaintext = finalSdPayload.undisclosedPayload.toString().encodeToByteArray(),
            headers = headers.mapValues { it.value.toJsonElement() }
        )

        val sdJwtVC = SDJwtVC(
            sdJwt = SDJwt.createFromSignedJwt(
                signedJwt = jwt,
                sdPayload = finalSdPayload
            )
        )

        return sdJwtVC.toString().plus(SEPARATOR_STR)
    }

    suspend fun generateW3CJwtVC(
        credentialRequest: CredentialRequest,
        credentialData: JsonObject,
        issuerKey: Key,
        issuerId: String,
        selectiveDisclosure: SDMap? = null,
        dataMapping: JsonObject? = null,
        x5Chain: List<String>? = null,
        display: List<DisplayProperties>? = null
    ): String {
        val proofHeader = credentialRequest.proof?.jwt?.let { JwtUtils.parseJWTHeader(it) }
            ?: throw CredentialError(
                credentialRequest = credentialRequest,
                errorCode = CredentialErrorCode.invalid_or_missing_proof,
                message = "Proof must be JWT proof"
            )

        val holderKid = proofHeader[JWTClaims.Header.keyID]?.jsonPrimitive?.content

        val holderDid =
            if (!holderKid.isNullOrEmpty() && DidUtils.isDidUrl(holderKid)) holderKid.substringBefore("#") else null

        val additionalJwtHeaders = x5Chain?.let {
            mapOf(JWTClaims.Header.x5c to JsonArray(it.map { cert -> cert.toJsonElement() }))
        } ?: mapOf()

        return W3CVC(credentialData).let { vc ->
            when (selectiveDisclosure.isNullOrEmpty()) {
                true -> vc.mergingJwtIssue(
                    issuerKey = issuerKey,
                    issuerId = issuerId,
                    subjectDid = holderDid ?: "",
                    mappings = dataMapping ?: JsonObject(emptyMap()),
                    additionalJwtHeader = additionalJwtHeaders,
                    display = Json.encodeToJsonElement(display ?: emptyList()).jsonArray,
                    additionalJwtOptions = emptyMap()
                )

                else -> vc.mergingSdJwtIssue(
                    issuerKey = issuerKey,
                    issuerId = issuerId,
                    subjectDid = holderDid ?: "",
                    mappings = dataMapping ?: JsonObject(emptyMap()),
                    additionalJwtHeaders = additionalJwtHeaders,
                    additionalJwtOptions = emptyMap(),
                    display = Json.encodeToJsonElement(display ?: emptyList()).jsonArray,
                    disclosureMap = selectiveDisclosure
                )
            }
        }
    }
}

enum class OpenID4VCIVersion(val versionString: String) {
    DRAFT11("draft11"),
    DRAFT13("draft13"),
    V1("v1");

    companion object {
        fun from(version: String): OpenID4VCIVersion {
            return entries.find { it.versionString == version }
                ?: throw IllegalArgumentException("Unsupported version: $version. Supported Versions are: DRAFT13 -> draft13, DRAFT11 -> draft11, V1 -> v1")
        }
    }
}
