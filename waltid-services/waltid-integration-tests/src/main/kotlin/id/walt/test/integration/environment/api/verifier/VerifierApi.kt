package id.walt.test.integration.environment.api.verifier

import id.walt.commons.testing.E2ETest
import id.walt.oid4vc.data.ResponseMode
import id.walt.oid4vc.requests.AuthorizationRequest
import id.walt.test.integration.expectSuccess
import id.walt.verifier.oidc.PresentationSessionInfo
import id.walt.verifier.oidc.models.presentedcredentials.PresentationSessionPresentedCredentials
import id.walt.verifier.oidc.models.presentedcredentials.PresentedCredentialsViewMode
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

class VerifierApi(
    private val e2e: E2ETest,
    val client: HttpClient
) {

    suspend fun getSessionRaw(sessionId: String) =
        client.get("/openid4vc/session/$sessionId")

    suspend fun getSession(sessionId: String): PresentationSessionInfo =
        getSessionRaw(sessionId).let {
            it.expectSuccess()
            it.body<PresentationSessionInfo>()
        }

    suspend fun getSignedAuthorizationRequestRaw(sessionId: String) =
        client.get("/openid4vc/request/${sessionId}")

    suspend fun getSignedAuthorizationRequest(sessionId: String): AuthorizationRequest =
        getSignedAuthorizationRequestRaw(sessionId).let {
            it.expectSuccess()
            AuthorizationRequest.fromRequestObject(it.bodyAsText())
        }

    suspend fun getPresentedCredentialsRaw(sessionId: String, responseFormat: PresentedCredentialsViewMode? = null) =
        client.get("/openid4vc/session/${sessionId}/presented-credentials") {
            if (responseFormat != null) {
                url {
                    parameters.append("viewMode", responseFormat.name)
                }
            }
        }

    suspend fun getPresentedCredentials(
        sessionId: String,
        responseFormat: PresentedCredentialsViewMode? = null
    ): PresentationSessionPresentedCredentials {
        val response = getPresentedCredentialsRaw(sessionId, responseFormat)
        response.expectSuccess()
        return response.body<PresentationSessionPresentedCredentials>()
    }

    suspend fun verifyRaw(payload: JsonObject, sessionId: String? = null, responseMode: ResponseMode? = null) =
        client.post("/openid4vc/verify") {
            if (sessionId != null || responseMode != null) {
                headers {
                    sessionId?.also { append("stateId", it) }
                    responseMode?.also { append("responseMode", it.toString()) }
                }
            }
            setBody(payload)
        }

    suspend fun verify(payload: JsonObject, sessionId: String? = null, responseMode: ResponseMode? = null) =
        verifyRaw(payload, sessionId, responseMode).let {
            it.expectSuccess()
            it.bodyAsText()
        }

    suspend fun verify(payload: String, sessionId: String? = null, responseMode: ResponseMode? = null) =
        verify(Json.decodeFromString<JsonObject>(payload), sessionId, responseMode)
}