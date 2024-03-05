package id.walt.oid4vc

import id.walt.oid4vc.data.CredentialOffer
import id.walt.oid4vc.data.GrantType
import id.walt.oid4vc.data.OfferedCredential
import id.walt.oid4vc.data.OpenIDProviderMetadata
import id.walt.oid4vc.definitions.CROSS_DEVICE_CREDENTIAL_OFFER_URL
import id.walt.oid4vc.definitions.JWTClaims
import id.walt.oid4vc.errors.TokenError
import id.walt.oid4vc.interfaces.ITokenProvider
import id.walt.oid4vc.providers.IssuanceSession
import id.walt.oid4vc.providers.TokenTarget
import id.walt.oid4vc.requests.CredentialOfferRequest
import id.walt.oid4vc.requests.TokenRequest
import id.walt.oid4vc.responses.TokenErrorCode
import id.walt.oid4vc.responses.TokenResponse
import id.walt.oid4vc.util.http
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.*
import kotlinx.datetime.Clock
import kotlinx.serialization.json.*

object OpenID4VCI {
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

    fun parseCredentialOfferRequestUrl(credOfferReqUrl: String): CredentialOfferRequest {
        return CredentialOfferRequest.fromHttpParameters(Url(credOfferReqUrl).parameters.toMap())
    }

    suspend fun parseAndResolveCredentialOfferRequestUrl(credOfferReqUrl: String): CredentialOffer {
        val offerReq = parseCredentialOfferRequestUrl(credOfferReqUrl)
        return if (offerReq.credentialOffer != null) {
            offerReq.credentialOffer
        } else if (!offerReq.credentialOfferUri.isNullOrEmpty()) {

            http.get(offerReq.credentialOfferUri).bodyAsText().let {
                CredentialOffer.fromJSONString(it)
            }
        } else throw Exception("Credential offer request has no credential offer object set by value or reference.")
    }

    fun getCIProviderMetadataUrl(credOffer: CredentialOffer): String {
        return getCIProviderMetadataUrl(credOffer.credentialIssuer)
    }

    fun getCIProviderMetadataUrl(baseUrl: String): String {
        return URLBuilder(baseUrl).apply {
            appendPathSegments(".well-known", "openid-credential-issuer")
        }.buildString()
    }

    fun getCommonProviderMetadataUrl(baseUrl: String): String {
        return URLBuilder(baseUrl).apply {
            appendPathSegments(".well-known", "openid-configuration")
        }.buildString()
    }

    suspend fun resolveCIProviderMetadata(credOffer: CredentialOffer): OpenIDProviderMetadata {
        return http.get(getCIProviderMetadataUrl(credOffer)).bodyAsText().let {
            OpenIDProviderMetadata.fromJSONString(it)
        }
    }

    fun resolveOfferedCredentials(credentialOffer: CredentialOffer, providerMetadata: OpenIDProviderMetadata): List<OfferedCredential> {
        val supportedCredentials =
            providerMetadata.credentialsSupported?.filter { !it.id.isNullOrEmpty() }?.associateBy { it.id!! } ?: mapOf()
        return credentialOffer.credentialConfigurationIds.mapNotNull { c ->
            supportedCredentials[c]?.let {
                OfferedCredential.fromProviderMetadata(it)
            }
        }
    }

//    fun processTokenRequest(tokenRequest: TokenRequest): TokenResponse {
//        val code = when (tokenRequest.grantType) {
//            GrantType.authorization_code -> tokenRequest.code ?: throw TokenError(
//                tokenRequest = tokenRequest,
//                errorCode = TokenErrorCode.invalid_grant,
//                message = "No code parameter found on token request"
//            )
//
//            GrantType.pre_authorized_code -> tokenRequest.preAuthorizedCode ?: throw TokenError(
//                tokenRequest = tokenRequest,
//                errorCode = TokenErrorCode.invalid_grant,
//                message = "No pre-authorized_code parameter found on token request"
//            )
//
//            else -> throw TokenError(tokenRequest, TokenErrorCode.unsupported_grant_type, "Grant type not supported")
//        }
//        val payload = validateAuthorizationCode(code) ?: throw TokenError(
//            tokenRequest = tokenRequest,
//            errorCode = TokenErrorCode.invalid_grant,
//            message = "Authorization code could not be verified"
//        )
//
//        val sessionId = payload["sub"]!!.jsonPrimitive.content
//        val session = getVerifiedSession(sessionId) ?: throw TokenError(
//            tokenRequest = tokenRequest,
//            errorCode = TokenErrorCode.invalid_request,
//            message = "No authorization session found for given authorization code, or session expired."
//        )
//
//        return generateTokenResponse(session, tokenRequest)
//    }
//
//    private fun generateTokenResponse(session: IssuanceSession, tokenRequest: TokenRequest, tokenProvider: ITokenProvider): TokenResponse {
//        if (tokenRequest.grantType == GrantType.pre_authorized_code && !session.preAuthUserPin.isNullOrEmpty() &&
//            session.preAuthUserPin != tokenRequest.userPin
//        ) {
//            throw TokenError(
//                tokenRequest,
//                TokenErrorCode.invalid_grant,
//                message = "User PIN required for this issuance session has not been provided or PIN is wrong."
//            )
//        }
//        return TokenResponse.success(
//            generateToken(tokenProvider, session.id, TokenTarget.ACCESS, session.issuer),
//            "bearer", state = session.authorizationRequest?.state,
//            cNonce = session.cNonce,
//            cNonceExpiresIn = session.expirationTimestamp - Clock.System.now()
//            // TODO: authorization_pending, interval
//        )
//    }
//
//    private fun generateToken(tokenProvider: ITokenProvider, sub: String, audience: TokenTarget, issuer: String, tokenId: String? = null): String {
//        return tokenProvider.signToken(audience, buildJsonObject {
//            put(JWTClaims.Payload.subject, sub)
//            put(JWTClaims.Payload.issuer, issuer)
//            put(JWTClaims.Payload.audience, audience.name)
//            tokenId?.let { put(JWTClaims.Payload.jwtID, it) }
//        })
//    }

}
