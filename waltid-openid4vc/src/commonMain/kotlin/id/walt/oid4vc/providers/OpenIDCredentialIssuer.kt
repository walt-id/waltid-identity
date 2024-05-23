package id.walt.oid4vc.providers

import id.walt.oid4vc.data.*
import id.walt.oid4vc.definitions.CROSS_DEVICE_CREDENTIAL_OFFER_URL
import id.walt.oid4vc.definitions.JWTClaims
import id.walt.oid4vc.definitions.OPENID_CREDENTIAL_AUTHORIZATION_TYPE
import id.walt.oid4vc.errors.*
import id.walt.oid4vc.interfaces.CredentialResult
import id.walt.oid4vc.interfaces.ICredentialProvider
import id.walt.oid4vc.requests.*
import id.walt.oid4vc.responses.*
import id.walt.oid4vc.util.randomUUID
import io.ktor.http.*
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
/**
 * Base object for a service, providing issuance of verifiable credentials via the OpenID4CI issuance protocol
 * e.g.: Credential issuer
 */
abstract class OpenIDCredentialIssuer(
    baseUrl: String,
    override val config: CredentialIssuerConfig
) : OpenIDProvider<IssuanceSession>(baseUrl), ICredentialProvider {

    override val metadata
        get() = createDefaultProviderMetadata().copy(
            credentialConfigurationsSupported = config.credentialConfigurationsSupported
        )
    private var _supportedCredentialFormats: Set<CredentialFormat>? = null
    val supportedCredentialFormats
        get() = _supportedCredentialFormats ?: (metadata.credentialsSupported?.map { it.format }?.toSet()
            ?: setOf()).also {
            _supportedCredentialFormats = it
        }

    private fun isCredentialTypeSupported(format: CredentialFormat, types: List<String>?, docType: String?): Boolean {
        if (types.isNullOrEmpty() && docType.isNullOrEmpty())
            return false
        return config.credentialConfigurationsSupported.values.any { cred ->
            format == cred.format && (
                    (docType != null && cred.docType == docType) ||
                            (types != null && cred.types != null && cred.types.containsAll(types))
                    )
        }
    }

    private fun isSupportedAuthorizationDetails(authorizationDetails: AuthorizationDetails): Boolean {
        return authorizationDetails.type == OPENID_CREDENTIAL_AUTHORIZATION_TYPE &&
                config.credentialConfigurationsSupported.values.any { credentialSupported ->
                    credentialSupported.format == authorizationDetails.format &&
                            ((authorizationDetails.types != null && credentialSupported.types?.containsAll(
                                authorizationDetails.types
                            ) == true) ||
                                    (authorizationDetails.docType != null && credentialSupported.docType == authorizationDetails.docType)
                                    )
                    // TODO: check other supported credential parameters
                }
    }

    override fun validateAuthorizationRequest(authorizationRequest: AuthorizationRequest): Boolean {
        return authorizationRequest.authorizationDetails != null && authorizationRequest.authorizationDetails.any {
            isSupportedAuthorizationDetails(it)
        }
    }

    override fun initializeAuthorization(
        authorizationRequest: AuthorizationRequest,
        expiresIn: Duration,
        idTokenRequestState: String?,
    ): IssuanceSession {
        return if (authorizationRequest.issuerState.isNullOrEmpty()) {
            if (!validateAuthorizationRequest(authorizationRequest)) {
                throw AuthorizationError(
                    authorizationRequest, AuthorizationErrorCode.invalid_request,
                    "No valid authorization details for credential issuance found on authorization request"
                )
            }
            IssuanceSession(
                randomUUID(), authorizationRequest,
                Clock.System.now().plus(expiresIn), idTokenRequestState = idTokenRequestState
            )
        } else {
            getVerifiedSession(authorizationRequest.issuerState)?.copy(authorizationRequest = authorizationRequest)
                ?: throw AuthorizationError(
                    authorizationRequest, AuthorizationErrorCode.invalid_request,
                    "No valid issuance session found for given issuer state"
                )
        }.also {
            val updatedSession = IssuanceSession(
                id = it.id,
                authorizationRequest = authorizationRequest,
                expirationTimestamp = Clock.System.now().plus(5.minutes),
                idTokenRequestState = idTokenRequestState,
                txCode = it.txCode,
                txCodeValue = it.txCodeValue,
                credentialOffer = it.credentialOffer,
                cNonce = it.cNonce,
                customParameters = it.customParameters
            )
            putSession(it.id, updatedSession)
        }
    }


    open fun initializeCredentialOffer(
        credentialOfferBuilder: CredentialOffer.Builder,
        expiresIn: Duration,
        allowPreAuthorized: Boolean,
        txCode: TxCode? = null, txCodeValue: String? = null
    ): IssuanceSession {
        val sessionId = randomUUID()
        credentialOfferBuilder.addAuthorizationCodeGrant(sessionId)
        if (allowPreAuthorized)
            credentialOfferBuilder.addPreAuthorizedCodeGrant(
                generateToken(sessionId, TokenTarget.TOKEN),
                txCode
            )
        return IssuanceSession(
            id = sessionId,
            authorizationRequest = null,
            expirationTimestamp = Clock.System.now().plus(expiresIn),
            txCode = txCode,
            txCodeValue = txCodeValue,
            credentialOffer = credentialOfferBuilder.build()
        ).also {
            putSession(it.id, it)
        }
    }

    private fun generateProofOfPossessionNonceFor(session: IssuanceSession): IssuanceSession {
        return session.copy(
            cNonce = randomUUID()
        ).also {
            putSession(it.id, it)
        }
    }

    override fun generateTokenResponse(session: IssuanceSession, tokenRequest: TokenRequest): TokenResponse {
        if (tokenRequest.grantType == GrantType.pre_authorized_code && session.txCode != null &&
            session.txCodeValue != tokenRequest.txCode
        ) {
            throw TokenError(
                tokenRequest,
                TokenErrorCode.invalid_grant,
                message = "User PIN required for this issuance session has not been provided or PIN is wrong."
            )
        }
        return super.generateTokenResponse(session, tokenRequest).copy(
            cNonce = generateProofOfPossessionNonceFor(session).cNonce,
            cNonceExpiresIn = session.expirationTimestamp - Clock.System.now()
            // TODO: authorization_pending, interval
        )
    }

    private fun createCredentialError(
        credReq: CredentialRequest, session: IssuanceSession,
        errorCode: CredentialErrorCode, message: String?
    ) =
        CredentialError(
            credReq, errorCode, null,
            // renew c_nonce for this session, if the error was invalid_or_missing_proof
            cNonce = if (errorCode == CredentialErrorCode.invalid_or_missing_proof) generateProofOfPossessionNonceFor(
                session
            ).cNonce else null,
            cNonceExpiresIn = if (errorCode == CredentialErrorCode.invalid_or_missing_proof) session.expirationTimestamp - Clock.System.now() else null,
            message = message
        )

    open fun generateCredentialResponse(credentialRequest: CredentialRequest, accessToken: String): CredentialResponse {
        val accessInfo = verifyAndParseToken(accessToken, TokenTarget.ACCESS) ?: throw CredentialError(
            credentialRequest,
            CredentialErrorCode.invalid_token,
            message = "Invalid access token"
        )
        val sessionId = accessInfo[JWTClaims.Payload.subject]!!.jsonPrimitive.content
        val session = getVerifiedSession(sessionId) ?: throw CredentialError(
            credentialRequest,
            CredentialErrorCode.invalid_token,
            "Session not found for given access token, or session expired."
        )
        return doGenerateCredentialResponseFor(credentialRequest, session)
    }

    private fun doGenerateCredentialResponseFor(
        credentialRequest: CredentialRequest,
        session: IssuanceSession
    ): CredentialResponse {
        val nonce = session.cNonce ?: throw createCredentialError(
            credentialRequest,
            session,
            CredentialErrorCode.invalid_request,
            "Session invalid"
        )
        println("Credential request to validate: $credentialRequest")
        if (credentialRequest.proof == null || !validateProofOfPossession(credentialRequest, nonce)) {
            throw createCredentialError(
                credentialRequest,
                session,
                CredentialErrorCode.invalid_or_missing_proof,
                "Invalid proof of possession"
            )
        }

        if (!supportedCredentialFormats.contains(credentialRequest.format))
            throw createCredentialError(
                credentialRequest,
                session,
                CredentialErrorCode.unsupported_credential_format,
                "Credential format not supported"
            )

        // check types, credential_definition.types, docType, one of them must be supported
        /*val types = credentialRequest.types ?: credentialRequest.credentialDefinition?.types
        if (!isCredentialTypeSupported(credentialRequest.format, types, credentialRequest.docType))
            throw createCredentialError(
                credentialRequest,
                session,
                CredentialErrorCode.unsupported_credential_type,
                "Credential type not supported: format=${credentialRequest.format}, types=$types, docType=${credentialRequest.docType}"
            )

         */

        // TODO: validate if requested credential was authorized
        //  (by authorization details, or credential offer, or scope)

        // issue credential for credential request
        return createCredentialResponseFor(generateCredential(credentialRequest), session)
    }

    open fun generateDeferredCredentialResponse(acceptanceToken: String): CredentialResponse {
        val accessInfo =
            verifyAndParseToken(acceptanceToken, TokenTarget.DEFERRED_CREDENTIAL) ?: throw DeferredCredentialError(
                CredentialErrorCode.invalid_token,
                message = "Invalid acceptance token"
            )
        val sessionId = accessInfo[JWTClaims.Payload.subject]!!.jsonPrimitive.content
        val credentialId = accessInfo[JWTClaims.Payload.jwtID]!!.jsonPrimitive.content
        val session = getVerifiedSession(sessionId) ?: throw DeferredCredentialError(
            CredentialErrorCode.invalid_token,
            "Session not found for given access token, or session expired."
        )
        // issue credential for credential request
        return createCredentialResponseFor(getDeferredCredential(credentialId), session)
    }

    open fun generateBatchCredentialResponse(
        batchCredentialRequest: BatchCredentialRequest,
        accessToken: String
    ): BatchCredentialResponse {
        val accessInfo = verifyAndParseToken(accessToken, TokenTarget.ACCESS) ?: throw BatchCredentialError(
            batchCredentialRequest,
            CredentialErrorCode.invalid_token,
            message = "Invalid access token"
        )
        val sessionId = accessInfo[JWTClaims.Payload.subject]!!.jsonPrimitive.content
        val session = getVerifiedSession(sessionId) ?: throw BatchCredentialError(
            batchCredentialRequest,
            CredentialErrorCode.invalid_token,
            "Session not found for given access token, or session expired."
        )

        try {
            val responses = batchCredentialRequest.credentialRequests.map {
                doGenerateCredentialResponseFor(it, session)
            }
            return generateProofOfPossessionNonceFor(session).let { updatedSession ->
                BatchCredentialResponse.success(
                    responses,
                    updatedSession.cNonce,
                    updatedSession.expirationTimestamp - Clock.System.now()
                )
            }
        } catch (error: CredentialError) {
            throw BatchCredentialError(
                batchCredentialRequest,
                error.errorCode,
                error.errorUri,
                error.cNonce,
                error.cNonceExpiresIn,
                error.message
            )
        }
    }

    override fun verifyAndParseToken(token: String, target: TokenTarget): JsonObject? {
        return super.verifyAndParseToken(token, target)?.let {
            if (target == TokenTarget.DEFERRED_CREDENTIAL && !it.containsKey(JWTClaims.Payload.jwtID))
                null
            else it
        }
    }

    private fun createDeferredCredentialToken(session: AuthorizationSession, credentialResult: CredentialResult) =
        generateToken(
            session.id, TokenTarget.DEFERRED_CREDENTIAL,
            credentialResult.credentialId
                ?: throw Exception("credentialId must not be null, if credential issuance is deferred.")
        )

    private fun createCredentialResponseFor(
        credentialResult: CredentialResult,
        session: IssuanceSession
    ): CredentialResponse {
        return credentialResult.credential?.let {
            CredentialResponse.success(credentialResult.format, it)
        } ?: generateProofOfPossessionNonceFor(session).let { updatedSession ->
            CredentialResponse.deferred(
                credentialResult.format,
                createDeferredCredentialToken(session, credentialResult),
                updatedSession.cNonce,
                updatedSession.expirationTimestamp - Clock.System.now()
            )
        }
    }

    private fun validateProofOfPossession(credentialRequest: CredentialRequest, nonce: String): Boolean {
        println("VALIDATING: ${credentialRequest.proof} with nonce $nonce")
        if (credentialRequest.proof?.proofType != ProofType.jwt || credentialRequest.proof.jwt == null)
            return false
        println("VERIFYING ITS SIGNATURE")
        return verifyTokenSignature(TokenTarget.PROOF_OF_POSSESSION, credentialRequest.proof.jwt) &&
                credentialRequest.proof.jwt.let {
                    parseTokenPayload(it)
                }[JWTClaims.Payload.nonce]?.jsonPrimitive?.content == nonce
    }

    fun getCIProviderMetadataUrl(): String {
        return URLBuilder(baseUrl).apply {
            pathSegments = listOf(".well-known", "openid-credential-issuer")
        }.buildString()
    }

    open fun getCredentialOfferRequestUrl(
        offerRequest: CredentialOfferRequest,
        walletCredentialOfferEndpoint: String = CROSS_DEVICE_CREDENTIAL_OFFER_URL
    ): String {
        val url = URLBuilder(walletCredentialOfferEndpoint).apply {
            parameters.appendAll(parametersOf(offerRequest.toHttpParameters()))
        }.buildString()

        println("CREATED URL: $url")

        return url
    }

    /**
     * Returns the URI on which the credential offer object can be retrieved for this issuance session, if the request object is passed by reference.
     * The returned URI will be used for the credential_offer_uri parameter of the credential offer request.
     * Override, to use custom path, by default, the path will be: "$baseUrl/credential_offer/<session_id>, e.g.: "https://issuer.myhost.com/api/credential_offer/1234-4567-8900"
     * @param issuanceSession   The issuance session for which the credential offer uri is created
     */
    protected open fun getCredentialOfferByReferenceUri(issuanceSession: IssuanceSession): String {
        return URLBuilder(baseUrl).appendPathSegments("credential_offer", issuanceSession.id).buildString()
    }

    open fun getCredentialOfferRequest(
        issuanceSession: IssuanceSession, byReference: Boolean = false
    ): CredentialOfferRequest {
        return if (byReference) {
            CredentialOfferRequest(null, getCredentialOfferByReferenceUri(issuanceSession))
        } else {
            CredentialOfferRequest(issuanceSession.credentialOffer)
        }
    }
}
