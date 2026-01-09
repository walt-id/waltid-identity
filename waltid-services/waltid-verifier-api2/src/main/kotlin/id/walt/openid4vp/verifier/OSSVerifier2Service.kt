@file:OptIn(ExperimentalUuidApi::class)

package id.walt.openid4vp.verifier

import id.walt.commons.fetchBinaryFile
import id.walt.commons.web.plugins.httpJson
import id.walt.crypto.utils.Base64Utils.encodeToBase64
import id.walt.ktornotifications.KtorNotifications.notifySessionUpdate
import id.walt.ktornotifications.SseNotifier
import id.walt.ktornotifications.core.KtorSessionUpdate
import id.walt.openid4vp.verifier.annexc.AnnexCService
import id.walt.openid4vp.verifier.data.*
import id.walt.openid4vp.verifier.data.Verification2Session.VerificationSessionStatus
import id.walt.openid4vp.verifier.handlers.authrequest.Verifier2AuthorizationRequestHandler
import id.walt.openid4vp.verifier.handlers.vpresponse.Verifier2VPDirectPostHandler
import id.walt.openid4vp.verifier.openapi.VerificationSessionCreateOpenApi
import id.walt.openid4vp.verifier.openapi.VerificationSessionUnifiedOpenApi
import id.walt.vical.*
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.github.smiley4.ktoropenapi.route
import io.klogging.logger
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.doublereceive.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sse.*
import io.ktor.server.util.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import kotlinx.serialization.serializer
import java.util.concurrent.ConcurrentHashMap
import kotlin.uuid.ExperimentalUuidApi


private val log = logger("Verifier2Service")
private const val VERIFICATION_SESSION = "verification-session"
private const val SESSION_ID = "sessionId"
private const val VICAL = "vical"
private const val ENVELOPE_QUERY_PARAM = "envelope"

@Serializable
data class UnifiedEnvelope(
    val sessionId: String,
    @SerialName("flow_type")
    val flowType: String,
    val data: JsonElement,
    val error: UnifiedError? = null
)

@Serializable
data class UnifiedError(
    val message: String,
    val code: String? = null
)

@Serializable
data class AnnexCResponsePayload(
    val response: String
)

private fun ApplicationCall.useUnifiedEnvelope(): Boolean {
    val envelopeParam = request.queryParameters[ENVELOPE_QUERY_PARAM]
    return envelopeParam?.equals("true", ignoreCase = true) == true
}

private inline fun <reified T> jsonElement(value: T): JsonElement =
    httpJson.encodeToJsonElement(value)


object Verifier2Service {

    val sessions = HashMap<String, Verification2Session>()
    private val sessionFlowTypes = ConcurrentHashMap<String, String>()

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
            this.status = VerificationSessionStatus.UNSUCCESSFUL
        }
    }

    private fun flowTypeForSetup(setup: VerificationSessionSetup): String = when (setup) {
        is CrossDeviceFlowSetup -> "cross_device"
        is SameDeviceFlowSetup -> "same_device"
        is DcApiFlowSetup -> "dc_api"
        is DcApiAnnexCFlowSetup -> AnnexCService.FLOW_TYPE
    }

    private fun flowTypeForSession(sessionId: String): String? =
        sessionFlowTypes[sessionId] ?: AnnexCService.sessions[sessionId]?.flowType

    private fun wrapEnvelope(sessionId: String, flowType: String, data: JsonElement, error: UnifiedError? = null) =
        UnifiedEnvelope(sessionId = sessionId, flowType = flowType, data = data, error = error)

    private fun wrapSseUpdate(sessionId: String, flowType: String, update: KtorSessionUpdate): KtorSessionUpdate {
        val envelope = wrapEnvelope(sessionId, flowType, update.session)
        val envelopeJson = httpJson.encodeToJsonElement(envelope).jsonObject
        return KtorSessionUpdate(target = update.target, event = update.event, session = envelopeJson)
    }

    fun Route.registerRoute() {
        route(VERIFICATION_SESSION) {
            route("", {
                tags("Verification Session Management")
            }) {
                post<VerificationSessionSetup>("create", VerificationSessionCreateOpenApi.createDocs) { sessionSetup ->
                    val useEnvelope = call.useUnifiedEnvelope()

                    when (sessionSetup) {
                        is DcApiAnnexCFlowSetup -> {
                            val session = AnnexCService.createSession(
                                docType = sessionSetup.docType,
                                requestedElements = sessionSetup.requestedElements,
                                policies = sessionSetup.policies,
                                origin = sessionSetup.origin,
                                ttlSeconds = sessionSetup.ttlSeconds
                            )
                            sessionFlowTypes[session.id] = session.flowType
                            AnnexCService.publishSessionUpdate(session)

                            val response = AnnexCService.AnnexCCreateResponse(
                                sessionId = session.id,
                                expiresAt = session.expiresAt.toString()
                            )

                            if (useEnvelope) {
                                call.respond(
                                    wrapEnvelope(
                                        sessionId = session.id,
                                        flowType = session.flowType,
                                        data = jsonElement(response)
                                    )
                                )
                            } else {
                                call.respond(response)
                            }
                        }

                        else -> {
                            val newSession = OSSVerifier2Manager.createVerificationSession(sessionSetup)
                            sessions[newSession.id] = newSession
                            sessionFlowTypes[newSession.id] = flowTypeForSetup(sessionSetup)

                            val creationResponse = newSession.toSessionCreationResponse()
                            if (useEnvelope) {
                                call.respond(
                                    wrapEnvelope(
                                        sessionId = newSession.id,
                                        flowType = sessionFlowTypes[newSession.id] ?: "unknown",
                                        data = jsonElement(creationResponse)
                                    )
                                )
                            } else {
                                call.respond(creationResponse)
                            }
                        }
                    }
                }

                route("{$SESSION_ID}") {

                    get("info", VerificationSessionUnifiedOpenApi.infoDocs) {
                        val sessionId = call.parameters.getOrFail(SESSION_ID)
                        val useEnvelope = call.useUnifiedEnvelope()

                        val verifierSession = sessions[sessionId]
                        if (verifierSession != null) {
                            if (useEnvelope) {
                                val flowType = flowTypeForSession(sessionId) ?: "unknown"
                                call.respond(
                                    wrapEnvelope(
                                        sessionId = sessionId,
                                        flowType = flowType,
                                        data = jsonElement(verifierSession)
                                    )
                                )
                            } else {
                                call.respond(verifierSession)
                            }
                            return@get
                        }

                        val annexSession = AnnexCService.sessions[sessionId]
                            ?: return@get call.respond(HttpStatusCode.NotFound, "Unknown session id")
                        val info = AnnexCService.buildInfoResponse(annexSession)
                        if (useEnvelope) {
                            call.respond(
                                wrapEnvelope(
                                    sessionId = sessionId,
                                    flowType = annexSession.flowType,
                                    data = jsonElement(info)
                                )
                            )
                        } else {
                            call.respond(info)
                        }
                    }

                    route(VerificationSessionUnifiedOpenApi.eventsDocs) {
                        sse("$VERIFICATION_SESSION/events", serialize = { typeInfo, it ->
                            val serializer = httpJson.serializersModule.serializer(typeInfo.kotlinType!!)
                            httpJson.encodeToString(serializer, it)
                        }) {
                            val sessionId = call.parameters.getOrFail(SESSION_ID)
                            val flowType = flowTypeForSession(sessionId)
                                ?: return@sse call.respond(HttpStatusCode.NotFound, "Unknown session id")
                            val useEnvelope = call.useUnifiedEnvelope()

                            // Get the flow for this specific target.
                            val sseFlow = SseNotifier.getSseFlow(sessionId)

                            // This will suspend until the client disconnects.
                            val initial = KtorSessionUpdate(
                                target = sessionId,
                                event = "connected",
                                session = JsonObject(emptyMap())
                            )
                            val initialOutbound = if (useEnvelope) wrapSseUpdate(sessionId, flowType, initial) else initial
                            send(initialOutbound)
                            sseFlow.collect { event ->
                                val outbound = if (useEnvelope) wrapSseUpdate(sessionId, flowType, event) else event
                                send(outbound)
                            }
                        }
                    }
                }
            }
            route("{$SESSION_ID}", {
                tags("Client endpoints")
            }) {
                get(
                    "request",
                    VerificationSessionUnifiedOpenApi.requestDocs
                ) {
                    val sessionId = call.parameters.getOrFail(SESSION_ID)
                    val useEnvelope = call.useUnifiedEnvelope()

                    val verificationSession = sessions[sessionId]
                    if (verificationSession != null) {
                        val formatted = Verifier2AuthorizationRequestHandler.handleAuthorizationRequestRequest(
                            verificationSession = verificationSession,
                            updateSessionCallback = updateSessionCallback
                        )

                        if (useEnvelope) {
                            val data = when (formatted) {
                                is Verifier2AuthorizationRequestHandler.JWTStringResponse ->
                                    JsonPrimitive(formatted.jwt)
                                is Verifier2AuthorizationRequestHandler.JsonObjectResponse ->
                                    formatted.json
                                is Verifier2AuthorizationRequestHandler.RawAuthorizationRequestResponse ->
                                    jsonElement(formatted.authorizationRequest)
                            }
                            val flowType = flowTypeForSession(sessionId) ?: "unknown"
                            call.respond(
                                wrapEnvelope(
                                    sessionId = sessionId,
                                    flowType = flowType,
                                    data = data
                                )
                            )
                        } else {
                            when (formatted) {
                                is Verifier2AuthorizationRequestHandler.JWTStringResponse -> call.respond(formatted.jwt)
                                is Verifier2AuthorizationRequestHandler.JsonObjectResponse -> call.respond(formatted.json)
                                is Verifier2AuthorizationRequestHandler.RawAuthorizationRequestResponse ->
                                    call.respond(formatted.authorizationRequest)
                            }
                        }
                        return@get
                    }

                    val annexSession = AnnexCService.sessions[sessionId]
                        ?: return@get call.respond(HttpStatusCode.NotFound, "Unknown session id")
                    val intentToRetain = call.request.queryParameters["intentToRetain"]?.equals("true", ignoreCase = true) == true
                    val response = AnnexCService.buildRequest(annexSession, intentToRetain)
                    AnnexCService.publishSessionUpdate(annexSession)

                    if (useEnvelope) {
                        call.respond(
                            wrapEnvelope(
                                sessionId = sessionId,
                                flowType = annexSession.flowType,
                                data = jsonElement(response)
                            )
                        )
                    } else {
                        call.respond(response)
                    }
                }

                route("") {
                    install(DoubleReceive)

                    post(
                        "response",
                        VerificationSessionUnifiedOpenApi.responseDocs
                    ) {
                        val sessionId = call.parameters.getOrFail(SESSION_ID)
                        val useEnvelope = call.useUnifiedEnvelope()
                        log.trace { "Received verification session response to session: $sessionId" }

                        val verificationSession = sessions[sessionId]
                        if (verificationSession != null) {
                            if (useEnvelope) {
                                val responseData = Verifier2VPDirectPostHandler.run {
                                    call.parseHttpRequestToDirectPostResponse()
                                }
                                val result = Verifier2VPDirectPostHandler.handleDirectPost(
                                    verificationSession = verificationSession,
                                    responseData = responseData,
                                    updateSessionCallback = updateSessionCallback,
                                    failSessionCallback = failSessionCallback
                                )
                                val flowType = flowTypeForSession(sessionId) ?: "unknown"
                                call.respond(
                                    wrapEnvelope(
                                        sessionId = sessionId,
                                        flowType = flowType,
                                        data = jsonElement(result)
                                    )
                                )
                            } else {
                                Verifier2VPDirectPostHandler.run {
                                    call.respondHandleDirectPostResponse(
                                        verificationSession = verificationSession,
                                        updateSessionCallback = updateSessionCallback,
                                        failSessionCallback = failSessionCallback
                                    )
                                }
                            }
                            return@post
                        }

                        val annexSession = AnnexCService.sessions[sessionId]
                            ?: return@post call.respond(HttpStatusCode.NotFound, "Unknown session id")
                        val annexResponse = call.receive<AnnexCResponsePayload>()
                        val ack = AnnexCService.acceptResponse(annexSession, annexResponse.response)

                        if (useEnvelope) {
                            call.respond(
                                wrapEnvelope(
                                    sessionId = sessionId,
                                    flowType = annexSession.flowType,
                                    data = jsonElement(ack)
                                )
                            )
                        } else {
                            call.respond(ack)
                        }
                    }
                }
            }
        }
        route(VICAL) {
            route("", {
                tags("VICAL")
            }) {
                post("fetch", {
                    summary = "Fetches a VICAL from a remote host or from the file system and converts it to Base64"
                    request {
                        body<VicalFetchRequest> {
                            example("Remote VICAL") {
                                value = VicalFetchRequest(vicalUrl = "https://beta.nationaldts.com.au/api/vical")
                            }
                        }
                    }
                    response { HttpStatusCode.OK to { body<VicalFetchResponse>() } }
                }) {
                    val vicalFetchRequest = call.receive<VicalFetchRequest>()
                    val vicalCbor = fetchBinaryFile(vicalFetchRequest.vicalUrl)
                    call.respond(VicalFetchResponse(vicalCbor?.encodeToBase64()))
                }

                post("validate", {
                    summary = "Validates a VICAL by the provided verification key"
                    request {
                        body<VicalValidationRequest> {
                            example("Validate provided VICAL") {
                                value = Json.decodeFromString<VicalValidationRequest>(
                                    """
                                        {
                                           "verificationKey":{
                                              "type":"jwk",
                                              "jwk":{
                                                 "kty":"EC",
                                                 "crv":"P-256",
                                                 "x":"5n7yVdsDcdYRBAzb78_-6iAjpXCrIHId6qdJ7wwg1lE",
                                                 "y":"EFp0x5hbusr51g61xDoL9Y1nlVUqFZGBcSdsuBsjizM"
                                              }
                                           },
                                           "vicalBase64":"hEOhASahGCFZAy ..."
                                        }
                                    """.trimIndent()
                                )
                            }
                        }
                    }
                    response { HttpStatusCode.OK to { body<VicalValidationResponse>() } }
                }) {
                    val vicalValidationRequest = call.receive<VicalValidationRequest>()
                    log.debug { "Received VICAL validation request: $vicalValidationRequest" }
                    call.respond(VicalService.validateVical(vicalValidationRequest))
                }
            }
        }
    }
}
