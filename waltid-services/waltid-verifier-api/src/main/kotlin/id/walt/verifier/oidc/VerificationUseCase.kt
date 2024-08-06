package id.walt.verifier.oidc

import COSE.AlgorithmID
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jose.crypto.ECDSAVerifier
import com.nimbusds.jose.jwk.ECKey
import id.walt.credentials.verification.models.PolicyRequest
import id.walt.credentials.verification.models.PolicyRequest.Companion.parsePolicyRequests
import id.walt.credentials.verification.policies.JwtSignaturePolicy
import id.walt.crypto.keys.KeyGenerationRequest
import id.walt.crypto.keys.KeyManager
import id.walt.crypto.keys.KeyType
import id.walt.mdoc.COSECryptoProviderKeyInfo
import id.walt.oid4vc.data.ClientIdScheme
import id.walt.oid4vc.data.OpenId4VPProfile
import id.walt.oid4vc.data.ResponseMode
import id.walt.oid4vc.data.ResponseType
import id.walt.oid4vc.data.dif.PresentationDefinition
import id.walt.oid4vc.providers.PresentationSession
import id.walt.oid4vc.responses.TokenResponse
import id.walt.sdjwt.JWTCryptoProvider
import id.walt.sdjwt.SimpleJWTCryptoProvider
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import java.security.KeyFactory
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import kotlin.collections.set

class VerificationUseCase(
    val http: HttpClient, cryptoProvider: JWTCryptoProvider,
) {
    private val logger = KotlinLogging.logger {}
    fun createSession(
        vpPoliciesJson: JsonElement?,
        vcPoliciesJson: JsonElement?,
        requestCredentialsJson: JsonElement,
        presentationDefinitionJson: JsonElement?,
        responseMode: ResponseMode,
        responseType: ResponseType? = ResponseType.VpToken,
        successRedirectUri: String?,
        errorRedirectUri: String?,
        statusCallbackUri: String?,
        statusCallbackApiKey: String?,
        stateId: String?,
        openId4VPProfile: OpenId4VPProfile = OpenId4VPProfile.DEFAULT,
        walletInitiatedAuthState: String? = null,
        trustedRootCAs: JsonArray? = null
    ) = let {
        val vpPolicies = vpPoliciesJson?.jsonArray?.parsePolicyRequests() ?: listOf(PolicyRequest(JwtSignaturePolicy()))

        val vcPolicies = vcPoliciesJson?.jsonArray?.parsePolicyRequests() ?: listOf(PolicyRequest(JwtSignaturePolicy()))

        val requestCredentialsArr = requestCredentialsJson.jsonArray

        val requestedTypes = requestCredentialsArr.map {
            when (it) {
                is JsonPrimitive -> it.contentOrNull
                is JsonObject -> it["credential"]?.jsonPrimitive?.contentOrNull
                else -> throw IllegalArgumentException("Invalid JSON type for requested credential: $it")
            } ?: throw IllegalArgumentException("Invalid VC type for requested credential: $it")
        }

        val presentationDefinition =
            (presentationDefinitionJson?.let { PresentationDefinition.fromJSON(it.jsonObject) })
                ?: PresentationDefinition.primitiveGenerationFromVcTypes(requestedTypes, openId4VPProfile)

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

        val specificPolicies = requestCredentialsArr.filterIsInstance<JsonObject>().associate {
            (it["credential"]
                ?: throw IllegalArgumentException("No `credential` name supplied, in `request_credentials`.")).jsonPrimitive.content to (it["policies"]
                ?: throw IllegalArgumentException("No `policies` supplied, in `request_credentials`.")).jsonArray.parsePolicyRequests()
        }

        OIDCVerifierService.sessionVerificationInfos[session.id] = OIDCVerifierService.SessionVerificationInformation(
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
        )
        session
    }

    fun getSession(sessionId: String): PresentationSession = sessionId.let { OIDCVerifierService.getSession(it) }!!

    fun verify(sessionId: String?, tokenResponseParameters: Map<String, List<String>>): Result<String> {
        logger.debug { "Verifying session $sessionId" }
        val session = sessionId?.let { OIDCVerifierService.getSession(it) }
            ?: return Result.failure(Exception("State parameter doesn't refer to an existing session, or session expired"))
        val tokenResponse = when (TokenResponse.isDirectPostJWT(tokenResponseParameters)) {
            true -> TokenResponse.fromDirectPostJWT(
                tokenResponseParameters,
                runBlocking { session.ephemeralEncKey?.exportJWKObject() }
                    ?: throw IllegalArgumentException("No ephemeral reader key found on session"))

            else -> TokenResponse.fromHttpParameters(tokenResponseParameters)
        }
        val sessionVerificationInfo = OIDCVerifierService.sessionVerificationInfos[session.id] ?: return Result.failure(
            IllegalStateException("No session verification information found for session id!")
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
            return Result.success(redirectUri)
        } else {
            val policyResults = OIDCVerifierService.policyResults[session.id]
            val redirectUri = sessionVerificationInfo.errorRedirectUri?.replace("\$id", session.id)

            if (redirectUri != null) {
                return Result.failure(Exception(redirectUri))
            }

            return if (policyResults == null) {
                Result.failure(Exception("Verification policies did not succeed"))
            } else {
                val failedPolicies =
                    policyResults.results.flatMap { it.policyResults.map { it } }.filter { !it.isSuccess }
                Result.failure(Exception("Verification policies did not succeed: ${failedPolicies.joinToString { it.policy }}"))
            }
        }
    }

    fun getResult(sessionId: String): Result<PresentationSessionInfo> {
        val session = OIDCVerifierService.getSession(sessionId)
            ?: return Result.failure(IllegalArgumentException("Invalid id provided (expired?): $sessionId"))

        val policyResults = OIDCVerifierService.policyResults[session.id]?.let { Json.encodeToJsonElement(it).jsonObject }

        return Result.success(
            PresentationSessionInfo.fromPresentationSession(
                session = session,
                policyResults = policyResults
            )
        )
    }

    fun getPresentationDefinition(sessionId: String): Result<PresentationDefinition> =
        OIDCVerifierService.getSession(sessionId)?.presentationDefinition?.let {
            Result.success(it)
        } ?: Result.failure(error("Invalid id provided (expired?): $sessionId"))

    fun getSignedAuthorizationRequestObject(sessionId: String): Result<String> =
        OIDCVerifierService.getSession(sessionId)?.authorizationRequest?.let {
            Result.success(it.toRequestObject(RequestSigningCryptoProvider, RequestSigningCryptoProvider.signingKey.keyID.orEmpty()))
        } ?: Result.failure(error("Invalid id provided (expired?): $sessionId"))

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

    fun getClientIdScheme(openId4VPProfile: OpenId4VPProfile, defaultClientIdScheme: ClientIdScheme): ClientIdScheme {
        return when (openId4VPProfile) {
            OpenId4VPProfile.ISO_18013_7_MDOC -> ClientIdScheme.X509SanDns
            else -> defaultClientIdScheme
        }
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
    val POTENTIAL_JWT_CRYPTO_PROVIDER = SimpleJWTCryptoProvider(JWSAlgorithm.ES256,
        ECDSASigner(ECKey.parseFromPEMEncodedObjects(POTENTIAL_ISSUER_PRIV + POTENTIAL_ISSUER_PUB).toECKey()), ECDSAVerifier(ECKey.parseFromPEMEncodedObjects(POTENTIAL_ISSUER_PUB).toECKey()))

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
