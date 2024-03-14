package id.walt.verifier.oidc

import id.walt.credentials.verification.models.PolicyRequest
import id.walt.credentials.verification.models.PolicyRequest.Companion.parsePolicyRequests
import id.walt.credentials.verification.policies.JwtSignaturePolicy
import id.walt.oid4vc.data.ResponseMode
import id.walt.oid4vc.data.dif.PresentationDefinition
import id.walt.oid4vc.responses.TokenResponse
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.json.*
import kotlin.collections.set

class VerificationUseCase(
    val http: HttpClient,
) {
    private val logger = KotlinLogging.logger {}
    fun createSession(
        vpPoliciesJson: JsonElement?,
        vcPoliciesJson: JsonElement?,
        requestCredentialsJson: JsonElement,
        presentationDefinitionJson: JsonElement?,
        responseMode: ResponseMode,
        successRedirectUri: String?,
        errorRedirectUri: String?,
        statusCallbackUri: String?,
        statusCallbackApiKey: String?,
        stateId: String?,
    ) = let {
        /*val presentationDefinition = (body["presentation_definition"]
                    ?: throw IllegalArgumentException("No `presentation_definition` supplied!"))
                    .let { PresentationDefinition.fromJSON(it.jsonObject) }*/
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
                ?: PresentationDefinition.primitiveGenerationFromVcTypes(requestedTypes)
        println("Presentation definition: " + presentationDefinition.toJSON())

        val session = OIDCVerifierService.initializeAuthorization(
            presentationDefinition, responseMode = responseMode, sessionId = stateId
        )

        val specificPolicies = requestCredentialsArr.filterIsInstance<JsonObject>().associate {
            (it["credential"]
                ?: throw IllegalArgumentException("No `credential` name supplied, in `request_credentials`.")).jsonPrimitive.content to (it["policies"]
                ?: throw IllegalArgumentException("No `policies` supplied, in `request_credentials`.")).jsonArray.parsePolicyRequests()
        }

        println("vpPolicies: $vpPolicies")
        println("vcPolicies: $vcPolicies")
        println("spPolicies: $specificPolicies")


        OIDCVerifierService.sessionVerificationInfos[session.id] = OIDCVerifierService.SessionVerificationInformation(
            vpPolicies = vpPolicies,
            vcPolicies = vcPolicies,
            specificPolicies = specificPolicies,
            successRedirectUri = successRedirectUri,
            errorRedirectUri = errorRedirectUri,
            statusCallback = statusCallbackUri?.let {
                OIDCVerifierService.StatusCallback(
                    statusCallbackUri = it,
                    statusCallbackApiKey = statusCallbackApiKey,
                )
            })
        session
    }

    fun verify(sessionId: String?, tokenResponseParameters: Map<String, List<String>>): Result<String> {
        val session = sessionId?.let { OIDCVerifierService.getSession(it) }
            ?: return Result.failure(error("State parameter doesn't refer to an existing session, or session expired"))
        val tokenResponse = TokenResponse.fromHttpParameters(tokenResponseParameters)
        val sessionVerificationInfo = OIDCVerifierService.sessionVerificationInfos[session.id] ?: return Result.failure(
            IllegalStateException("No session verification information found for session id!")
        )

        val maybePresentationSessionResult = runCatching { OIDCVerifierService.verify(tokenResponse, session) }

        if (maybePresentationSessionResult.getOrNull() != null) {
            val presentationSession = maybePresentationSessionResult.getOrThrow()
            if (presentationSession.verificationResult == true) {
                val redirectUri = sessionVerificationInfo.successRedirectUri?.replace("\$id", session.id) ?: ""
                return Result.success(redirectUri)
            } else {
                val policyResults = OIDCVerifierService.policyResults[session.id]
                val redirectUri = sessionVerificationInfo.errorRedirectUri?.replace("\$id", session.id)

                if (redirectUri != null) {
                    return Result.failure(error(redirectUri))
                } else {
                    if (policyResults == null) {
                        return Result.failure(error("Verification policies did not succeed"))
                    } else {
                        val failedPolicies =
                            policyResults.results.flatMap { it.policyResults.map { it } }.filter { it.result.isFailure }
                        return Result.failure(error("Verification policies did not succeed: ${failedPolicies.joinToString { it.request.policy.name }}"))
                    }
                }
            }
        } else {
            return Result.failure(error("Verification failed"))
        }
    }

    suspend fun notifySubscribers(sessionId: String) =
        OIDCVerifierService.sessionVerificationInfos[sessionId]?.statusCallback?.let {
            val response = http.post(it.statusCallbackUri) {
                header(HttpHeaders.ContentType, ContentType.Application.Json)
                it.statusCallbackApiKey?.let { bearerAuth(it) }
                setBody(mapOf("sessionId" to sessionId))
            }
            logger.debug { "status callback: ${response.status}" }
        }
}