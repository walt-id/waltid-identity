package id.walt.verifier.oidc

import org.cose.java.AlgorithmID
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jose.crypto.ECDSAVerifier
import com.nimbusds.jose.jwk.ECKey
import id.walt.credentials.utils.VCFormat
import id.walt.crypto.keys.KeyGenerationRequest
import id.walt.crypto.keys.KeyManager
import id.walt.crypto.keys.KeyType
import id.walt.crypto.utils.JsonUtils.toJsonElement
import id.walt.mdoc.COSECryptoProviderKeyInfo
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
import id.walt.sdjwt.SimpleJWTCryptoProvider
import io.klogging.logger
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.plugins.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import java.security.KeyFactory
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.*
import kotlin.time.Duration

class VerificationUseCase(
    val http: HttpClient, cryptoProvider: JWTCryptoProvider,
) {
    private val logger = logger("Verification")
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
        val vpPolicies = vpPoliciesJson?.jsonArray?.parsePolicyRequests() ?: getDefaultVPPolicyRequests(presentationFormat)
        val vcPolicies = vcPoliciesJson?.jsonArray?.parsePolicyRequests() ?: getDefaultVCPolicies(presentationFormat)

        val presentationDefinition = PresentationDefinition(inputDescriptors = requestedCredentials.map { it.toInputDescriptor() })

        logger.debug { "Presentation definition: " + presentationDefinition.toJSON() }

        val session = OIDCVerifierService.initializeAuthorization(
            presentationDefinition, responseMode = responseMode, sessionId = stateId,
            ephemeralEncKey = when (responseMode) {
                ResponseMode.direct_post_jwt -> runBlocking { KeyManager.createKey(KeyGenerationRequest(keyType = KeyType.secp256r1)) }
                else -> null
            },
            clientIdScheme = this.getClientIdScheme(openId4VPProfile, OIDCVerifierService.config.defaultClientIdScheme),
            openId4VPProfile = openId4VPProfile,
            walletInitiatedAuthState = walletInitiatedAuthState,
            responseType = responseType,
            trustedRootCAs = trustedRootCAs?.map { it.jsonPrimitive.content }
        )

        val specificPolicies = requestedCredentials.filter { !it.policies.isNullOrEmpty() }.associate {
            it.id to it.policies!!.parsePolicyRequests()
        }

        OIDCVerifierService.sessionVerificationInfos.set(
            session.id,
            OIDCVerifierService.SessionVerificationInformation(
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
            sessionTtl
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
            vpPoliciesJson, vcPoliciesJson,
            requestCredentialsJson = JsonArray(Json.decodeFromJsonElement<PresentationDefinition>(presentationDefinitionJson).inputDescriptors.map {
                RequestedCredential(inputDescriptor = it, policies = null).toJsonElement()
            }), responseMode, responseType, successRedirectUri, errorRedirectUri, statusCallbackUri, statusCallbackApiKey, stateId,
            openId4VPProfile, walletInitiatedAuthState, trustedRootCAs, sessionTtl
        )
    }

    fun getSession(sessionId: String): PresentationSession = sessionId.let { OIDCVerifierService.getSession(it) }

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
                tokenResponseParameters,
                runBlocking { session.ephemeralEncKey?.exportJWKObject() }
                    ?: throw IllegalArgumentException("No ephemeral reader key found on session"))

            else -> TokenResponse.fromHttpParameters(tokenResponseParameters)
        }
        val sessionVerificationInfo = OIDCVerifierService.sessionVerificationInfos[session.id] ?: return Result.failure(
            NotFoundException("No session verification information found for session id: ${session.id}")
        )

        val maybePresentationSessionResult = runCatching { OIDCVerifierService.verify(tokenResponse, session) }

        if (maybePresentationSessionResult.isFailure) {
            return Result.failure(
                IllegalStateException(
                    "Verification failed: ${maybePresentationSessionResult.exceptionOrNull()!!.message}",
                    maybePresentationSessionResult.exceptionOrNull()
                )
            )
        }

        val presentationSession = maybePresentationSessionResult.getOrThrow()
        if (presentationSession.verificationResult == true) {
            val redirectUri = sessionVerificationInfo.successRedirectUri?.replace("\$id", session.id) ?: ""
            logger.debug { "Presentation is successful, redirecting to: $redirectUri" }
            return Result.success(redirectUri)
        } else {
            val policyResults = OIDCVerifierService.policyResults[session.id]
            val redirectUri = sessionVerificationInfo.errorRedirectUri?.replace("\$id", session.id)

            logger.debug { "Presentation failed, redirecting to: $redirectUri" }


            return if (policyResults == null) {
                Result.failure(FailedVerificationException(redirectUri, IllegalArgumentException("Verification policies did not succeed")))
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

        val policyResults = OIDCVerifierService.policyResults[session.id]?.let { Json.encodeToJsonElement(it).jsonObject }

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
        val credentialFormat = requestedCredentials.map { it.format ?: it.inputDescriptor?.format?.keys?.first() }.distinct().singleOrNull()
        if (credentialFormat == null) throw IllegalArgumentException("Credentials formats must be distinct for a presentation request")
        return when (credentialFormat) {
            VCFormat.mso_mdoc -> VCFormat.mso_mdoc
            VCFormat.sd_jwt_vc -> VCFormat.sd_jwt_vc
            VCFormat.ldp_vc, VCFormat.ldp -> VCFormat.ldp_vp
            VCFormat.jwt_vc, VCFormat.jwt -> VCFormat.jwt_vp
            VCFormat.jwt_vc_json -> VCFormat.jwt_vp_json
            else -> throw IllegalArgumentException("Credentials format $credentialFormat is not a valid format for a requested credential")
        }
    }

    private fun getDefaultVPPolicyRequests(presentationFormat: VCFormat): List<PolicyRequest> = when (presentationFormat) {
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

object LspPotentialInteropEvent {
    const val POTENTIAL_ROOT_CA_CERT = "-----BEGIN CERTIFICATE-----\n" +
            "MIIBQzCB66ADAgECAgjbHnT+6LsrbDAKBggqhkjOPQQDAjAYMRYwFAYDVQQDDA1NRE9DIFJPT1QgQ1NQMB4XDTI0MDUwMjEzMTMzMFoXDTI0MDUwMzEzMTMzMFowFzEVMBMGA1UEAwwMTURPQyBST09UIENBMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEWP0sG+CkjItZ9KfM3sLF+rLGb8HYCfnlsIH/NWJjiXkTx57ryDLYfTU6QXYukVKHSq6MEebvQPqTJT1blZ/xeKMgMB4wDAYDVR0TAQH/BAIwADAOBgNVHQ8BAf8EBAMCAQYwCgYIKoZIzj0EAwIDRwAwRAIgWM+JtnhdqbTzFD1S3byTvle0n/6EVALbkKCbdYGLn8cCICOoSETqwk1oPnJEEPjUbdR4txiNqkHQih8HKAQoe8t5\n" +
            "-----END CERTIFICATE-----\n"
    const val POTENTIAL_ROOT_CA_PRIV = "-----BEGIN PRIVATE KEY-----\n" +
            "MEECAQAwEwYHKoZIzj0CAQYIKoZIzj0DAQcEJzAlAgEBBCBXPx4eVTypvm0pQkFdqVXlORn+YIFNb+Hs5xvmG3EM8g==\n" +
            "-----END PRIVATE KEY-----\n"
    const val POTENTIAL_ROOT_CA_PUB = "-----BEGIN PUBLIC KEY-----\n" +
            "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEWP0sG+CkjItZ9KfM3sLF+rLGb8HYCfnlsIH/NWJjiXkTx57ryDLYfTU6QXYukVKHSq6MEebvQPqTJT1blZ/xeA==\n" +
            "-----END PUBLIC KEY-----\n"
    const val POTENTIAL_ISSUER_CERT = "-----BEGIN CERTIFICATE-----\n" +
            "MIIBRzCB7qADAgECAgg57ch6mnj5KjAKBggqhkjOPQQDAjAXMRUwEwYDVQQDDAxNRE9DIFJPT1QgQ0EwHhcNMjQwNTAyMTMxMzMwWhcNMjUwNTAyMTMxMzMwWjAbMRkwFwYDVQQDDBBNRE9DIFRlc3QgSXNzdWVyMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEG0RINBiF+oQUD3d5DGnegQuXenI29JDaMGoMvioKRBN53d4UazakS2unu8BnsEtxutS2kqRhYBPYk9RAriU3gaMgMB4wDAYDVR0TAQH/BAIwADAOBgNVHQ8BAf8EBAMCB4AwCgYIKoZIzj0EAwIDSAAwRQIhAI5wBBAA3ewqIwslhuzFn4rNFW9dkz2TY7xeImO7CraYAiAYhai1NzJ6abAiYg8HxcRdYpO4bu2Sej8E6CzFHK34Yw==\n" +
            "-----END CERTIFICATE-----"
    const val POTENTIAL_ISSUER_PUB = "-----BEGIN PUBLIC KEY-----\n" +
            "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEG0RINBiF+oQUD3d5DGnegQuXenI29JDaMGoMvioKRBN53d4UazakS2unu8BnsEtxutS2kqRhYBPYk9RAriU3gQ==\n" +
            "-----END PUBLIC KEY-----\n"
    const val POTENTIAL_ISSUER_PRIV = "-----BEGIN PRIVATE KEY-----\n" +
            "MEECAQAwEwYHKoZIzj0CAQYIKoZIzj0DAQcEJzAlAgEBBCAoniTdVyXlKP0x+rius1cGbYyg+hjf8CT88hH8SCwWFA==\n" +
            "-----END PRIVATE KEY-----\n"
    const val POTENTIAL_ISSUER_KEY_ID = "potential-lsp-issuer-key-01"
    val POTENTIAL_ISSUER_CRYPTO_PROVIDER_INFO = loadPotentialIssuerKeys()
    val POTENTIAL_JWT_CRYPTO_PROVIDER = SimpleJWTCryptoProvider(
        JWSAlgorithm.ES256,
        ECDSASigner(ECKey.parseFromPEMEncodedObjects(POTENTIAL_ISSUER_PRIV + POTENTIAL_ISSUER_PUB).toECKey()),
        ECDSAVerifier(ECKey.parseFromPEMEncodedObjects(POTENTIAL_ISSUER_PUB).toECKey())
    )

    fun readKeySpec(pem: String): ByteArray {
        val publicKeyPEM = pem
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace(System.lineSeparator().toRegex(), "")
            .replace("-----END PUBLIC KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")

        return Base64.getDecoder().decode(publicKeyPEM)
    }

    fun loadPotentialIssuerKeys(): COSECryptoProviderKeyInfo {
        val factory = CertificateFactory.getInstance("X.509")
        val rootCaCert = (factory.generateCertificate(POTENTIAL_ROOT_CA_CERT.byteInputStream())) as X509Certificate
        val issuerCert = (factory.generateCertificate(POTENTIAL_ISSUER_CERT.byteInputStream())) as X509Certificate
        val issuerPub = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(readKeySpec(POTENTIAL_ISSUER_PUB)))
        val issuerPriv = KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(readKeySpec(POTENTIAL_ISSUER_PRIV)))
        return COSECryptoProviderKeyInfo(
            POTENTIAL_ISSUER_KEY_ID,
            AlgorithmID.ECDSA_256,
            issuerPub,
            issuerPriv,
            listOf(issuerCert),
            listOf(rootCaCert)
        )
    }
}
