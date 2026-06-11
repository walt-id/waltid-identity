package id.walt.issuer2.controller

import id.walt.commons.config.ConfigManager
import id.walt.issuer2.config.AuthenticationServiceConfig
import id.walt.issuer2.config.Issuer2ServiceConfig
import id.walt.issuer2.controller.openapi.OpenId4VciRoutesDocs
import id.walt.issuer2.service.CredentialOfferService
import id.walt.issuer2.service.openid4vci.MetadataService
import id.walt.issuer2.service.openid4vci.OpenId4VciProtocolService
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.github.smiley4.ktoropenapi.route
import io.klogging.logger
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
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

private val logger = KotlinLogging.logger {}

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
                response.redirectUri?.let { redirectUri ->
                    // Don't manually append Location header - respondRedirect handles it
                    response.headers.filterKeys { it.lowercase() != "location" }
                        .forEach { (name, value) -> call.response.headers.append(name, value) }
                    call.respondRedirect(redirectUri)
                } ?: run {
                    response.headers.forEach { (name, value) -> call.response.headers.append(name, value) }
                    call.respond(HttpStatusCode.fromValue(response.status), response.body ?: "")
                }
            }

            get("external_login/{internalAuthReq...}", OpenId4VciRoutesDocs.externalLogin()) {
                // Manually trigger OAuth redirect to Keycloak
                val allParams = call.parameters.getAll("internalAuthReq")
                println("[DEBUG] external_login route hit - internalAuthReq params count: ${allParams?.size}")
                allParams?.forEachIndexed { idx, param ->
                    println("[DEBUG]   [$idx]: ${param.take(200)}${if (param.length > 200) "..." else ""}")
                }
                val internalAuthReq = allParams?.joinToString("/") 
                    ?: error("Missing internalAuthReq")
                val internalAuthorizationRequest = internalAuthReq
                println("[DEBUG] Joined internalAuthorizationRequest length: ${internalAuthorizationRequest.length}")
                
                // Build Keycloak redirect URL
                val authConfig = ConfigManager.getConfig<AuthenticationServiceConfig>()
                val issuerConfig = ConfigManager.getConfig<Issuer2ServiceConfig>()
                val redirectUri = "${issuerConfig.baseUrl.trimEnd('/')}/openid4vci/external/oauth/callback"
                
                val keycloakUrl = URLBuilder(authConfig.authorizeUrl).apply {
                    parameters.append("client_id", authConfig.clientId)
                    parameters.append("redirect_uri", redirectUri)
                    parameters.append("response_type", "code")
                    parameters.append("scope", authConfig.defaultScopes.joinToString(" "))
                    // Generate and store state
                    val state = java.util.UUID.randomUUID().toString()
                    parameters.append("state", state)
                    
                    // Store internal auth request for later
                    protocolService.processExternalLoginInterception(
                        externalAuthorizationRequest = this.buildString(),
                        internalAuthorizationRequest = internalAuthorizationRequest,
                    )
                }.buildString()
                
                call.respondRedirect(keycloakUrl)
            }

            authenticate("auth-oauth") {

                get("external/oauth/callback", OpenId4VciRoutesDocs.externalOAuthCallback()) {
                    val principal = call.principal<OAuthAccessTokenResponse.OAuth2>()
                        ?: throw IllegalArgumentException("External OAuth callback is missing OAuth principal")
                    val idToken = principal.extraParameters["id_token"]
                        ?: throw IllegalArgumentException("id_token is missing in the callback request")
                    val state = call.request.queryParameters["state"]
                        ?: throw IllegalArgumentException("state parameter is missing in the callback request")

                    println("[DEBUG] OAuth callback - state from Keycloak: $state")
                    val response = protocolService.processExternalAuthorizationCallback(
                        authServerState = state,
                        idToken = idToken,
                    )
                    println("[DEBUG] OAuth callback response - status: ${response.status}, redirectUri: ${response.redirectUri}")
                    response.redirectUri?.let { uri ->
                        println("[DEBUG] Redirect URI details: $uri")
                    }
                    response.redirectUri?.let { redirectUri ->
                        // Don't manually append Location header - respondRedirect handles it
                        response.headers.filterKeys { it.lowercase() != "location" }
                            .forEach { (name, value) -> 
                                println("[DEBUG] Response header: $name = $value")
                                call.response.headers.append(name, value)
                            }
                        call.respondRedirect(redirectUri)
                    } ?: run {
                        response.headers.forEach { (name, value) -> 
                            println("[DEBUG] Response header: $name = $value")
                            call.response.headers.append(name, value) 
                        }
                        call.respond(HttpStatusCode.fromValue(response.status), response.body ?: "")
                    }
                }
            }

            post("token", OpenId4VciRoutesDocs.token()) {
                val params = call.receiveParameters().toMap()
                println("[DEBUG] Token endpoint request params: ${params.mapValues { (k, v) -> if (k.contains("assertion") || k.contains("verifier")) "${v.firstOrNull()?.take(50)}..." else v }}")
                val response = protocolService.processTokenRequest(params)
                println("[DEBUG] Token endpoint response: status=${response.status}, hasPayload=${response.payload.isNotEmpty()}")
                if (response.status != 200) {
                    println("[DEBUG] Token endpoint error payload: ${response.payload}")
                }
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