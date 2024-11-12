package id.walt.oid4vc

import cbor.Cbor
import id.walt.credentials.issuance.Issuer.mergingJwtIssue
import id.walt.credentials.issuance.Issuer.mergingSdJwtIssue
import id.walt.credentials.issuance.dataFunctions
import id.walt.credentials.utils.CredentialDataMergeUtils.mergeSDJwtVCPayloadWithMapping
import id.walt.credentials.utils.VCFormat
import id.walt.credentials.vc.vcs.W3CVC
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
import id.walt.oid4vc.requests.AuthorizationRequest
import id.walt.oid4vc.requests.CredentialOfferRequest
import id.walt.oid4vc.requests.CredentialRequest
import id.walt.oid4vc.requests.TokenRequest
import id.walt.oid4vc.responses.AuthorizationCodeWithAuthorizationRequestResponse
import id.walt.oid4vc.responses.CredentialErrorCode
import id.walt.oid4vc.responses.TokenErrorCode
import id.walt.oid4vc.util.JwtUtils
import id.walt.oid4vc.util.http
import id.walt.policies.Verifier
import id.walt.policies.models.PolicyRequest.Companion.parsePolicyRequests
import id.walt.sdjwt.SDJwt
import id.walt.sdjwt.SDJwtVC
import id.walt.sdjwt.SDJwtVC.Companion.SD_JWT_VC_TYPE_HEADER
import id.walt.sdjwt.SDJwtVC.Companion.defaultPayloadProperties
import id.walt.sdjwt.SDMap
import id.walt.sdjwt.SDPayload
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.*
import io.ktor.utils.io.core.*
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.json.*

object OpenID4VCI {
    private val log = KotlinLogging.logger { }

    class CredentialRequestValidationResult(val success: Boolean, errorCode: CredentialErrorCode? = null, val message: String? = null) {}

    fun getCredentialOfferRequestUrl(
        credOffer: CredentialOffer,
        credentialOfferEndpoint: String = CROSS_DEVICE_CREDENTIAL_OFFER_URL,
        customReqParams: Map<String, List<String>> = mapOf()
    ): String {
        return URLBuilder(credentialOfferEndpoint).apply {
            parameters.appendAll(parametersOf(CredentialOfferRequest(credOffer, customParameters = customReqParams).toHttpParameters()))
        }.buildString()
    }

    fun getCredentialOfferRequestUrl(
        credOfferUrl: String,
        credentialOfferEndpoint: String = CROSS_DEVICE_CREDENTIAL_OFFER_URL,
        customReqParams: Map<String, List<String>> = mapOf()
    ): String {
        return URLBuilder(credentialOfferEndpoint).apply {
            parameters.appendAll(
                parametersOf(
                    CredentialOfferRequest(
                        credentialOfferUri = credOfferUrl,
                        customParameters = customReqParams
                    ).toHttpParameters()
                )
            )
        }.buildString()
    }

    fun getCredentialOfferRequestUrl(
        credOfferReq: CredentialOfferRequest,
        credentialOfferEndpoint: String = CROSS_DEVICE_CREDENTIAL_OFFER_URL
    ): String {
        return URLBuilder(credentialOfferEndpoint).apply {
            parameters.appendAll(parametersOf(credOfferReq.toHttpParameters()))
        }.buildString()
    }

    fun parseCredentialOfferRequestUrl(credOfferReqUrl: String): CredentialOfferRequest {
        return CredentialOfferRequest.fromHttpParameters(Url(credOfferReqUrl).parameters.toMap())
    }

    suspend fun parseAndResolveCredentialOfferRequestUrl(credOfferReqUrl: String): CredentialOffer {
        val offerReq = parseCredentialOfferRequestUrl(credOfferReqUrl)
        return offerReq.credentialOffer
            ?: if (!offerReq.credentialOfferUri.isNullOrEmpty()) {

                http.get(offerReq.credentialOfferUri).bodyAsText().let {
                    CredentialOffer.fromJSONString(it)
                }
            } else throw Exception("Credential offer request has no credential offer object set by value or reference.")
    }

    fun getCIProviderMetadataUrl(credOffer: CredentialOffer): String {
        return getCIProviderMetadataUrl(credOffer.credentialIssuer)
    }

    fun getCIProviderMetadataUrl(baseUrl: String) = URLBuilder(baseUrl).apply {
            appendPathSegments(".well-known", "openid-credential-issuer")
        }.buildString()

    fun getCommonProviderMetadataUrl(baseUrl: String) = URLBuilder(baseUrl).apply {
            appendPathSegments(".well-known", "openid-configuration")
        }.buildString()

    fun getOAuthProviderMetadataUrl(baseUrl: String) = URLBuilder(baseUrl).apply {
            appendPathSegments(".well-known", "oauth-authorization-server")
        }.buildString()

    fun getJWTIssuerProviderMetadataUrl(baseUrl: String) = URLBuilder(baseUrl).apply {
            appendPathSegments(".well-known", "jwt-vc-issuer")
        }.buildString()

    suspend fun resolveCIProviderMetadata(credOffer: CredentialOffer) = http.get(getCIProviderMetadataUrl(credOffer)).bodyAsText().let {
        OpenIDProviderMetadata.fromJSONString(it)
    }

    fun resolveOfferedCredentials(credentialOffer: CredentialOffer, providerMetadata: OpenIDProviderMetadata): List<OfferedCredential> {
        val supportedCredentials = when (providerMetadata) {
            is OpenIDProviderMetadataD10 -> providerMetadata.credentialSupported ?: mapOf()
            is OpenIDProviderMetadataD13 -> providerMetadata.credentialConfigurationsSupported ?: mapOf()
        }

        return credentialOffer.credentialConfigurationIds.mapNotNull { c ->
            supportedCredentials[c]?.let {
                OfferedCredential.fromProviderMetadata(it)
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

    fun validateTokenRequestRaw(tokenRequestRaw: Map<String, List<String>>, authorizationCode: String): TokenRequest {
        val tokenRequest = parseTokenRequest(tokenRequestRaw)
        validateAuthorizationCode(tokenRequest, authorizationCode).let {
            if (!it) throw TokenError(tokenRequest, TokenErrorCode.invalid_request, "Code is not valid")
        }
        return tokenRequest
    }

    private fun parseTokenRequest(tokenRequestRaw: Map<String, List<String>>): TokenRequest {
        return TokenRequest.fromHttpParameters(tokenRequestRaw)
    }

    private fun validateAuthorizationCode(tokenRequest: TokenRequest, authorizationCode: String): Boolean {
        val code = when (tokenRequest.grantType) {
            GrantType.authorization_code -> tokenRequest.code ?: throw TokenError(
                tokenRequest = tokenRequest,
                errorCode = TokenErrorCode.invalid_grant,
                message = "No code parameter found on token request"
            )

            GrantType.pre_authorized_code -> tokenRequest.preAuthorizedCode ?: throw TokenError(
                tokenRequest = tokenRequest,
                errorCode = TokenErrorCode.invalid_grant,
                message = "No pre-authorized_code parameter found on token request"
            )

            else -> throw TokenError(tokenRequest, TokenErrorCode.unsupported_grant_type, "Grant type not supported")
        }
        return code == authorizationCode
    }

    suspend fun signToken(privateKey: Key, payload: JsonObject, headers: Map<String, JsonElement>? = null): String {
        return privateKey.signJws(payload.toString().toByteArray(), headers ?: emptyMap())
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
        val redirectUri = clientId + "/direct_post"
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
                // Create a jwt as request object as defined in JAR OAuth2.0 specification
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
            presentationFormat,
            vpToken = token,
            vpPolicies = policies,
            globalVcPolicies = policies,
            specificCredentialPolicies = emptyMap(),
            when (presentationSubmission != null) {
                true -> mapOf("presentationSubmission" to presentationSubmission)
                else -> emptyMap()
            }
        )

        return payload
    }

    fun createDefaultProviderMetadata(baseUrl: String, credentialSupported: Map<String, CredentialSupported>, version: OpenID4VCIVersion) : OpenIDProviderMetadata {

        return when (version) {
            OpenID4VCIVersion.D13 -> OpenIDProviderMetadataD13(
                issuer = baseUrl,
                authorizationEndpoint = "$baseUrl/authorize",
                pushedAuthorizationRequestEndpoint = "$baseUrl/par",
                tokenEndpoint = "$baseUrl/token",
                credentialEndpoint = "$baseUrl/credential",
                batchCredentialEndpoint = "$baseUrl/batch_credential",
                deferredCredentialEndpoint = "$baseUrl/credential_deferred",
                jwksUri = "$baseUrl/jwks",
                grantTypesSupported = setOf(GrantType.authorization_code, GrantType.pre_authorized_code),
                requestUriParameterSupported = true,
                subjectTypesSupported = setOf(SubjectType.public),
                authorizationServer = baseUrl,
                credentialIssuer = baseUrl, // (EBSI) this should be just "$baseUrl"  https://openid.net/specs/openid-4-verifiable-credential-issuance-1_0.html#section-11.2.1
                responseTypesSupported = setOf(
                    "code",
                    "vp_token",
                    "id_token"
                ),  // (EBSI) this is required one  https://www.rfc-editor.org/rfc/rfc8414.html#section-2
                idTokenSigningAlgValuesSupported = setOf("ES256"), // (EBSI) https://openid.net/specs/openid-connect-self-issued-v2-1_0.html#name-self-issued-openid-provider-
                codeChallengeMethodsSupported = listOf("S256"),
                credentialConfigurationsSupported = credentialSupported
            )

            OpenID4VCIVersion.D10 -> OpenIDProviderMetadataD10(
                issuer = baseUrl,
                authorizationEndpoint = "$baseUrl/authorize",
                pushedAuthorizationRequestEndpoint = "$baseUrl/par",
                tokenEndpoint = "$baseUrl/token",
                credentialEndpoint = "$baseUrl/credential",
                batchCredentialEndpoint = "$baseUrl/batch_credential",
                deferredCredentialEndpoint = "$baseUrl/credential_deferred",
                jwksUri = "$baseUrl/jwks",
                grantTypesSupported = setOf(GrantType.authorization_code, GrantType.pre_authorized_code),
                requestUriParameterSupported = true,
                subjectTypesSupported = setOf(SubjectType.public),
                authorizationServer = baseUrl,
                credentialIssuer = baseUrl, // (EBSI) this should be just "$baseUrl"  https://openid.net/specs/openid-4-verifiable-credential-issuance-1_0.html#section-11.2.1
                responseTypesSupported = setOf(
                    "code",
                    "vp_token",
                    "id_token"
                ),  // (EBSI) this is required one  https://www.rfc-editor.org/rfc/rfc8414.html#section-2
                idTokenSigningAlgValuesSupported = setOf("ES256"), // (EBSI) https://openid.net/specs/openid-connect-self-issued-v2-1_0.html#name-self-issued-openid-provider-
                codeChallengeMethodsSupported = listOf("S256"),
                credentialSupported = credentialSupported.mapValues { (_, credential) -> credential.copy(types = credential.credentialDefinition?.type, credentialDefinition = null) }
            )
        }
    }

    fun getNonceFromProof(proofOfPossession: ProofOfPossession) = when (proofOfPossession.proofType) {
        ProofType.jwt -> JwtUtils.parseJWTPayload(proofOfPossession.jwt!!)[JWTClaims.Payload.nonce]?.jsonPrimitive?.content
        ProofType.cwt -> Cbor.decodeFromByteArray<COSESign1>(proofOfPossession.cwt!!.base64UrlDecode()).decodePayload()?.let { payload ->
            payload.value[MapKey(ProofOfPossession.CWTProofBuilder.LABEL_NONCE)].let {
                when (it) {
                    is ByteStringElement -> io.ktor.utils.io.core.String(it.value)
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
            credentialRequest.proof.isJwtProofType -> OpenID4VC.verifyTokenSignature(
                TokenTarget.PROOF_OF_POSSESSION, credentialRequest.proof.jwt!!
            ) && getNonceFromProof(credentialRequest.proof) == nonce

            credentialRequest.proof.isCwtProofType -> OpenID4VC.verifyCOSESign1Signature(
                TokenTarget.PROOF_OF_POSSESSION, credentialRequest.proof.cwt!!
            ) && getNonceFromProof(credentialRequest.proof) == nonce

            else -> false
        }
    }

    suspend fun validateCredentialRequest(credentialRequest: CredentialRequest, nonce: String, openIDProviderMetadata: OpenIDProviderMetadata): CredentialRequestValidationResult {
        log.debug { "Credential request to validate: $credentialRequest" }
        if (credentialRequest.proof == null || !validateProofOfPossession(credentialRequest, nonce)) {
            return CredentialRequestValidationResult(
                false,
                CredentialErrorCode.invalid_or_missing_proof,
                "Invalid proof of possession"
            )
        }
        val supportedCredentialFormats = when (openIDProviderMetadata) {
            is OpenIDProviderMetadataD13 -> openIDProviderMetadata.credentialConfigurationsSupported?.values?.map { it.format }?.toSet() ?: setOf()
            is OpenIDProviderMetadataD10 -> openIDProviderMetadata.credentialSupported?.values?.map { it.format }?.toSet() ?: setOf()
        }

        if (!supportedCredentialFormats.contains(credentialRequest.format))
            return CredentialRequestValidationResult(
                false,
                CredentialErrorCode.unsupported_credential_format,
                "Credential format not supported"
            )

        return CredentialRequestValidationResult(true)
    }

    suspend fun generateDeferredCredentialToken(sessionId: String, issuer: String, credentialId: String, tokenKey: Key): String {
        return OpenID4VC.generateToken(sessionId, issuer, TokenTarget.DEFERRED_CREDENTIAL, credentialId, tokenKey)
    }

    suspend fun generateSdJwtVC(credentialRequest: CredentialRequest,
                                credentialData: JsonObject, dataMapping: JsonObject?,
                                selectiveDisclosure: SDMap?, vct: String,
                                issuerDid: String?, issuerKid: String?, x5Chain: List<String>?,
                                issuerKey: Key): String {
        val proofHeader = credentialRequest.proof?.jwt?.let { JwtUtils.parseJWTHeader(it) } ?: throw CredentialError(
            credentialRequest, CredentialErrorCode.invalid_or_missing_proof, message = "Proof must be JWT proof"
        )
        val holderKid = proofHeader[JWTClaims.Header.keyID]?.jsonPrimitive?.content
        val holderKey = proofHeader[JWTClaims.Header.jwk]?.jsonObject
        if (holderKey.isNullOrEmpty() && holderKid.isNullOrEmpty()) throw CredentialError(
            credentialRequest,
            CredentialErrorCode.invalid_or_missing_proof,
            message = "Proof JWT header must contain kid or jwk claim"
        )
        val holderDid = if (!holderKid.isNullOrEmpty() && DidUtils.isDidUrl(holderKid)) holderKid.substringBefore("#") else null
        val holderKeyJWK =  JWKKey.importJWK(holderKey.toString()).getOrNull()?.exportJWKObject()?.plus("kid" to JWKKey.importJWK(holderKey.toString()).getOrThrow().getKeyId())?.toJsonObject()

        val sdPayload = SDPayload.createSDPayload(
            credentialData.mergeSDJwtVCPayloadWithMapping(
                mapping = dataMapping ?: JsonObject(emptyMap()),
                context = mapOf(
                    "issuerDid" to issuerDid,
                    "subjectDid" to holderDid
                ).filterValues { !it.isNullOrEmpty() }.mapValues { JsonPrimitive(it.value) },
                dataFunctions
            ),
            selectiveDisclosure ?: SDMap(mapOf())
        )
        val cnf = holderDid?.let { buildJsonObject { put("kid", holderDid) } } ?:
        holderKeyJWK?.let { buildJsonObject { put("jwk", holderKeyJWK) } }
        ?: throw IllegalArgumentException("Either holderKey or holderDid must be given")

        val defaultPayloadProperties = defaultPayloadProperties(
            issuerDid ?: issuerKid ?: issuerKey.getKeyId(),
            cnf, vct, null, null, null, null)
        val undisclosedPayload = sdPayload.undisclosedPayload.plus(defaultPayloadProperties).let { JsonObject(it) }
        val fullPayload = sdPayload.fullPayload.plus(defaultPayloadProperties).let { JsonObject(it) }

        val headers = mapOf(
            "kid" to issuerKid,
            "typ" to SD_JWT_VC_TYPE_HEADER
        ).plus(x5Chain?.let {
            mapOf("x5c" to JsonArray(it.map { cert -> cert.toJsonElement() }))
        } ?: mapOf())

        val finalSdPayload = SDPayload.createSDPayload(fullPayload, undisclosedPayload)

        val jwt = issuerKey.signJws(finalSdPayload.undisclosedPayload.toString().encodeToByteArray(),
            headers.mapValues { it.value.toJsonElement() })
        return SDJwtVC(SDJwt.createFromSignedJwt(jwt, finalSdPayload)).toString()
    }

    suspend fun generateW3CJwtVC(credentialRequest: CredentialRequest,
                                credentialData: JsonObject, dataMapping: JsonObject?,
                                selectiveDisclosure: SDMap?, issuerDid: String?, issuerKid: String?,
                                 x5Chain: List<String>?, issuerKey: Key): String {
        val proofHeader = credentialRequest.proof?.jwt?.let { JwtUtils.parseJWTHeader(it) } ?: throw CredentialError(
            credentialRequest, CredentialErrorCode.invalid_or_missing_proof, message = "Proof must be JWT proof"
        )
        val holderKid = proofHeader[JWTClaims.Header.keyID]?.jsonPrimitive?.content
        val holderDid = if (!holderKid.isNullOrEmpty() && DidUtils.isDidUrl(holderKid)) holderKid.substringBefore("#") else null
        val additionalJwtHeaders = x5Chain?.let {
            mapOf("x5c" to JsonArray(it.map { cert -> cert.toJsonElement() }))
        } ?: mapOf()
        return W3CVC(credentialData).let { vc -> when(selectiveDisclosure.isNullOrEmpty()) {
            true -> vc.mergingJwtIssue(
                issuerKey = issuerKey,
                issuerDid = issuerDid,
                issuerKid = issuerKid,
                subjectDid = holderDid ?: "",
                mappings = dataMapping ?: JsonObject(emptyMap()),
                additionalJwtHeader = additionalJwtHeaders,
                additionalJwtOptions = emptyMap()
            )
            else -> vc.mergingSdJwtIssue(
                issuerKey = issuerKey,
                issuerDid = issuerDid,
                subjectDid = holderDid ?: "",
                mappings = dataMapping ?: JsonObject(emptyMap()),
                additionalJwtHeaders = additionalJwtHeaders,
                additionalJwtOptions = emptyMap(),
                disclosureMap = selectiveDisclosure
            )
        }}
    }
}


enum class OpenID4VCIVersion(val versionString: String) {
    D10("d10"),
    D13("d13");

    companion object {
        fun from(version: String): OpenID4VCIVersion {
            return values().find { it.versionString == version }
                ?: throw IllegalArgumentException("Unsupported version: $version. Supported Versions are: Draft13 -> d13 and Draft10 -> d10")
        }
    }
}