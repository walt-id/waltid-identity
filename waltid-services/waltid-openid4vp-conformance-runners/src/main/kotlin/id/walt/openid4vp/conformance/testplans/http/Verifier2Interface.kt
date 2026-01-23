package id.walt.openid4vp.conformance.testplans.http

import id.walt.openid4vp.verifier.data.CrossDeviceFlowSetup
import id.walt.openid4vp.verifier.data.Verification2Session
import id.walt.openid4vp.verifier.data.VerificationSessionSetup
import id.walt.openid4vp.verifier.handlers.sessioncreation.VerificationSessionCreator
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*

class Verifier2Interface(
    val http: HttpClient
) {

    /** Create verification session at Verifier2 */
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
        }.body<VerificationSessionCreator.VerificationSessionCreationResponse>()

    /** Create verification session information from Verifier2 */
    suspend fun getVerificationSessionInfo(sessionId: String) =
        http.get("/verification-session/$sessionId/info")
            .body<Verification2Session>()


}
