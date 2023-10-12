package id.walt.oid4vc

import id.walt.auditor.Auditor
import id.walt.auditor.policies.SignaturePolicy
import id.walt.oid4vc.data.ResponseMode
import id.walt.oid4vc.data.dif.PresentationDefinition
import id.walt.oid4vc.providers.CredentialVerifierConfig
import id.walt.oid4vc.providers.OpenIDCredentialVerifier
import id.walt.oid4vc.providers.PresentationSession
import id.walt.oid4vc.responses.TokenResponse
import id.walt.oid4vc.util.randomUUID
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*

const val VP_VERIFIER_PORT = 8002
const val VP_VERIFIER_BASE_URL = "http://localhost:$VP_VERIFIER_PORT"

class VPTestVerifier : OpenIDCredentialVerifier(
    CredentialVerifierConfig("$VP_VERIFIER_BASE_URL/verify")
) {

    private val sessionCache = mutableMapOf<String, PresentationSession>()
    private val presentationDefinitionCache = mutableMapOf<String, PresentationDefinition>()
    override fun getSession(id: String): PresentationSession? = sessionCache[id]

    override fun putSession(id: String, session: PresentationSession): PresentationSession? =
        sessionCache.put(id, session)

    override fun removeSession(id: String): PresentationSession? = sessionCache.remove(id)

    override fun preparePresentationDefinitionUri(
        presentationDefinition: PresentationDefinition,
        sessionID: String
    ): String {
        val cachedPresDef = presentationDefinition.copy(id = randomUUID())
        presentationDefinitionCache.put(cachedPresDef.id, presentationDefinition)
        return "$VP_VERIFIER_BASE_URL/pd/${cachedPresDef.id}"
    }

    override fun prepareResponseOrRedirectUri(sessionID: String, responseMode: ResponseMode): String {
        return super.prepareResponseOrRedirectUri(sessionID, responseMode).plus("/$sessionID")
    }

    override fun doVerify(tokenResponse: TokenResponse, session: PresentationSession): Boolean {
        return tokenResponse.vpToken != null && Auditor.getService()
            .verify(tokenResponse.vpToken!!.toString(), listOf(SignaturePolicy())).result
    }

    fun start() {
        embeddedServer(Netty, port = VP_VERIFIER_PORT) {
            install(ContentNegotiation) {
                json()
            }
            routing {
                get("/pd/{id}") {
                    val pd = presentationDefinitionCache[call.parameters["id"]]
                    if (pd != null) {
                        call.respond(pd.toJSON())
                    } else {
                        call.respond(HttpStatusCode.NotFound)
                    }
                }
                post("/verify/{state}") {
                    val state = call.parameters["state"]
                    val session = state?.let { getSession(it) }
                    val params = call.receiveParameters()
                    val tokenResponse = TokenResponse.fromHttpParameters(params.toMap())
                    if (tokenResponse.vpToken == null || tokenResponse.presentationSubmission == null || session == null) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            "vp_token and/or presentation_submission missing, or invalid session state given."
                        )
                    } else if (verify(tokenResponse, session).verificationResult == true) {
                        call.respond(HttpStatusCode.OK)
                    } else {
                        call.respond(HttpStatusCode.BadRequest, "token response could not be verified")
                    }
                }
            }
        }.start()
    }
}
