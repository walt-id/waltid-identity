package id.walt.openid4vp.conformance.testplans.http

import id.walt.verifier2.data.CrossDeviceFlowSetup
import id.walt.verifier2.data.Verification2Session
import id.walt.verifier2.data.VerificationSessionSetup
import id.walt.verifier2.handlers.sessioncreation.VerificationSessionCreationResponse
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*

/**
 * Interface to the walt.id Verifier service for conformance testing.
 * 
 * Used by conformance test runners to create and query verification sessions.
 */
class VerifierInterface(
    val http: HttpClient
) {

    /** Create verification session at Verifier */
    suspend fun createVerificationSession(
        authorizationEndpointToUse: String,
        verificationSessionSetup: VerificationSessionSetup
    ) =
        http.post("/verification-session/create") {
            setBody(
                if (verificationSessionSetup is CrossDeviceFlowSetup)
                    verificationSessionSetup.copy(
                        urlConfig = verificationSessionSetup.urlConfig.copy(
                            urlHost = authorizationEndpointToUse
                        )
                    )
                else verificationSessionSetup
            )
        }.body<VerificationSessionCreationResponse>()

    /** Get verification session information from Verifier */
    suspend fun getVerificationSessionInfo(sessionId: String) =
        http.get("/verification-session/$sessionId/info")
            .body<Verification2Session>()
}
