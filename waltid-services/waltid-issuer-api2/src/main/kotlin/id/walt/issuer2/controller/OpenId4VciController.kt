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
                logger.debug { "External login route hit - internalAuthReq params count: ${allParams?.size}" }
                allParams?.forEachIndexed { idx, param ->
                    logger.trace { "  internalAuthReq[$idx]: ${param.take(200)}${if (param.length > 200) "..." else ""}" }
                }
                val internalAuthReq = allParams?.joinToString("/") 
                    ?: error("Missing internalAuthReq")
                val internalAuthorizationRequest = internalAuthReq
                logger.debug { "Joined internalAuthorizationRequest length: ${internalAuthorizationRequest.length}" }
                
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

                    logger.debug { "OAuth callback - state from Keycloak: $state" }
                    val response = protocolService.processExternalAuthorizationCallback(
                        authServerState = state,
                        idToken = idToken,
                    )
                    logger.debug { "OAuth callback response - status: ${response.status}, redirectUri: ${response.redirectUri?.take(100)}" }
                    
                    response.redirectUri?.let { redirectUri ->
                        // Don't manually append Location header - respondRedirect handles it
                        response.headers.filterKeys { it.lowercase() != "location" }
                            .forEach { (name, value) -> 
                                logger.trace { "OAuth callback response header: $name = $value" }
                                call.response.headers.append(name, value)
                            }
                        call.respondRedirect(redirectUri)
                    } ?: run {
                        response.headers.forEach { (name, value) -> 
                            logger.trace { "OAuth callback response header: $name = $value" }
                            call.response.headers.append(name, value) 
                        }
                        call.respond(HttpStatusCode.fromValue(response.status), response.body ?: "")
                    }
                }
            }

            post("token", OpenId4VciRoutesDocs.token()) {
                val params = call.receiveParameters().toMap()
                logger.debug { 
                    "Token endpoint request - grant_type: ${params["grant_type"]?.firstOrNull()}, " +
                    "code: ${params["code"]?.firstOrNull()?.take(20)}..., " +
                    "client_auth: ${if (params.containsKey("client_assertion")) "private_key_jwt" else if (params.containsKey("client_secret")) "client_secret" else "none"}" 
                }
                val response = protocolService.processTokenRequest(params)
                logger.debug { "Token endpoint response - status: ${response.status}, hasPayload: ${response.payload.isNotEmpty()}" }
                if (response.status != 200) {
                    logger.warn { "Token endpoint error: ${response.payload}" }
                }
                call.respond(HttpStatusCode.fromValue(response.status), response.payload)
            }

            post("nonce", OpenId4VciRoutesDocs.nonce()) {
                // RFC 6749 requires cache-control: no-store for token-like responses
                call.response.headers.append("cache-control", "no-store")
                call.respond(buildJsonObject {
                    protocolService.createNonceResponse().forEach { (key, value) -> put(key, value) }
                })
            }

            post("credential", OpenId4VciRoutesDocs.credential()) {
                val authHeader = call.request.headers[HttpHeaders.Authorization]
                    ?: throw IllegalArgumentException("No Authorization header found")
                // Handle both "Bearer" and "bearer" (case-insensitive per RFC 6750 §2.1)
                val accessToken = when {
                    authHeader.startsWith("Bearer ", ignoreCase = true) -> authHeader.substring(7)
                    else -> throw IllegalArgumentException("Authorization header must start with Bearer")
                }
                logger.debug { "Credential request - access token length: ${accessToken.length}" }
                val request = call.receive<JsonObject>()
                logger.trace { "Credential request payload: ${request.toString().take(200)}..." }
                val response = protocolService.processCredentialRequest(accessToken, request)
                logger.debug { "Credential response - status: ${response.status}" }
                if (response.status != 200) {
                    logger.warn { "Credential endpoint error: ${response.payload}" }
                }
                call.respond(HttpStatusCode.fromValue(response.status), response.payload)
            }
        }
    }
}