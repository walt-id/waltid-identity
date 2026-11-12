package id.walt.issuer2.controller

import id.walt.issuer2.models.CredentialOfferCreateRequest
import id.walt.issuer2.models.CredentialOfferRequestBody
import id.walt.issuer2.models.MultiCredentialOfferCreateRequest
import id.walt.issuer2.models.PublicIssuanceSession
import id.walt.issuer2.controller.openapi.Issuer2ManagementRoutesDocs
import id.walt.issuer2.notifications.IssuanceNotificationService
import id.walt.issuer2.service.CredentialProfileService
import id.walt.issuer2.service.IssuanceSessionService
import id.walt.issuer2.service.CredentialOfferService
import id.walt.ktornotifications.SseNotifier
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.github.smiley4.ktoropenapi.route
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.callid.callId
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.sse.sse
import kotlinx.serialization.json.Json

class Issuer2ManagementController(
    private val profileService: CredentialProfileService,
    private val sessionService: IssuanceSessionService,
    private val offerService: CredentialOfferService,
) {
    fun register(route: Route) =
        route.route("issuer2", { tags = listOf(Issuer2ManagementRoutesDocs.CREDENTIAL_ISSUANCE_TAG) }) {
            val profileExamples = Issuer2ManagementRoutesDocs.selectProfileExamples(profileService.listProfiles())

            get("profiles", Issuer2ManagementRoutesDocs.listProfiles(profileExamples)) {
                call.respondText(
                    text = Json.encodeToString(profileService.listProfiles()),
                    contentType = ContentType.Application.Json,
                )
            }

            get("profiles/{profileId}", Issuer2ManagementRoutesDocs.getProfile(profileExamples)) {
                val profileId = requireNotNull(call.parameters["profileId"]) { "Missing profileId" }
                call.respondText(
                    text = Json.encodeToString(profileService.getProfile(profileId)),
                    contentType = ContentType.Application.Json,
                )
            }

            post("credential-offers", Issuer2ManagementRoutesDocs.createCredentialOffer()) {
                val requestId = requireNotNull(call.callId) { "Missing call ID" }
                val request = try {
                    call.receive<CredentialOfferRequestBody>()
                } catch (ex: BadRequestException) {
                    val validationMessage = ex.cause?.cause?.message ?: ex.cause?.message ?: ex.message
                    throw BadRequestException("${ex.message}: $validationMessage")
                }
                when (request) {
                    is CredentialOfferCreateRequest -> call.respond(
                        HttpStatusCode.Created,
                        offerService.createCredentialOffer(request, requestId)
                    )

                    is MultiCredentialOfferCreateRequest -> call.respond(
                        HttpStatusCode.Created,
                        offerService.createCredentialOffer(request, requestId)
                    )
                }
            }

            for ((operation, decision) in mapOf("complete" to id.walt.openid4vci.deferred.DeferredStatus.READY, "deny" to id.walt.openid4vci.deferred.DeferredStatus.DENIED)) {
                post("credential-offers/{offerId}/deferred/$operation", {
                    summary = "${operation.replaceFirstChar(Char::uppercase)} deferred issuance"
                    request { pathParameter<String>("offerId") { description = "Existing credential offer ID" } }
                    response {
                        HttpStatusCode.NoContent to { description = "Decision recorded (idempotent)" }
                        HttpStatusCode.NotFound to { description = "Unknown or expired offer" }
                        HttpStatusCode.Conflict to { description = "Ordinary offer or conflicting decision" }
                    }
                }) {
                    try {
                        id.walt.openid4vci.deferred.DeferredIssuance(sessionService.deferredStore).decide(requireNotNull(call.parameters["offerId"]), decision)
                        call.respond(HttpStatusCode.NoContent)
                    } catch (e: id.walt.openid4vci.deferred.DeferredDecisionException) {
                        call.respond(HttpStatusCode.fromValue(e.httpStatus), mapOf("error" to e.message))
                    }
                }
            }

            get("sessions", Issuer2ManagementRoutesDocs.listSessions()) {
                call.respond(sessionService.listSessions().map(::PublicIssuanceSession))
            }

            get("sessions/{sessionId}", Issuer2ManagementRoutesDocs.getSession()) {
                val sessionId = requireNotNull(call.parameters["sessionId"]) { "Missing sessionId" }
                call.respond(PublicIssuanceSession(sessionService.getSession(sessionId)))
            }

            route(Issuer2ManagementRoutesDocs.issuerEvents()) {
                sse("events") {
                    val sseFlow = SseNotifier.getSseFlow(IssuanceNotificationService.ISSUER_EVENT_STREAM_TARGET)

                    send("{}")
                    sseFlow.collect { update ->
                        send(Json.encodeToString(update))
                    }
                }
            }

            route(Issuer2ManagementRoutesDocs.sessionEvents()) {
                sse("sessions/{sessionId}/events") {
                    val sessionId = requireNotNull(call.parameters["sessionId"]) { "Missing sessionId" }
                    sessionService.getSession(sessionId)
                    val sseFlow = SseNotifier.getSseFlow(sessionId)

                    send("{}")
                    sseFlow.collect { update ->
                        send(Json.encodeToString(update))
                    }
                }
            }
        }
}
