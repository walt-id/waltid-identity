package id.walt.issuer2.controller

import id.walt.issuer2.controller.openapi.OpenId4VciRoutesDocs
import id.walt.issuer2.service.CredentialOfferService
import id.walt.issuer2.service.openid4vci.MetadataService
import id.walt.issuer2.service.openid4vci.OpenId4VciProtocolService
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.github.smiley4.ktoropenapi.route
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.auth.OAuthAccessTokenResponse
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.util.toMap
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class OpenId4VciController(
    private val metadataService: MetadataService,
    private val protocolService: OpenId4VciProtocolService,
    private val offerService: CredentialOfferService,
) {
    fun register(route: Route) {
        route.get(".well-known/openid-credential-issuer/openid4vci", OpenId4VciRoutesDocs.credentialIssuerMetadata()) {
            call.respond(metadataService.getCredentialIssuerMetadata())
        }

        route.get(".well-known/oauth-authorization-server/openid4vci", OpenId4VciRoutesDocs.authorizationServerMetadata()) {
            call.respond(metadataService.getAuthorizationServerMetadata())
        }

        route.get(".well-known/jwt-vc-issuer/openid4vci", OpenId4VciRoutesDocs.jwtVcIssuerMetadata()) {
            call.respond(metadataService.getJwtVcIssuerMetadata())
        }

        route.get(".well-known/vct/{type}", OpenId4VciRoutesDocs.vctTypeMetadata()) {
            val credentialType = requireNotNull(call.parameters["type"]) { "Missing VCT type" }
            call.respond(metadataService.getVctTypeMetadata(credentialType))
        }

        route.route("openid4vci", { tags = listOf(OpenId4VciRoutesDocs.OPENID4VCI_TAG) }) {
            get("jwks", OpenId4VciRoutesDocs.jwks()) {
                call.respond(metadataService.listJwks())
            }

            get("credential-offer", OpenId4VciRoutesDocs.credentialOffer()) {
                val sessionId = requireNotNull(call.parameters["id"]) { "Missing credential offer id" }
                call.respond(
                    offerService.getCredentialOffer(sessionId)
                        ?: throw IllegalArgumentException("Credential offer not found: $sessionId")
                )
            }

            post("par", OpenId4VciRoutesDocs.pushedAuthorizationRequest()) {
                val response = protocolService.processPushedAuthorizationRequest(call.receiveParameters().toMap())
                call.respond(HttpStatusCode.fromValue(response.status), response.payload)
            }

            get("authorize", OpenId4VciRoutesDocs.authorize()) {
                val response = protocolService.processAuthorizeRequest(call.parameters.toMap())
                response.headers.forEach { (name, value) -> call.response.headers.append(name, value) }
                response.redirectUri?.let { redirectUri ->
                    call.respondRedirect(redirectUri)
                } ?: call.respond(HttpStatusCode.fromValue(response.status), response.body ?: "")
            }

            val authOAuthInterceptor = createRouteScopedPlugin("issuer2AuthOAuthInterceptor") {
                onCallRespond { call ->
                    val internalAuthorizationRequest = call.parameters["internalAuthReq"] ?: return@onCallRespond
                    protocolService.processExternalLoginInterception(
                        externalAuthorizationRequest = call.response.headers.allValues().toMap()["Location"]?.firstOrNull(),
                        internalAuthorizationRequest = internalAuthorizationRequest,
                    )
                }
            }

            authenticate("auth-oauth") {
                install(authOAuthInterceptor)

                get("external_login/{internalAuthReq}", OpenId4VciRoutesDocs.externalLogin()) {
                    // Ktor OAuth redirects to the configured external authorization server.
                }

                get("external/oauth/callback", OpenId4VciRoutesDocs.externalOAuthCallback()) {
                    val principal = call.principal<OAuthAccessTokenResponse.OAuth2>()
                        ?: throw IllegalArgumentException("External OAuth callback is missing OAuth principal")
                    val idToken = principal.extraParameters["id_token"]
                        ?: throw IllegalArgumentException("id_token is missing in the callback request")
                    val state = call.request.queryParameters["state"]
                        ?: throw IllegalArgumentException("state parameter is missing in the callback request")

                    val response = protocolService.processExternalAuthorizationCallback(
                        authServerState = state,
                        idToken = idToken,
                    )
                    response.headers.forEach { (name, value) -> call.response.headers.append(name, value) }
                    response.redirectUri?.let { redirectUri ->
                        call.respondRedirect(redirectUri)
                    } ?: call.respond(HttpStatusCode.fromValue(response.status), response.body ?: "")
                }
            }

            post("token", OpenId4VciRoutesDocs.token()) {
                val response = protocolService.processTokenRequest(call.receiveParameters().toMap())
                call.respond(HttpStatusCode.fromValue(response.status), response.payload)
            }

            post("nonce", OpenId4VciRoutesDocs.nonce()) {
                call.respond(buildJsonObject {
                    protocolService.createNonceResponse().forEach { (key, value) -> put(key, value) }
                })
            }

            post("credential", OpenId4VciRoutesDocs.credential()) {
                val accessToken = call.request.headers[HttpHeaders.Authorization]
                    ?.substringAfter("Bearer ")
                    ?: throw IllegalArgumentException("No bearer access token found")
                val request = call.receive<JsonObject>()
                val response = protocolService.processCredentialRequest(accessToken, request)
                call.respond(HttpStatusCode.fromValue(response.status), response.payload)
            }
        }
    }
}