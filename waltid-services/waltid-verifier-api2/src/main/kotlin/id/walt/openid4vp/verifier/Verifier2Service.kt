@file:OptIn(ExperimentalUuidApi::class)

package id.walt.openid4vp.verifier

import id.walt.ktornotifications.KtorNotifications.notifySessionUpdate
import id.walt.ktornotifications.SseNotifier
import id.walt.openid4vp.verifier.Verification2Session.VerificationSessionStatus
import id.walt.openid4vp.verifier.VerificationSessionCreator.VerificationSessionSetup
import id.walt.openid4vp.verifier.openapi.VerificationSessionCreateOpenApi
import id.walt.verifier.openid.models.authorization.AuthorizationRequest
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.github.smiley4.ktoropenapi.route
import io.klogging.logger
import io.ktor.http.*
import io.ktor.server.plugins.doublereceive.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sse.*
import io.ktor.server.util.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.serializer
import kotlin.uuid.ExperimentalUuidApi

private val log = logger("Verifier2Service")
private const val VERIFICATION_SESSION = "verification-session"

object Verifier2Service {

    val sessions = HashMap<String, Verification2Session>()

    /**
     * Update data for this session and send session update notifications
     */
    val updateSessionCallback: suspend (
        session: Verification2Session,
        event: SessionEvent,
        block: Verification2Session.() -> Unit
    ) -> Unit = { session, event, block ->
        log.trace { "Updating session due to '$event': ${session.id}" }
        val newSession = session.apply {
            block.invoke(this)
        }
        sessions[newSession.id] = newSession

        Verifier2SessionUpdate(session.id, event, session)
            .toKtorSessionUpdate()
            .notifySessionUpdate(session.id, session.notifications)
    }

    /**
     * Mark this session as failed
     */
    val failSessionCallback: suspend (
        session: Verification2Session,
        event: SessionEvent,
        updateSession: suspend (Verification2Session, SessionEvent, block: Verification2Session.() -> Unit) -> Unit
    ) -> Unit = { session, event, updateSession ->
        updateSession(session, event) {
            this.status = VerificationSessionStatus.FAILED
        }
    }

    fun Route.registerRoute() {
        route(VERIFICATION_SESSION) {
            route("", {
                tags("Verification Session Management")
            }) {
                post<VerificationSessionSetup>("create", VerificationSessionCreateOpenApi.createDocs) { sessionSetup ->
                    val newSession = OSSVerifier2Manager.createVerificationSession(sessionSetup)
                    sessions[newSession.id] = newSession
                    val creationResponse = newSession.toSessionCreationResponse()
                    call.respond(creationResponse)
                }

                route("{$VERIFICATION_SESSION}") {

                    get("info", {
                        summary = "View data of existing verification session"
                        request { pathParameter<String>(VERIFICATION_SESSION) }
                        response { HttpStatusCode.OK to { body<Verification2Session>() } }
                    }
                    ) {
                        val verifierSession =
                            sessions[call.parameters.getOrFail(VERIFICATION_SESSION)]
                                ?: throw IllegalArgumentException("Unknown session id")
                        call.respond(verifierSession)
                    }

                    route({
                        summary = "Receive update events via SSE about the verification session"
                    }) {
                        sse("$VERIFICATION_SESSION/events", serialize = { typeInfo, it ->
                            val serializer = Json.serializersModule.serializer(typeInfo.kotlinType!!)
                            Json.encodeToString(serializer, it)
                        }) {
                            val verifierSession =
                                sessions[call.parameters.getOrFail("sessionId")] ?: throw IllegalArgumentException("Unknown session id")

                            // Get the flow for this specific target.
                            val sseFlow = SseNotifier.getSseFlow(verifierSession.id)

                            // This will suspend until the client disconnects.
                            send(JsonObject(emptyMap()))
                            sseFlow.collect { event -> send(event) }
                        }
                    }
                }
            }
            route("{$VERIFICATION_SESSION}", {
                tags("Client endpoints")
            }) {
                get(
                    "request",
                    {
                        summary = "Wallets lookup the AuthorizationRequest here"
                        request { pathParameter<String>(VERIFICATION_SESSION) }
                        response { HttpStatusCode.OK to { body<AuthorizationRequest>() } }
                    }) {
                    val verificationSession =
                        sessions[call.parameters.getOrFail(VERIFICATION_SESSION)] ?: throw IllegalArgumentException("Unknown session id")

                    if (verificationSession.signedAuthorizationRequestJwt != null) {
                        // JAR (Signed)
                        call.respond(verificationSession.signedAuthorizationRequestJwt!!)
                    } else {
                        // Unsigned
                        call.respond(verificationSession.authorizationRequest)
                    }
                }

                route("") {
                    install(DoubleReceive)

                    post<String>(
                        "response",
                        {
                            summary = "Wallets respond to an AuthorizationRequest here"
                            request {
                                pathParameter<String>(VERIFICATION_SESSION)
                                body<String> { description = "" /* TODO */ }
                            }
                        }) { body ->
                        val sessionId = call.parameters.getOrFail(VERIFICATION_SESSION)
                        log.trace { "Received verification session response to session: $sessionId" }
                        val verificationSession = sessions[sessionId]

                        val urlParameters = call.receiveParameters()
                        val vpTokenString = urlParameters.getOrFail("vp_token")
                        val receivedState = urlParameters["state"]

                        log.trace { "Verification session data: state = $receivedState, vp_token = $vpTokenString" }

                        call.respond(
                            Verifier2ReceivedCredentialHandler.handleDirectPost(
                                verificationSession = verificationSession,
                                vpTokenString = vpTokenString,
                                receivedState = receivedState,
                                updateSessionCallback = updateSessionCallback,
                                failSessionCallback = failSessionCallback
                            )
                        )
                    }
                }
            }
        }
    }
}

