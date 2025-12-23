package id.walt.oid4vc

import id.walt.crypto.utils.UuidUtils.randomUUIDString
import id.walt.oid4vc.data.ResponseMode
import id.walt.oid4vc.data.dif.PresentationDefinition
import id.walt.oid4vc.providers.CredentialVerifierConfig
import id.walt.oid4vc.providers.OpenIDCredentialVerifier
import id.walt.oid4vc.providers.PresentationSession
import id.walt.oid4vc.responses.TokenResponse
import id.walt.policies.policies.JwtSignaturePolicy
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
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Duration

const val VP_VERIFIER_PORT = 8002
const val VP_VERIFIER_BASE_URL = "http://localhost:$VP_VERIFIER_PORT"

class VPTestVerifier : OpenIDCredentialVerifier(
    CredentialVerifierConfig("$VP_VERIFIER_BASE_URL/verify")
) {

    private val sessionCache = mutableMapOf<String, PresentationSession>()
    private val presentationDefinitionCache = mutableMapOf<String, PresentationDefinition>()
    override fun getSession(id: String): PresentationSession? = sessionCache[id]

    override fun putSession(id: String, session: PresentationSession, ttl: Duration?) {
        sessionCache[id] = session
    }

    override fun getSessionByAuthServerState(authServerState: String): PresentationSession? {
        TODO("Not yet implemented")
    }

    override fun removeSession(id: String) {
        sessionCache.remove(id)
    }

    override fun preparePresentationDefinitionUri(
        presentationDefinition: PresentationDefinition,
        sessionID: String,
    ): String {
        val cachedPresDef = presentationDefinition.copy(id = randomUUIDString())
        presentationDefinitionCache[cachedPresDef.id] = presentationDefinition
        return "$VP_VERIFIER_BASE_URL/pd/${cachedPresDef.id}"
    }

    override fun prepareResponseOrRedirectUri(sessionID: String, responseMode: ResponseMode): String {
        return super.prepareResponseOrRedirectUri(sessionID, responseMode).plus("/$sessionID")
    }

    override fun doVerify(tokenResponse: TokenResponse, session: PresentationSession): Boolean {
        return runBlocking {
            tokenResponse.vpToken != null &&
                    JwtSignaturePolicy().verify(tokenResponse.vpToken.toString(), null, mapOf()).isSuccess
        }
    }

    fun start(wait: Boolean = false) {
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
                get("/req/{state}") {
                    val state = call.parameters["state"]
                    call.respond(
                        "eyJhbGciOiJFUzI1NksiLCJraWQiOiJkaWQ6d2ViOmVudHJhLndhbHQuaWQjYjNmNDEzZGIzNmJkNGVkM2JjMGNmY2FlZTljMDRiOGF2Y1NpZ25pbmdLZXktNzE5MzMiLCJ0eXAiOiJKV1QifQ.eyJqdGkiOiIzODU0MTMzNS1lNmI1LTQzYjYtOGMwNi0xMWM5MzU2N2VmMjIiLCJpYXQiOjE3MDEyNTQyODcsInJlc3BvbnNlX3R5cGUiOiJpZF90b2tlbiIsInJlc3BvbnNlX21vZGUiOiJwb3N0Iiwic2NvcGUiOiJvcGVuaWQiLCJub25jZSI6ImtkVG9ZTjJZNjRyVXJqVmVRdXl3Q2c9PSIsInJlZGlyZWN0X3VyaSI6Imh0dHBzOi8vdmVyaWZpZWRpZC5kaWQubXNpZGVudGl0eS5jb20vdjEuMC90ZW5hbnRzLzhiYzk1NWQ5LTM4ZmQtNGMxNS1hNTIwLTBjNjU2NDA3NTM3YS92ZXJpZmlhYmxlQ3JlZGVudGlhbHMvdmVyaWZ5UHJlc2VudGF0aW9uIiwicmVnaXN0cmF0aW9uIjp7ImNsaWVudF9uYW1lIjoiMTIzNCIsInN1YmplY3Rfc3ludGF4X3R5cGVzX3N1cHBvcnRlZCI6WyJkaWQ6aW9uIl0sInZwX2Zvcm1hdHMiOnsiand0X3ZwIjp7ImFsZyI6WyJFUzI1NiIsIkVTMjU2SyIsIkVkRFNBIl19LCJqd3RfdmMiOnsiYWxnIjpbIkVTMjU2IiwiRVMyNTZLIiwiRWREU0EiXX19fSwiY2xpZW50X2lkIjoiZGlkOndlYjplbnRyYS53YWx0LmlkIiwic3RhdGUiOiJkaktUdTZXbjhYS29Zelo1R1BYclFzazZUWlZHOFAvd0o5MkhURVlJQitSWVNkMG1SRVRjTm1QOUhhcGRwY3dmOWRCb3htczhCOEs4OXEyLzBwd0xjRUxSRjZVSkJpZWNISFlpb2o4UWNteWJTN3Z4VzdkZTBuMmRBa00vVkRWTU8yT1Zza09adVowZWFnSVoycEdzREF0SkwwVkdnUExKYzBkS1hIdmsrdytabDNrYWJ0aVE3elQ4QTJPSUFkZTJGdGZiU0k2bXNuR3hCVEx1L1hGZ1RhUDhCUFgvSExJTXJBQzV4b09CcVI1dU40Vm4weGs2cnM2WThHaFo2OHE4MEpYbW5RUlhMWDBTNHVoUkdwMFlVcmFDbk92WWswbXN2UFJmYXptU0pNRlZSUCtqU0J6ZldGWUFUbUZrbnR0clJmcHVsNkVqZHRvbm01THcrUWpJTDkvNkRTakFrdEY5VHdJZUoxeURkM0ZJTnhWNGhXZDdhNXpRNkdXUWtTTWp4bzgrUm9sazVDM2R4dURNN3VWSzVJYzRmNXAzTFF2cWxadVNJTFN0dVd4WFZvZ21wc2dRYTkwQlJQYUhiMitLU0daL01CYjNJVlR3dWRlcXFGc21ra0RLNGllTFV6Z3drVy96Y2lzbHpPdVovN1BpVkNqRHVEYk5tVy9Za1lmK0dlL29wb0lGZ3FtaXN0Mzk5Z3BCc0ExMmpGT3c4aEE4Vi9SaFZCS28vU2RSbkI4UDVVMUtiNVBzU21YYjVCUUtqUkgxSjJlZHlnR042Vk1NNUFtbER3OXZ0M0IzTXhzS284REtoMTh2c0xmeGVqZzQ2UlFNRHBSVjlJVEt4eU5xa2pDYnVQWW43Qk01eWd4U0xjaHZTNXQyVmlTcU4ycE1SZklrS1dPVy9sRmo1YnE1dHNNTSIsImV4cCI6MTcwMTI1NDU4NywiY2xhaW1zIjp7InZwX3Rva2VuIjp7InByZXNlbnRhdGlvbl9kZWZpbml0aW9uIjp7ImlkIjoiMTNlMzVkZWItN2YxMC00MzU0LTlmMjktZWIwY2E4M2U3NWVlIiwiaW5wdXRfZGVzY3JpcHRvcnMiOlt7ImlkIjoiOTE5OTE2OTAtMTUxMi00MDk3LWE0YzgtN2YwZjE5Yzc3ODA4IiwibmFtZSI6IlZlcmlmaWVkRW1wbG95ZWUiLCJwdXJwb3NlIjoiVGVzdCIsInNjaGVtYSI6W3sidXJpIjoiVmVyaWZpZWRFbXBsb3llZSJ9XSwiY29uc3RyYWludHMiOnsiZmllbGRzIjpbeyJwYXRoIjpbIiQuaXNzdWVyIiwiJC52Yy5pc3N1ZXIiLCIkLmlzcyJdLCJmaWx0ZXIiOnsidHlwZSI6InN0cmluZyIsInBhdHRlcm4iOiJkaWQ6d2ViOmVudHJhLndhbHQuaWQifX1dfX1dfX19fQ.p2PcdPo8V9IqwZQqKR5ehzDB-lozzuGu7GVyi57Mdy2wyCFQqrY-ly66vWSb50q_-7erLvASqjIBUQaPZWvVMA"
                    )
                }
                post("/callback") {
                    val cbObj = call.receiveText().also { println(it) }.let { Json.parseToJsonElement(it).jsonObject }
                    println("CALLBACK state: ${cbObj["state"]}")
                    println("CALLBACK requestStatus: ${cbObj["requestStatus"]?.jsonPrimitive?.content}")
                    println("CALLBACK requestId: ${cbObj["requestId"]?.jsonPrimitive?.content}")
                    println("CALLBACK subject: ${cbObj["subject"]?.jsonPrimitive?.content ?: "-"}")
                    println("CALLBACK verifiedCredentialsData: ${cbObj["verifiedCredentialsData"]?.toString() ?: "-"}")
                    call.respond(HttpStatusCode.OK)
                }
            }
        }.start(wait)
    }
}
