@file:Suppress("ExtractKtorModule")

package id.walt.verifier.oidc

import id.walt.credentials.verification.Verifier
import id.walt.credentials.verification.models.PolicyRequest
import id.walt.credentials.verification.models.PresentationVerificationResponse
import id.walt.oid4vc.data.ResponseMode
import id.walt.oid4vc.data.dif.PresentationDefinition
import id.walt.oid4vc.providers.CredentialVerifierConfig
import id.walt.oid4vc.providers.OpenIDCredentialVerifier
import id.walt.oid4vc.providers.PresentationSession
import id.walt.oid4vc.responses.TokenResponse
import id.walt.verifier.base.config.ConfigManager
import id.walt.verifier.base.config.OIDCVerifierServiceConfig
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

/**
 * OIDC for Verifiable Presentations service provider, implementing abstract base provider from OIDC4VC library.
 */
object OIDCVerifierService : OpenIDCredentialVerifier(
    config = CredentialVerifierConfig(ConfigManager.getConfig<OIDCVerifierServiceConfig>().baseUrl.let { "$it/openid4vc/verify" })
) {
    // ------------------------------------
    // Simple in-memory session management
    val presentationSessions = HashMap<String, PresentationSession>()

    data class SessionVerificationInformation(
        val vpPolicies: List<PolicyRequest>,
        val vcPolicies: List<PolicyRequest>,
        val specificPolicies: Map<String, List<PolicyRequest>>,
        val successRedirectUri: String?,
        val errorRedirectUri: String?,
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
    /*override fun preparePresentationDefinitionUri(presentationDefinition: PresentationDefinition, sessionID: String): String {
        return ConfigManager.getConfig<OIDCVerifierServiceConfig>().baseUrl.let { "$it/vp/pd/$sessionID" }
    }*/
    override fun preparePresentationDefinitionUri(
        presentationDefinition: PresentationDefinition,
        sessionID: String
    ): String? {
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

        val vpToken = when (tokenResponse.vpToken) {
            is JsonObject -> tokenResponse.vpToken.toString()
            is JsonPrimitive -> tokenResponse.vpToken!!.jsonPrimitive.content
            null -> {
                println("Null in tokenResponse.vpToken!")
                return false
            }

            else -> throw IllegalArgumentException("Illegal tokenResponse.vpToken: ${tokenResponse.vpToken}")
        }


        if (tokenResponse.vpToken is JsonObject) TODO("Token response is jsonobject - not yet handled")

        println("VP token: $vpToken")
        val results = runBlocking {
            Verifier.verifyPresentation(
                vpTokenJwt = vpToken,
                vpPolicies = policies.vpPolicies,
                globalVcPolicies = policies.vcPolicies,
                specificCredentialPolicies = policies.specificPolicies,
                presentationContext = mapOf(
                    "presentationDefinition" to session.presentationDefinition,
                    "challenge" to session.id
                )
            )
        }

        policyResults[session.id] = results

        return results.overallSuccess()

        /*
        val vpToken = when (tokenResponse.vpToken) {
            is JsonObject -> tokenResponse.vpToken.toString()
            is JsonPrimitive -> tokenResponse.vpToken!!.jsonPrimitive.content
            null -> {
                println("Null in tokenResponse.vpToken!")
                return false
            }

            else -> throw IllegalArgumentException("Illegal tokenResponse.vpToken: ${tokenResponse.vpToken}")
        }
        println("Verifying vpToken: $vpToken")


        return Auditor.getService().verify(
            vpToken, listOf(
                SignaturePolicy()
            )
        ).result*/
    }
}
