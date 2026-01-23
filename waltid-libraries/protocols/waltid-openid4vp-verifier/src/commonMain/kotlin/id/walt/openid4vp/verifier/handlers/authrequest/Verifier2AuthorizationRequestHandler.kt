package id.walt.openid4vp.verifier.handlers.authrequest

import id.walt.crypto.utils.JsonUtils.toJsonObject
import id.walt.openid4vp.verifier.data.SessionEvent
import id.walt.openid4vp.verifier.data.Verification2Session
import id.walt.verifier.openid.models.authorization.AuthorizationRequest
import id.walt.verifier.openid.models.openid.OpenID4VPResponseMode
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlin.jvm.JvmInline

object Verifier2AuthorizationRequestHandler {

    private fun dcApiWrapper(protocol: String, data: JsonObject) = mapOf(
        "digital" to mapOf(
            "requests" to listOf(
                mapOf(
                    "protocol" to protocol,
                    "data" to data
                )
            )
        )
    ).toJsonObject().let { JsonObjectResponse(json = it) }

    sealed interface AuthorizationRequestResponseFormat

    /** JSON Object (e.g. for DC API) */
    @JvmInline
    value class JsonObjectResponse(val json: JsonObject) : AuthorizationRequestResponseFormat

    /** JWT String (e.g. for signed request) */
    @JvmInline
    value class JWTStringResponse(val jwt: String) : AuthorizationRequestResponseFormat

    /** Raw AuthorizationRequest */
    @JvmInline
    value class RawAuthorizationRequestResponse(val authorizationRequest: AuthorizationRequest) : AuthorizationRequestResponseFormat

    /** Requires ContentNegotiation plugin! */
    suspend fun RoutingCall.respondAuthorizationRequest(
        verificationSession: Verification2Session,
        updateSessionCallback: suspend (session: Verification2Session, event: SessionEvent, block: Verification2Session.() -> Unit) -> Unit
    ) {
        val call = this

        val formattedSessionResponse = handleAuthorizationRequestRequest(
            verificationSession = verificationSession,
            updateSessionCallback = updateSessionCallback
        )

        when (formattedSessionResponse) {
            is JWTStringResponse -> call.respond(formattedSessionResponse.jwt)
            is JsonObjectResponse -> call.respond(formattedSessionResponse.json)
            is RawAuthorizationRequestResponse -> call.respond(formattedSessionResponse.authorizationRequest)
        }
    }

    suspend fun handleAuthorizationRequestRequest(
        verificationSession: Verification2Session,
        updateSessionCallback: suspend (session: Verification2Session, event: SessionEvent, block: Verification2Session.() -> Unit) -> Unit
    ): AuthorizationRequestResponseFormat {
        suspend fun Verification2Session.updateSession(event: SessionEvent, block: Verification2Session.() -> Unit) =
            updateSessionCallback.invoke(this, event, block)

        verificationSession.updateSession(SessionEvent.authorization_request_requested) {
            status = Verification2Session.VerificationSessionStatus.IN_USE
        }

        val isDcApi = verificationSession.authorizationRequest.responseMode in OpenID4VPResponseMode.DC_API_RESPONSES
        val isSigned = verificationSession.requestMode == Verification2Session.RequestMode.REQUEST_URI_SIGNED // TODO

        return when {
            isDcApi && isSigned -> dcApiWrapper(
                "openid4vp-v1-signed", mapOf(
                    "client_id" to verificationSession.authorizationRequest.clientId,
                    "expected_origins" to verificationSession.authorizationRequest.expectedOrigins,
                    "request" to verificationSession.signedAuthorizationRequestJwt
                ).toJsonObject()
            )

            isDcApi && !isSigned -> dcApiWrapper(
                "openid4vp-v1-unsigned",
                Json.encodeToJsonElement(verificationSession.authorizationRequest).jsonObject
            )


            // JAR (Signed)
            verificationSession.signedAuthorizationRequestJwt != null ->
                JWTStringResponse(jwt = verificationSession.signedAuthorizationRequestJwt)

            // Unsigned
            else -> RawAuthorizationRequestResponse(authorizationRequest = verificationSession.authorizationRequest)
        }
    }
}
