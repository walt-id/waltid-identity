package id.walt.entrawallet.core.service

import com.nimbusds.jose.jwk.JWK
import com.nimbusds.jose.jwk.KeyUse
import com.nimbusds.jose.util.Base64URL
import id.walt.crypto.keys.KeyGenerationRequest
import id.walt.crypto.keys.KeyManager
import id.walt.crypto.keys.KeyType
import id.walt.did.dids.DidService
import id.walt.did.dids.registrar.LocalRegistrar
import id.walt.did.dids.resolver.LocalResolver
import id.walt.entrawallet.core.service.exchange.IssuanceService
import id.walt.entrawallet.core.service.exchange.PresentationRequestParameter
import id.walt.entrawallet.core.service.oidc4vc.TestCredentialWallet
import id.walt.entrawallet.core.service.oidc4vc.VPresentationSession
import id.walt.entrawallet.core.utils.SessionAttributes
import id.walt.oid4vc.data.CredentialOffer
import id.walt.oid4vc.data.ResponseMode
import id.walt.oid4vc.errors.AuthorizationError
import id.walt.oid4vc.providers.CredentialWalletConfig
import id.walt.oid4vc.providers.OpenIDClientConfig
import id.walt.oid4vc.requests.AuthorizationRequest
import id.walt.oid4vc.requests.CredentialOfferRequest
import id.walt.oid4vc.responses.AuthorizationErrorCode
import id.walt.oid4vc.responses.TokenResponse
import id.walt.webwallet.utils.StringUtils.couldBeJsonObject
import id.walt.webwallet.utils.StringUtils.parseAsJsonObject
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Duration.Companion.seconds

class SSIKit2WalletService(
    private val http: HttpClient,
) {
    private val logger = KotlinLogging.logger { }

    companion object {
        init {
            runBlocking {
                DidService.apply {
                    registerResolver(LocalResolver())
                    updateResolversForMethods()
                    registerRegistrar(LocalRegistrar())
                    updateRegistrarsForMethods()
                }
            }
        }

        val testCIClientConfig = OpenIDClientConfig("test-client", null, redirectUri = "http://blank")
        private val credentialWallets = HashMap<String, TestCredentialWallet>()
        fun getCredentialWallet(did: String) = credentialWallets.getOrPut(did) {
            TestCredentialWallet(
                CredentialWalletConfig("http://blank"), did
            )
        }
    }

    /* SIOP */
    @Serializable
    data class PresentationResponse(
        val vp_token: String,
        val presentation_submission: String,
        val id_token: String?,
        val state: String?,
        val fulfilled: Boolean,
        val rp_response: String?,
    )

    data class PresentationError(override val message: String, val redirectUri: String?) :
        IllegalArgumentException(message)


    /**
     * @return redirect uri
     */
    suspend fun usePresentationRequest(parameter: PresentationRequestParameter): Result<String?> {
        val credentialWallet = getCredentialWallet(parameter.did)

        val authReq =
            AuthorizationRequest.fromHttpParametersAuto(parseQueryString(Url(parameter.request).encodedQuery).toMap())
        logger.debug { "Auth req: $authReq" }

        SessionAttributes.HACK_outsideMappedKey[authReq.state ?: error("missing required state")] =
            parameter.key

        logger.debug { "Using presentation request, selected credentials: ${parameter.selectedCredentials}" }

        val presentationSession =
            credentialWallet.initializeAuthorization(authReq, 60.seconds, parameter.selectedCredentials.toSet())
        logger.debug { "Initialized authorization (VPPresentationSession): $presentationSession" }

        logger.debug { "Resolved presentation definition: ${presentationSession.authorizationRequest!!.presentationDefinition!!.toJSONString()}" }

        SessionAttributes.HACK_outsideMappedSelectedCredentialsPerSession[presentationSession.authorizationRequest!!.state + presentationSession.authorizationRequest.presentationDefinition?.id] =
            parameter.selectedCredentials


        if (parameter.disclosures != null) {
            SessionAttributes.HACK_outsideMappedSelectedDisclosuresPerSession[presentationSession.authorizationRequest.state + presentationSession.authorizationRequest.presentationDefinition?.id] =
                parameter.disclosures
        }

        val tokenResponse = credentialWallet.processImplicitFlowAuthorization(presentationSession.authorizationRequest)
        val submitFormParams = getFormParameters(presentationSession.authorizationRequest, tokenResponse, presentationSession)

        val resp = this.http.submitForm(
            presentationSession.authorizationRequest.responseUri
                ?: presentationSession.authorizationRequest.redirectUri ?: throw AuthorizationError(
                    presentationSession.authorizationRequest,
                    AuthorizationErrorCode.invalid_request,
                    "No response_uri or redirect_uri found on authorization request"
                ), parameters {
                submitFormParams.forEach { entry ->
                    entry.value.forEach { append(entry.key, it) }
                }
            })
        val httpResponseBody = runCatching { resp.bodyAsText() }.getOrNull()
        val isResponseRedirectUrl = httpResponseBody != null && httpResponseBody.take(10).lowercase().let {
            @Suppress("HttpUrlsUsage")
            it.startsWith("http://") || it.startsWith("https://")
        }
        logger.debug { "HTTP Response: $resp, body: $httpResponseBody" }

        return if (resp.status.value == 302 && !resp.headers["location"].toString().contains("error")) {
            Result.success(if (isResponseRedirectUrl) httpResponseBody else null)
        } else if (resp.status.isSuccess()) {
            Result.success(if (isResponseRedirectUrl) httpResponseBody else null)
        } else {
            logger.debug { "Presentation failed, return = $httpResponseBody" }
            if (isResponseRedirectUrl) {
                Result.failure(
                    PresentationError(
                        message = "Presentation failed - redirecting to error page",
                        redirectUri = httpResponseBody
                    )
                )
            } else {
                logger.debug { "Response body: $httpResponseBody" }
                Result.failure(
                    PresentationError(
                        message =
                        httpResponseBody?.let {
                            if (it.couldBeJsonObject()) it.parseAsJsonObject().getOrNull()?.get("message")?.jsonPrimitive?.content
                                ?: "Presentation failed"
                            else it
                        } ?: "Presentation failed",
                        redirectUri = ""
                    )
                )
            }
        }
    }

    suspend fun resolvePresentationRequest(request: String): String {
        val credentialWallet = getAnyCredentialWallet()

        return Url(request)
            .protocolWithAuthority
            .plus("?")
            .plus(credentialWallet.parsePresentationRequest(request).toHttpQueryString())
    }

    private fun getAnyCredentialWallet() =
        credentialWallets.values.firstOrNull() ?: getCredentialWallet("did:test:test")

    suspend fun resolveVct(vct: String) = IssuanceService.resolveVct(vct)

    fun resolveCredentialOffer(
        offerRequest: CredentialOfferRequest,
    ): CredentialOffer {
        return getAnyCredentialWallet().resolveCredentialOffer(offerRequest)
    }

    private fun getFormParameters(
        authorizationRequest: AuthorizationRequest,
        tokenResponse: TokenResponse,
        presentationSession: VPresentationSession
    ) = if (authorizationRequest.responseMode == ResponseMode.direct_post_jwt) {
        directPostJwtParameters(authorizationRequest, tokenResponse, presentationSession)
    } else tokenResponse.toHttpParameters()

    private fun directPostJwtParameters(
        authorizationRequest: AuthorizationRequest,
        tokenResponse: TokenResponse,
        presentationSession: VPresentationSession
    ): Map<String, List<String>> {
        val encKey = authorizationRequest.clientMetadata?.jwks?.get("keys")?.jsonArray?.first { jwk ->
            JWK.parse(jwk.toString()).keyUse?.equals(KeyUse.ENCRYPTION) ?: false
        }?.jsonObject ?: throw Exception("No ephemeral reader key found")
        val ephemeralWalletKey = runBlocking { KeyManager.createKey(KeyGenerationRequest(keyType = KeyType.secp256r1)) }
        return tokenResponse.toDirectPostJWTParameters(
            encKey,
            alg = authorizationRequest.clientMetadata!!.authorizationEncryptedResponseAlg!!,
            enc = authorizationRequest.clientMetadata!!.authorizationEncryptedResponseEnc!!,
            mapOf(
                "epk" to runBlocking { ephemeralWalletKey.getPublicKey().exportJWKObject() },
                "apu" to JsonPrimitive(Base64URL.encode(presentationSession.nonce).toString()),
                "apv" to JsonPrimitive(Base64URL.encode(authorizationRequest.nonce!!).toString())
            )
        )
    }
}

