@file:OptIn(ExperimentalEncodingApi::class)

package id.walt.verifier.oidc

import id.walt.crypto.exceptions.CryptoArgumentException
import id.walt.crypto.keys.KeyGenerationRequest
import id.walt.crypto.keys.KeyManager
import id.walt.crypto.keys.KeyType
import id.walt.crypto.utils.JsonUtils.toJsonElement
import id.walt.oid4vc.data.ClientIdScheme
import id.walt.oid4vc.data.OpenId4VPProfile
import id.walt.oid4vc.data.ResponseMode
import id.walt.oid4vc.data.ResponseType
import id.walt.oid4vc.data.dif.PresentationDefinition
import id.walt.oid4vc.providers.PresentationSession
import id.walt.oid4vc.responses.TokenResponse
import id.walt.policies.models.PolicyRequest
import id.walt.policies.models.PolicyRequest.Companion.parsePolicyRequests
import id.walt.policies.policies.JwtSignaturePolicy
import id.walt.policies.policies.SdJwtVCSignaturePolicy
import id.walt.sdjwt.JWTCryptoProvider
import id.walt.verifier.oidc.models.presentedcredentials.PresentationSessionPresentedCredentials
import id.walt.verifier.oidc.models.presentedcredentials.PresentedCredentialsViewMode
import id.walt.w3c.utils.VCFormat
import io.klogging.logger
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.logging.SIMPLE
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.plugins.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.Duration


object VerifierService {
    private val logger = logger("Verification")

    private val http = HttpClient {

        install(ContentNegotiation) {
            json()
        }

        install(Logging) {
            logger = Logger.SIMPLE
            level = LogLevel.ALL
        }
    }

    suspend fun createSession(
        vpPoliciesJson: JsonElement?,
        vcPoliciesJson: JsonElement?,
        requestCredentialsJson: JsonElement,
        responseMode: ResponseMode,
        responseType: ResponseType? = ResponseType.VpToken,
        successRedirectUri: String?,
        errorRedirectUri: String?,
        statusCallbackUri: String?,
        statusCallbackApiKey: String?,
        stateId: String?,
        openId4VPProfile: OpenId4VPProfile = OpenId4VPProfile.DEFAULT,
        walletInitiatedAuthState: String? = null,
        trustedRootCAs: JsonArray? = null,
        sessionTtl: Duration? = null,
    ) = let {
        val requestedCredentials = requestCredentialsJson.jsonArray.map {
            when (it) {
                is JsonObject -> Json.decodeFromJsonElement<RequestedCredential>(it)
                else -> throw IllegalArgumentException("Invalid JSON type for requested credential: $it")
            }
        }

        val presentationFormat = getPresentationFormat(requestedCredentials)

        val vpPolicies =
            vpPoliciesJson?.jsonArray?.parsePolicyRequests() ?: getDefaultVPPolicyRequests(presentationFormat)
        val vcPolicies = vcPoliciesJson?.jsonArray?.parsePolicyRequests() ?: getDefaultVCPolicies(presentationFormat)

        val presentationDefinition = PresentationDefinition(
            inputDescriptors = requestedCredentials.map { it.toInputDescriptor() }
        )

        logger.debug { "Presentation definition: " + presentationDefinition.toJSON() }

        val session = OIDCVerifierService.initializeAuthorization(
            presentationDefinition = presentationDefinition,
            responseMode = responseMode,
            sessionId = stateId,
            ephemeralEncKey = when (responseMode) {
                ResponseMode.direct_post_jwt -> runBlocking { KeyManager.createKey(KeyGenerationRequest(keyType = KeyType.secp256r1)) }
                else -> null
            },
            clientIdScheme = this.getClientIdScheme(
                openId4VPProfile = openId4VPProfile,
                defaultClientIdScheme = OIDCVerifierService.config.defaultClientIdScheme
            ),
            openId4VPProfile = openId4VPProfile,
            walletInitiatedAuthState = walletInitiatedAuthState,
            responseType = responseType,
            trustedRootCAs = trustedRootCAs?.map { it.jsonPrimitive.content }
        )

        val specificPolicies = requestedCredentials.filter {
            !it.policies.isNullOrEmpty()
        }.associate {
            it.id to it.policies!!.parsePolicyRequests()
        }

        OIDCVerifierService.sessionVerificationInfos.set(
            id = session.id,
            value = OIDCVerifierService.SessionVerificationInformation(
                vpPolicies = vpPolicies,
                vcPolicies = vcPolicies,
                specificPolicies = specificPolicies,
                successRedirectUri = successRedirectUri,
                errorRedirectUri = errorRedirectUri,
                walletInitiatedAuthState = walletInitiatedAuthState,
                statusCallback = statusCallbackUri?.let {
                    OIDCVerifierService.StatusCallback(
                        statusCallbackUri = it,
                        statusCallbackApiKey = statusCallbackApiKey,
                    )
                }
            ),
            ttl = sessionTtl
        )

        session
    }

    suspend fun createSession(
        vpPoliciesJson: JsonElement?,
        vcPoliciesJson: JsonElement?,
        presentationDefinitionJson: JsonObject,
        responseMode: ResponseMode,
        responseType: ResponseType? = ResponseType.VpToken,
        successRedirectUri: String?,
        errorRedirectUri: String?,
        statusCallbackUri: String?,
        statusCallbackApiKey: String?,
        stateId: String?,
        openId4VPProfile: OpenId4VPProfile = OpenId4VPProfile.DEFAULT,
        walletInitiatedAuthState: String? = null,
        trustedRootCAs: JsonArray? = null,
        sessionTtl: Duration? = null,
    ): PresentationSession {
        return createSession(
            vpPoliciesJson = vpPoliciesJson,
            vcPoliciesJson = vcPoliciesJson,
            requestCredentialsJson = JsonArray(
                Json.decodeFromJsonElement<PresentationDefinition>(
                    presentationDefinitionJson
                ).inputDescriptors.map {
                    RequestedCredential(inputDescriptor = it, policies = null).toJsonElement()
                }),
            responseMode = responseMode,
            responseType = responseType,
            successRedirectUri = successRedirectUri,
            errorRedirectUri = errorRedirectUri,
            statusCallbackUri = statusCallbackUri,
            statusCallbackApiKey = statusCallbackApiKey,
            stateId = stateId,
            openId4VPProfile = openId4VPProfile,
            walletInitiatedAuthState = walletInitiatedAuthState,
            trustedRootCAs = trustedRootCAs,
            sessionTtl = sessionTtl
        )
    }

    fun getSession(sessionId: String): PresentationSession = OIDCVerifierService.getSession(sessionId)

    data class FailedVerificationException(
        val redirectUrl: String?,
        override val cause: Throwable?,
        override val message: String = cause?.message ?: "Verification failed",
    ) : IllegalArgumentException()

    suspend fun verify(sessionId: String, tokenResponseParameters: Map<String, List<String>>): Result<String> {
        logger.debug { "Verifying session $sessionId" }
        val session = OIDCVerifierService.getSession(sessionId)

        val tokenResponse = when (TokenResponse.isDirectPostJWT(tokenResponseParameters)) {
            true -> TokenResponse.fromDirectPostJWT(
                parameters = tokenResponseParameters,
                encKeyJwk = runBlocking { session.ephemeralEncKey?.exportJWKObject() }
                    ?: throw IllegalArgumentException("No ephemeral reader key found on session"))

            else -> TokenResponse.fromHttpParameters(tokenResponseParameters)
        }
        val sessionVerificationInfo = OIDCVerifierService.sessionVerificationInfos[session.id]
            ?: return Result.failure(
                NotFoundException("No session verification information found for session id: ${session.id}")
            )

        val maybePresentationSessionResult = runCatching { OIDCVerifierService.verify(tokenResponse, session) }

        if (maybePresentationSessionResult.isFailure) {
            return Result.failure(
                CryptoArgumentException(
                    "Verification failed (VerificationUseCase): ${maybePresentationSessionResult.exceptionOrNull()!!.message}",
                    maybePresentationSessionResult.exceptionOrNull()
                )
            )
        }

        val presentationSession = maybePresentationSessionResult.getOrThrow()

        return if (presentationSession.verificationResult == true) {
            val redirectUri = sessionVerificationInfo.successRedirectUri?.replace("\$id", session.id) ?: ""
            logger.debug { "Presentation is successful, redirecting to: $redirectUri" }
            Result.success(redirectUri)
        } else {
            val policyResults = OIDCVerifierService.policyResults[session.id]
            val redirectUri = sessionVerificationInfo.errorRedirectUri?.replace("\$id", session.id)

            logger.debug { "Presentation failed, redirecting to: $redirectUri" }


            return if (policyResults == null) {
                Result.failure(
                    FailedVerificationException(
                        redirectUri,
                        IllegalArgumentException("Verification policies did not succeed")
                    )
                )
            } else {
                val failedPolicies =
                    policyResults.results.flatMap { it.policyResults.map { it } }.filter { !it.isSuccess }
                val errorCause =
                    IllegalArgumentException("Verification policies did not succeed: ${failedPolicies.joinToString { it.policy + " (${it.error})" }}")

                Result.failure(FailedVerificationException(redirectUri, errorCause))
            }
        }
    }

    fun getResult(sessionId: String): Result<PresentationSessionInfo> {
        val session = OIDCVerifierService.getSession(sessionId)

        val policyResults =
            OIDCVerifierService.policyResults[session.id]?.let { Json.encodeToJsonElement(it).jsonObject }

        return Result.success(
            PresentationSessionInfo.fromPresentationSession(
                session = session,
                policyResults = policyResults
            )
        )
    }

    fun getPresentationDefinition(sessionId: String): Result<PresentationDefinition> =
        OIDCVerifierService.getSession(sessionId).presentationDefinition.let {
            Result.success(it)
        }


    fun getSignedAuthorizationRequestObject(sessionId: String): Result<String> = runCatching {
        checkNotNull(OIDCVerifierService.getSession(sessionId).authorizationRequest) {
            "No authorization request found for session id: $sessionId"
        }
        OIDCVerifierService.getSession(sessionId).authorizationRequest!!.toRequestObject(
            RequestSigningCryptoProvider, RequestSigningCryptoProvider.signingKey.keyID.orEmpty()
        )
    }

    fun getSessionPresentedCredentials(
        sessionId: String,
        viewMode: PresentedCredentialsViewMode,
    ) = runCatching {
        val session = OIDCVerifierService.getSession(sessionId)

        check(session.verificationResult == true) {
            "Presented credentials can only be retrieved for sessions whose vp_token has been successfully verified"
        }

        val tokenResponse = checkNotNull(session.tokenResponse) {
            "It should be impossible to have a null token response and a successful verification result - bug!"
        }

        val vpTokenStringified = checkNotNull(tokenResponse.vpToken) {
            "It should be impossible to have a null vp_token response and a successful verification result - bug!"
        }.jsonPrimitive.content

        val format = checkNotNull(
            tokenResponse.presentationSubmission?.descriptorMap?.firstOrNull()?.format
                ?: tokenResponse.presentationSubmission?.descriptorMap?.firstOrNull()?.pathNested?.format
        ) {
            "No presentation submission or presentation format found for session id: $sessionId"
        }

        PresentationSessionPresentedCredentials.fromVpTokenStringsByFormat(
            vpTokenStringsByFormat = mapOf(
                when (format) {
                    VCFormat.jwt_vp_json, VCFormat.jwt_vp, VCFormat.jwt_vc_json -> {
                        VCFormat.jwt_vc_json to listOf(vpTokenStringified)
                    }

                    else -> {
                        format to listOf(vpTokenStringified)
                    }
                }
            ),
            viewMode = viewMode,
        )
    }


    suspend fun notifySubscribers(sessionId: String) = runCatching {
        OIDCVerifierService.sessionVerificationInfos[sessionId]?.statusCallback?.let {
            http.post(it.statusCallbackUri) {
                header(HttpHeaders.ContentType, ContentType.Application.Json)
                it.statusCallbackApiKey?.let { bearerAuth(it) }
                setBody(getResult(sessionId).getOrThrow())
            }.also {
                logger.debug { "status callback: ${it.status}" }
            }
        }
    }

    private fun getClientIdScheme(
        openId4VPProfile: OpenId4VPProfile,
        defaultClientIdScheme: ClientIdScheme,
    ): ClientIdScheme {
        return when (openId4VPProfile) {
            OpenId4VPProfile.ISO_18013_7_MDOC -> ClientIdScheme.X509SanDns
            else -> defaultClientIdScheme
        }
    }

    private fun getPresentationFormat(requestedCredentials: List<RequestedCredential>): VCFormat {
        val credentialFormat =
            requestedCredentials.map { it.format ?: it.inputDescriptor?.format?.keys?.first() }.distinct()
                .singleOrNull()
        requireNotNull(credentialFormat) {
            "Credentials formats must be distinct for a presentation request"
        }
        return when (credentialFormat) {
            VCFormat.mso_mdoc -> VCFormat.mso_mdoc
            VCFormat.sd_jwt_vc -> VCFormat.sd_jwt_vc
            VCFormat.ldp_vc, VCFormat.ldp -> VCFormat.ldp_vp
            VCFormat.jwt_vc, VCFormat.jwt -> VCFormat.jwt_vp
            VCFormat.jwt_vc_json -> VCFormat.jwt_vp_json
            else -> throw IllegalArgumentException("Credentials format $credentialFormat is not a valid format for a requested credential")
        }
    }

    private fun getDefaultVPPolicyRequests(presentationFormat: VCFormat): List<PolicyRequest> =
        when (presentationFormat) {
            //VCFormat.mso_mdoc -> TODO()
            VCFormat.sd_jwt_vc -> listOf(PolicyRequest(SdJwtVCSignaturePolicy()))
            else -> listOf(PolicyRequest(JwtSignaturePolicy()))
        }

    private fun getDefaultVCPolicies(presentationFormat: VCFormat): List<PolicyRequest> = when (presentationFormat) {
        //VCFormat.mso_mdoc -> TODO()
        VCFormat.sd_jwt_vc -> listOf()
        else -> listOf(PolicyRequest(JwtSignaturePolicy()))
    }
}
