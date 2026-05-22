package id.walt.issuer2.controller

import id.walt.issuer2.controller.openapi.OpenId4VciRoutesDocs
import id.walt.issuer2.service.openid4vci.CredentialOfferService
import id.walt.issuer2.service.openid4vci.MetadataService
import id.walt.issuer2.service.openid4vci.OpenId4VciProtocolService
import id.walt.openid4vci.metadata.issuer.CredentialIssuerMetadata
import id.walt.openid4vci.metadata.oauth.AuthorizationServerMetadata
import id.walt.openid4vci.metadata.oidc.OpenIDProviderMetadata
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.github.smiley4.ktoropenapi.route
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.util.toMap
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject

class OpenId4VciController(
    private val metadataService: MetadataService,
    private val protocolService: OpenId4VciProtocolService,
    private val offerService: CredentialOfferService,
) {
    fun register(route: Route) {
        route.get(".well-known/openid-credential-issuer", OpenId4VciRoutesDocs.credentialIssuerMetadata()) {
            call.respond(
                Json.encodeToJsonElement(
                    CredentialIssuerMetadata.serializer(),
                    metadataService.getCredentialIssuerMetadata(),
                )
            )
        }

        route.get(".well-known/oauth-authorization-server", OpenId4VciRoutesDocs.authorizationServerMetadata()) {
            call.respond(
                Json.encodeToJsonElement(
                    AuthorizationServerMetadata.serializer(),
                    metadataService.getAuthorizationServerMetadata(),
                )
            )
        }

        route.get(".well-known/openid-configuration", OpenId4VciRoutesDocs.openIdProviderMetadata()) {
            call.respond(
                Json.encodeToJsonElement(
                    OpenIDProviderMetadata.serializer(),
                    metadataService.getOpenIdProviderMetadata(),
                )
            )
        }

        route.route("openid4vci", { tags = listOf("OpenID4VCI") }) {
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

            get("authorize", OpenId4VciRoutesDocs.authorize()) {
                val response = protocolService.processAuthorizeRequest(call.parameters.toMap())
                response.headers.forEach { (name, value) -> call.response.headers.append(name, value) }
                response.redirectUri?.let { redirectUri ->
                    call.respondRedirect(redirectUri)
                } ?: call.respond(HttpStatusCode.fromValue(response.status), response.body ?: "")
            }

            post("token", OpenId4VciRoutesDocs.token()) {
                val response = protocolService.processTokenRequest(call.receiveParameters().toMap())
                response.headers.forEach { (name, value) -> call.response.headers.append(name, value) }
                call.respond(HttpStatusCode.fromValue(response.status), response.payload)
            }

            post("credential", OpenId4VciRoutesDocs.credential()) {
                val accessToken = call.request.headers[HttpHeaders.Authorization]
                    ?.substringAfter("Bearer ")
                    ?: throw IllegalArgumentException("No bearer access token found")
                val request = call.receive<JsonObject>()
                call.respond(protocolService.processCredentialRequest(accessToken, request))
            }

            post("nonce", OpenId4VciRoutesDocs.nonce()) {
                call.respond(buildJsonObject {
                    protocolService.createNonceResponse().forEach { (key, value) -> put(key, value) }
                })
            }
        }
    }
}
