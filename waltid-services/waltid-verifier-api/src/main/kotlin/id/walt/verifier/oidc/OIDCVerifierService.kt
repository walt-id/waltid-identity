@file:Suppress("ExtractKtorModule")

package id.walt.verifier.oidc

import COSE.AlgorithmID
import COSE.OneKey
import com.nimbusds.jose.util.X509CertUtils
import com.upokecenter.cbor.CBORObject
import id.walt.commons.config.ConfigManager
import id.walt.commons.persistence.ConfiguredPersistence
import id.walt.credentials.verification.VerificationPolicy
import id.walt.credentials.verification.Verifier
import id.walt.credentials.verification.models.PolicyRequest
import id.walt.credentials.verification.models.PresentationVerificationResponseSurrogate
import id.walt.credentials.verification.policies.*
import id.walt.credentials.verification.policies.vp.HolderBindingPolicy
import id.walt.credentials.verification.policies.vp.MaximumCredentialsPolicy
import id.walt.credentials.verification.policies.vp.MinimumCredentialsPolicy
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.did.dids.DidService
import id.walt.did.dids.DidUtils
import id.walt.mdoc.COSECryptoProviderKeyInfo
import id.walt.mdoc.SimpleCOSECryptoProvider
import id.walt.mdoc.dataelement.EncodedCBORElement
import id.walt.mdoc.dataelement.ListElement
import id.walt.mdoc.dataelement.MapElement
import id.walt.mdoc.dataelement.NullElement
import id.walt.mdoc.dataretrieval.DeviceResponse
import id.walt.mdoc.doc.MDocVerificationParams
import id.walt.mdoc.doc.VerificationType
import id.walt.mdoc.mdocauth.DeviceAuthentication
import id.walt.oid4vc.OpenID4VP
import id.walt.oid4vc.data.*
import id.walt.oid4vc.data.dif.PresentationDefinition
import id.walt.oid4vc.providers.CredentialVerifierConfig
import id.walt.oid4vc.providers.OpenIDCredentialVerifier
import id.walt.oid4vc.providers.PresentationSession
import id.walt.oid4vc.responses.TokenResponse
import id.walt.oid4vc.util.randomUUID
import id.walt.sdjwt.SDJwtVC
import id.walt.sdjwt.WaltIdJWTCryptoProvider
import id.walt.verifier.config.OIDCVerifierServiceConfig
import id.walt.verifier.policies.PresentationDefinitionPolicy
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import java.security.cert.X509Certificate
import java.util.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * OIDC for Verifiable Presentations service provider, implementing abstract base provider from OIDC4VC library.
 */
object OIDCVerifierService : OpenIDCredentialVerifier(
    config = CredentialVerifierConfig(
        ConfigManager.getConfig<OIDCVerifierServiceConfig>().baseUrl.let { "$it/openid4vc/verify" },
        clientIdMap = ConfigManager.getConfig<OIDCVerifierServiceConfig>().x509SanDnsClientId?.let { mapOf(ClientIdScheme.X509SanDns to it) }
            ?: emptyMap())
) {
    private val logger = KotlinLogging.logger {}

    @Serializable
    data class SessionVerificationInformation(
        val vpPolicies: List<PolicyRequest>,
        val vcPolicies: List<PolicyRequest>,
        val specificPolicies: Map<String, List<PolicyRequest>>,
        val successRedirectUri: String?,
        val errorRedirectUri: String?,
        val statusCallback: StatusCallback? = null,
        val walletInitiatedAuthState: String? = null,
    )

    @Serializable
    data class StatusCallback(
        val statusCallbackUri: String,
        val statusCallbackApiKey: String? = null,
    )

    // Persistence
    val presentationSessions = ConfiguredPersistence<PresentationSession>(
        "presentation_session", defaultExpiration = 5.minutes,
        encoding = { Json.encodeToString(it) },
        decoding = {
            println("Decoding $it to PresentationSession")

            Json.decodeFromString(it) },
    )

    // Todo: Make this automatic?
    private val module = SerializersModule {
        polymorphic(VerificationPolicy::class) {
            subclass(HolderBindingPolicy::class)
            subclass(MaximumCredentialsPolicy::class)
            subclass(MinimumCredentialsPolicy::class)
            subclass(AllowedIssuerPolicy::class)
            subclass(ExpirationDatePolicy::class)
            subclass(JsonSchemaPolicy::class)
            subclass(JwtSignaturePolicy::class)
            subclass(NotBeforeDatePolicy::class)
            subclass(RevocationPolicy::class)
            subclass(WebhookPolicy::class)
            subclass(PresentationDefinitionPolicy::class)
        }
    }

    val format = Json { serializersModule = module; encodeDefaults = true }

    val sessionVerificationInfos = ConfiguredPersistence<SessionVerificationInformation>(
        "session_verification_infos", defaultExpiration = 5.minutes,
        encoding = { format.encodeToString(it) },
        decoding = { format.decodeFromString(it) },
    )
    val policyResults = ConfiguredPersistence<PresentationVerificationResponseSurrogate>(
        "policy_results", defaultExpiration = 5.minutes,
        encoding = { format.encodeToString(it) },
        decoding = { format.decodeFromString(it) },
    )


    override fun getSession(id: String) = presentationSessions[id]
    override fun putSession(id: String, session: PresentationSession) = presentationSessions.put(id, session)
    override fun getSessionByAuthServerState(authServerState: String): PresentationSession? {
        TODO("Not yet implemented")
    }

    override fun removeSession(id: String) = presentationSessions.remove(id)

    // ------------------------------------
    // Abstract verifier service provider interface implementation
    override fun preparePresentationDefinitionUri(
        presentationDefinition: PresentationDefinition, sessionID: String,
    ): String {
        val baseUrl = ConfigManager.getConfig<OIDCVerifierServiceConfig>().baseUrl
        return "$baseUrl/openid4vc/pd/$sessionID"
    }

    override fun prepareResponseOrRedirectUri(sessionID: String, responseMode: ResponseMode): String {
        return super.prepareResponseOrRedirectUri(sessionID, responseMode).plus("/$sessionID")
    }

    // ------------------------------------
    // Simple cryptographic operations interface implementation
    override fun doVerify(tokenResponse: TokenResponse, session: PresentationSession): Boolean {
        val policies = sessionVerificationInfos[session.id]
            ?: throw IllegalArgumentException("Could not find policy listing for session: ${session.id}")

        val vpToken = when (tokenResponse.idToken) {
            null -> when (tokenResponse.vpToken) {
                is JsonObject -> tokenResponse.vpToken.toString()
                is JsonPrimitive -> tokenResponse.vpToken!!.jsonPrimitive.content
                null -> {
                    logger.debug { "Null in tokenResponse.vpToken!" }
                    return false
                }

                else -> throw IllegalArgumentException("Illegal tokenResponse.vpToken: ${tokenResponse.vpToken}")
            }

            else -> tokenResponse.idToken.toString()
        }

        if (tokenResponse.vpToken is JsonObject) TODO("Token response is jsonobject - not yet handled")

        logger.debug { "VP token: $vpToken" }
        logger.info { "OpenID4VP profile: ${session.openId4VPProfile}" }

        return when (session.openId4VPProfile) {
            OpenId4VPProfile.ISO_18013_7_MDOC -> verifyMdoc(tokenResponse, session)
            OpenId4VPProfile.HAIP -> runBlocking { verifySdJwtVC(tokenResponse, session) }
            else -> {
                val results = runBlocking {
                    Verifier.verifyPresentation(
                        vpTokenJwt = vpToken,
                        vpPolicies = policies.vpPolicies,
                        globalVcPolicies = policies.vcPolicies,
                        specificCredentialPolicies = policies.specificPolicies,
                        presentationContext = mapOf(
                            "presentationDefinition" to session.presentationDefinition, "challenge" to session.id
                        )
                    )
                }

                policyResults[session.id] = PresentationVerificationResponseSurrogate(results)

                results.overallSuccess()
            }
        }
    }

    private fun getAdditionalTrustedRootCAs(session: PresentationSession): List<X509Certificate> {
        return session.trustedRootCAs?.map { X509CertUtils.parse(it) } ?: listOf()
    }

    private fun verifyMdoc(tokenResponse: TokenResponse, session: PresentationSession): Boolean {
        val mdocHandoverRestored = OpenID4VP.generateMDocOID4VPHandover(
            session.authorizationRequest!!,
            Base64.getUrlDecoder().decode(tokenResponse.jwsParts!!.header["apu"]!!.jsonPrimitive.content).decodeToString()
        )
        val parsedDeviceResponse = DeviceResponse.fromCBORBase64URL(tokenResponse.vpToken!!.jsonPrimitive.content)
        val parsedMdoc = parsedDeviceResponse.documents[0]
        val deviceKey = OneKey(CBORObject.DecodeFromBytes(parsedMdoc.MSO!!.deviceKeyInfo.deviceKey.toCBOR()))
        val issuerKey = parsedMdoc.issuerSigned.issuerAuth?.x5Chain?.let { X509CertUtils.parse(it) }?.publicKey ?: throw Exception("Issuer key not found in x5Chain header (33)")
        return parsedMdoc.verify(
            MDocVerificationParams(
                VerificationType.forPresentation,
                issuerKeyID = "ISSUER_KEY_ID",
                deviceKeyID = "DEVICE_KEY_ID",
                deviceAuthentication = DeviceAuthentication(
                    ListElement(listOf(NullElement(), NullElement(), mdocHandoverRestored)),
                    session.authorizationRequest!!.presentationDefinition?.inputDescriptors?.first()?.id!!,
                    EncodedCBORElement(MapElement(mapOf()))
                )
            ), SimpleCOSECryptoProvider(
                listOf(
                    COSECryptoProviderKeyInfo("ISSUER_KEY_ID", AlgorithmID.ECDSA_256, issuerKey, null, listOf(), getAdditionalTrustedRootCAs(session)),
                    COSECryptoProviderKeyInfo("DEVICE_KEY_ID", AlgorithmID.ECDSA_256, deviceKey.AsPublicKey(), null)
                )
            )
        )
    }

    private suspend fun resolveIssuerKeyFromSdJwt(sdJwt: SDJwtVC): Key {
        val kid = sdJwt.header.get("kid")?.jsonPrimitive?.content ?: randomUUID()
        return if(DidUtils.isDidUrl(kid)) {
            DidService.resolveToKey(kid).getOrThrow()
        } else {
            sdJwt.header.get("x5c")?.jsonArray?.last()?.let {
                return JWKKey.importPEM(it.jsonPrimitive.content).getOrThrow().let { JWKKey(it.jwk, kid) }
            } ?: throw UnsupportedOperationException("Resolving issuer key from SD-JWT is only supported for issuer did in kid header and PEM cert in x5c header parameter")
        }
    }

    private suspend fun verifySdJwtVC(tokenResponse: TokenResponse, session: PresentationSession): Boolean {
        val sdJwtVC = SDJwtVC.parse(tokenResponse.vpToken!!.jsonPrimitive.content)
        require(sdJwtVC.isPresentation && sdJwtVC.keyBindingJwt != null) { "SD-JWT is not a presentation and/or doesn't contain a holder key binding JWT" }
        val holderKey = JWKKey.importJWK(sdJwtVC.holderKeyJWK.toString()).getOrThrow()
        val issuerKey = resolveIssuerKeyFromSdJwt(sdJwtVC)
        val verificationResult = sdJwtVC.verifyVC(
            WaltIdJWTCryptoProvider(mapOf(
                issuerKey.getKeyId() to issuerKey,
                holderKey.getKeyId() to holderKey)
            ),
            requiresHolderKeyBinding = true,
            session.authorizationRequest!!.clientId,
            session.authorizationRequest!!.nonce!!
        )
        return verificationResult.verified
    }

    override fun initializeAuthorization(
        presentationDefinition: PresentationDefinition,
        responseMode: ResponseMode,
        responseType: ResponseType?,
        scope: Set<String>,
        expiresIn: Duration,
        sessionId: String?,
        ephemeralEncKey: Key?,
        clientIdScheme: ClientIdScheme,
        openId4VPProfile: OpenId4VPProfile,
        walletInitiatedAuthState: String?,
        trustedRootCAs: List<String>?
    ): PresentationSession {
        val presentationSession = super.initializeAuthorization(
            presentationDefinition = presentationDefinition,
            responseMode = responseMode,
            responseType = responseType,
            scope = scope,
            expiresIn = expiresIn,
            sessionId = sessionId,
            ephemeralEncKey = ephemeralEncKey,
            clientIdScheme = clientIdScheme,
            openId4VPProfile = openId4VPProfile,
            walletInitiatedAuthState = walletInitiatedAuthState,
            trustedRootCAs = trustedRootCAs
        )
        return presentationSession.copy(
            authorizationRequest = presentationSession.authorizationRequest!!.copy(
                clientMetadata = OpenIDClientMetadata(
                    jwks = ephemeralEncKey?.let {
                        buildJsonObject {
                            put("keys", JsonArray(listOf(runBlocking {
                                it.getPublicKey().exportJWKObject().let {
                                    JsonObject(it + ("use" to JsonPrimitive("enc")) + ("alg" to JsonPrimitive("ECDH-ES")))
                                }
                            })))
                        }
                    },
                    authorizationEncryptedResponseEnc = "A256GCM", // TODO: configurable?
                    authorizationEncryptedResponseAlg = "ECDH-ES"  // TODO: configurable?
                )
            )
        ).also {
            putSession(it.id, it)
        }
    }
}
