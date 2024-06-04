@file:Suppress("ExtractKtorModule")

package id.walt.verifier.oidc

import COSE.AlgorithmID
import COSE.OneKey
import com.upokecenter.cbor.CBORObject
import id.walt.credentials.verification.Verifier
import id.walt.credentials.verification.models.PolicyRequest
import id.walt.credentials.verification.models.PresentationVerificationResponse
import id.walt.crypto.keys.Key
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
import id.walt.verifier.base.config.ConfigManager
import id.walt.verifier.base.config.OIDCVerifierServiceConfig
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import java.util.Base64
import kotlin.time.Duration

/**
 * OIDC for Verifiable Presentations service provider, implementing abstract base provider from OIDC4VC library.
 */
object OIDCVerifierService : OpenIDCredentialVerifier(
    config = CredentialVerifierConfig(ConfigManager.getConfig<OIDCVerifierServiceConfig>().baseUrl.let { "$it/openid4vc/verify" },
        clientIdMap = ConfigManager.getConfig<OIDCVerifierServiceConfig>().x509SanDnsClientId?.let { mapOf(ClientIdScheme.X509SanDns to it) } ?: emptyMap())
) {
    private val logger = KotlinLogging.logger {}

    // ------------------------------------
    // Simple in-memory session management
    private val presentationSessions = HashMap<String, PresentationSession>()

    data class SessionVerificationInformation(
        val vpPolicies: List<PolicyRequest>,
        val vcPolicies: List<PolicyRequest>,
        val specificPolicies: Map<String, List<PolicyRequest>>,
        val successRedirectUri: String?,
        val errorRedirectUri: String?,
        val statusCallback: StatusCallback? = null,
        val walletInitiatedAuthState: String? = null
    )

    data class StatusCallback(
        val statusCallbackUri: String,
        val statusCallbackApiKey: String? = null,
    )

    val sessionVerificationInfos = HashMap<String, SessionVerificationInformation>()
    val policyResults = HashMap<String, PresentationVerificationResponse>()

    data class CredentialPolicyResult(val type: String, val policyResults: List<JsonObject>)

    override fun getSession(id: String) = presentationSessions[id]
    override fun putSession(id: String, session: PresentationSession) = presentationSessions.put(id, session)
    override fun getSessionByIdTokenRequestState(idTokenRequestState: String): PresentationSession? {
        TODO("Not yet implemented")
    }

    override fun removeSession(id: String) = presentationSessions.remove(id)

    // ------------------------------------
    // Abstract verifier service provider interface implementation
    override fun preparePresentationDefinitionUri(
        presentationDefinition: PresentationDefinition, sessionID: String
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

        val vpToken = when(tokenResponse.idToken) {
            null -> when (tokenResponse.vpToken) {
                is JsonObject -> tokenResponse.vpToken.toString()
                is JsonPrimitive -> tokenResponse.vpToken!!.jsonPrimitive.content
                null -> {
                    logger.debug { "Null in tokenResponse.vpToken!" }
                    return false
                }
                else -> throw IllegalArgumentException("Illegal tokenResponse.vpToken: ${tokenResponse.vpToken}")
            }
            else ->tokenResponse.idToken.toString()
        }

        if (tokenResponse.vpToken is JsonObject) TODO("Token response is jsonobject - not yet handled")

        logger.debug { "VP token: $vpToken" }

        if(session.openId4VPProfile == OpenId4VPProfile.ISO_18013_7_MDOC)
            return verifyMdoc(tokenResponse, session)
        else {
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

            policyResults[session.id] = results

            return results.overallSuccess()
        }
    }

    private fun verifyMdoc(tokenResponse: TokenResponse, session: PresentationSession): Boolean {
        val mdocHandoverRestored = OpenID4VP.generateMDocOID4VPHandover(session.authorizationRequest!!, Base64.getUrlDecoder().decode(tokenResponse.jwsParts!!.header["apu"]!!.jsonPrimitive.content).decodeToString())
        val parsedDeviceResponse = DeviceResponse.fromCBORBase64URL(tokenResponse.vpToken!!.jsonPrimitive.content)
        val parsedMdoc = parsedDeviceResponse.documents[0]
        val deviceKey = OneKey(CBORObject.DecodeFromBytes(parsedMdoc.MSO!!.deviceKeyInfo.deviceKey.toCBOR()))
        //TODO("Find issuer key and device key")
        return parsedMdoc.verify(
            MDocVerificationParams(
            VerificationType.forPresentation,
            issuerKeyID = LspPotentialInteropEvent.POTENTIAL_ISSUER_KEY_ID, deviceKeyID = "DEVICE_KEY_ID",
            deviceAuthentication = DeviceAuthentication(
                ListElement(listOf(NullElement(), NullElement(), mdocHandoverRestored)),
                session.authorizationRequest!!.presentationDefinition?.inputDescriptors?.first()?.id!!, EncodedCBORElement(MapElement(mapOf()))
            )
        ), SimpleCOSECryptoProvider(
            listOf(LspPotentialInteropEvent.POTENTIAL_ISSUER_CRYPTO_PROVIDER_INFO,
            COSECryptoProviderKeyInfo("DEVICE_KEY_ID", AlgorithmID.ECDSA_256, deviceKey.AsPublicKey(), null))
        ))
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
            walletInitiatedAuthState = walletInitiatedAuthState
        )
        return presentationSession.copy(
            authorizationRequest = presentationSession.authorizationRequest!!.copy(
                clientMetadata = OpenIDClientMetadata(
                    jwks = ephemeralEncKey?.let { buildJsonObject {
                        put("keys", JsonArray(listOf(runBlocking {
                            it.getPublicKey().exportJWKObject().let {
                                JsonObject(it + ("use" to JsonPrimitive("enc")) + ("alg" to JsonPrimitive("ECDH-ES")))
                            }
                        })))
                    }},
                    authorizationEncryptedResponseEnc = "A256GCM", // TODO: configurable?
                    authorizationEncryptedResponseAlg = "ECDH-ES"  // TODO: configurable?
                )
            )
        ).also {
            putSession(it.id, it)
        }
    }
}
