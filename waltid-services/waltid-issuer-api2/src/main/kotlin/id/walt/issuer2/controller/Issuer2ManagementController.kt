package id.walt.issuer2.controller

import id.walt.issuer2.controller.dto.CredentialOfferCreateRequest
import id.walt.issuer2.controller.openapi.Issuer2ManagementRoutesDocs
import id.walt.issuer2.service.CredentialProfileService
import id.walt.issuer2.service.IssuanceSessionService
import id.walt.issuer2.service.CredentialOfferService
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.github.smiley4.ktoropenapi.route
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.sse.sse

class Issuer2ManagementController(
    private val profileService: CredentialProfileService,
    private val sessionService: IssuanceSessionService,
    private val offerService: CredentialOfferService,
) {
    fun register(route: Route) = route.route("issuer2", { tags = listOf("Issuer2 Management") }) {
        val profileExamples = Issuer2ManagementRoutesDocs.selectProfileExamples(profileService.listProfiles())

        get("profiles", Issuer2ManagementRoutesDocs.listProfiles(profileExamples)) {
            call.respond(profileService.listProfiles())
        }

        get("profiles/{profileId}", Issuer2ManagementRoutesDocs.getProfile(profileExamples)) {
            val profileId = requireNotNull(call.parameters["profileId"]) { "Missing profileId" }
            call.respond(profileService.getProfile(profileId))
        }

        post("credential-offers", Issuer2ManagementRoutesDocs.createCredentialOffer()) {
            val request = call.receive<CredentialOfferCreateRequest>()
            call.respond(HttpStatusCode.Created, offerService.createCredentialOffer(request))
        }

        get("sessions", Issuer2ManagementRoutesDocs.listSessions()) {
            call.respond(sessionService.listSessions())
        }

        get("sessions/{sessionId}", Issuer2ManagementRoutesDocs.getSession()) {
            val sessionId = requireNotNull(call.parameters["sessionId"]) { "Missing sessionId" }
            call.respond(sessionService.getSession(sessionId))
        }

        sse("sessions/{sessionId}/events") {
            send("connected")
        }
    }
}