@file:OptIn(ExperimentalUuidApi::class)

package id.walt.verifier2

import id.walt.commons.config.ConfigManager
import id.walt.ktornotifications.SseNotifier
import id.walt.ktornotifications.WebhookNotifier
import id.walt.verifier.openid.models.authorization.AuthorizationRequest
import id.walt.verifier2.Verification2Session.VerificationSessionStatus
import id.walt.verifier2.Verifier2Manager.VerificationSessionCreationResponse
import id.walt.verifier2.Verifier2Manager.VerificationSessionSetup
import id.walt.verifier2.Verifier2OpenApiExamples.exampleOf
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

object Verifier2Service {

    private val log = logger("Verifier2Service")

    private val config = ConfigManager.getConfig<OSSVerifier2ServiceConfig>()

    val sessions = HashMap<String, Verification2Session>()

    suspend fun notifySessionUpdate(session: Verification2Session, event: SessionEvent) {
        val update = Verifier2SessionUpdate(session.id, event, session).toKtorSessionUpdate()

        SseNotifier.notify(session.id, update)

        if (session.notifications != null) {
            if (session.notifications!!.webhook != null) {
                WebhookNotifier.notify(update = update, config = session.notifications!!.webhook!!)
            }
        }
    }

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

        notifySessionUpdate(session, event)
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
        route("verification-session") {
            post<VerificationSessionSetup>("create", {
                summary = "Create new verification session"
                request {
                    body<VerificationSessionSetup> {
                        example("Basic example") { value = Verifier2OpenApiExamples.basicExample }
                        example("W3C + path") { value = exampleOf(Verifier2OpenApiExamples.w3cPlusPath) }
                        example("Empty meta") { value = exampleOf(Verifier2OpenApiExamples.emptyMeta) }
                        example("Nested presentation request") {
                            value = exampleOf(Verifier2OpenApiExamples.nestedPresentationRequestW3C)
                        }
                        example("Nested presentation request + multiple claims") {
                            value = exampleOf(Verifier2OpenApiExamples.nestedPresentationRequestWithMultipleClaims)
                        }
                        example("W3C type values") { value = exampleOf(Verifier2OpenApiExamples.w3cTypeValues) }
                        example("W3C without claims") { value = exampleOf(Verifier2OpenApiExamples.W3CWithoutClaims) }
                        example("W3C with claims and values") { value = exampleOf(Verifier2OpenApiExamples.W3CWithClaimsAndValues) }
                    }
                }
                response {
                    HttpStatusCode.Created to {
                        body<VerificationSessionCreationResponse>()
                    }
                }
            }) { sessionSetup ->
                val newSession =
                    Verifier2Manager.createVerificationSession(
                        sessionSetup, config.clientId, config.clientMetadata, config.urlPrefix
                    )
                sessions[newSession.id] = newSession
                val creationResponse = newSession.toSessionCreationResponse()
                call.respond(creationResponse)
            }

            route("{sessionId}") {

                get(
                    "info", {
                        summary = "View data of existing verification session"
                        response { HttpStatusCode.OK to { body<Verification2Session>() } }
                    }
                ) {
                    val verifierSession =
                        sessions[call.parameters.getOrFail("sessionId")] ?: throw IllegalArgumentException("Unknown session id")
                    call.respond(verifierSession)
                }

                route({
                    summary = "Receive update events via SSE about the verification session"
                }) {
                    sse("verification-session/events", serialize = { typeInfo, it ->
                        val serializer = Json.serializersModule.serializer(typeInfo.kotlinType!!)
                        Json.encodeToString(serializer, it)
                    }) {
                        val verifierSession =
                            sessions[call.parameters.getOrFail("sessionId")] ?: throw IllegalArgumentException("Unknown session id")

                        // Get the flow for this specific target.
                        val sseFlow = SseNotifier.getSseFlow(verifierSession.id.toString())

                        // This will suspend until the client disconnects.
                        send(JsonObject(emptyMap()))
                        sseFlow.collect { event -> send(event) }
                    }
                }
                get(
                    "request",
                    {
                        summary = "Wallets lookup the AuthorizationRequest here"
                        request { pathParameter<String>("verification-session") }
                        response { HttpStatusCode.OK to { body<AuthorizationRequest>() } }
                    }) {
                    val verificationSession =
                        sessions[call.parameters.getOrFail("sessionId")] ?: throw IllegalArgumentException("Unknown session id")

                    // TODO: JAR

                    call.respond(verificationSession.authorizationRequest)
                }

                route("") {
                    install(DoubleReceive)

                    post<String>(
                        "response",
                        {
                            summary = "Wallets respond to an AuthorizationRequest here"
                            request {
                                pathParameter<String>("verification-session")
                                body<String> { description = "" /* TODO */ }
                            }
                        }) { body ->
                        val sessionId = call.parameters.getOrFail("sessionId")
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

