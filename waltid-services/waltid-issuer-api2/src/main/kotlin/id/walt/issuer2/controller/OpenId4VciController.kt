package id.walt.issuer2.controller

import id.walt.issuer2.controller.openapi.OpenId4VciRoutesDocs
import id.walt.issuer2.service.CredentialOfferService
import id.walt.issuer2.service.openid4vci.MetadataService
import id.walt.issuer2.service.openid4vci.OpenId4VciProtocolService
import id.walt.openid4vci.dpop.DPoPConstants
import id.walt.openid4vci.metadata.issuer.CredentialIssuerMetadataJwt
import id.walt.openid4vci.requests.credential.encryption.CredentialEncryptionProfile
import id.walt.openid4vci.responses.credential.CredentialResponseBody
import id.walt.openid4vci.responses.credential.CredentialResponseHttp
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.github.smiley4.ktoropenapi.route
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.parseAndSortContentTypeHeader
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.auth.OAuthAccessTokenResponse
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.request.receiveParameters
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
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
            call.response.headers.append(HttpHeaders.Vary, HttpHeaders.Accept)
            val signedContentType = call.requestedSignedCredentialIssuerMetadataContentType()
            if (signedContentType == null) {
                call.respond(metadataService.getCredentialIssuerMetadata())
            } else {
                call.respondText(
                    text = metadataService.getSignedCredentialIssuerMetadata(),
                    contentType = signedContentType,
                )
            }
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
                val response = protocolService.processPushedAuthorizationRequest(
                    parameters = call.receiveParameters().toMap(),
                    headers = call.request.headers.toMap(),
                )
                response.headers.forEach { (name, value) -> call.response.headers.append(name, value) }
                call.respond(HttpStatusCode.fromValue(response.status), response.payload)
            }

            get("authorize", OpenId4VciRoutesDocs.authorize()) {
                val response = protocolService.processAuthorizeRequest(call.parameters.toMap())
                response.redirectUri?.let { redirectUri ->
                    response.headers.filterKeys { it.lowercase() != "location" }
                        .forEach { (name, value) -> call.response.headers.append(name, value) }
                    call.respondRedirect(redirectUri)
                } ?: run {
                    response.headers.forEach { (name, value) -> call.response.headers.append(name, value) }
                    call.respond(HttpStatusCode.fromValue(response.status), response.body ?: "")
                }
            }

            val authOAuthInterceptor = createRouteScopedPlugin("issuer2AuthOAuthInterceptor") {
                onCallRespond { call ->
                    val authorizationRequestEnvelope = call.parameters["internalAuthReq"]
                        ?: return@onCallRespond
                    protocolService.processExternalLoginInterception(
                        externalAuthorizationRequest = call.response.headers.allValues().toMap()["Location"]?.firstOrNull(),
                        authorizationRequestEnvelope = authorizationRequestEnvelope,
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
                    response.redirectUri?.let { redirectUri ->
                        response.headers.filterKeys { it.lowercase() != "location" }
                            .forEach { (name, value) -> call.response.headers.append(name, value) }
                        call.respondRedirect(redirectUri)
                    } ?: run {
                        response.headers.forEach { (name, value) -> call.response.headers.append(name, value) }
                        call.respond(HttpStatusCode.fromValue(response.status), response.body ?: "")
                    }
                }
            }

            post("token", OpenId4VciRoutesDocs.token()) {
                val response = protocolService.processTokenRequest(
                    parameters = call.receiveParameters().toMap(),
                    headers = call.request.headers.toMap(),
                )
                response.headers.forEach { (name, value) -> call.response.headers.append(name, value) }
                call.respond(HttpStatusCode.fromValue(response.status), response.payload)
            }

            post("nonce", OpenId4VciRoutesDocs.nonce()) {
                call.response.headers.append(HttpHeaders.CacheControl, "no-store")
                call.respond(buildJsonObject {
                    protocolService.createNonceResponse().forEach { (key, value) -> put(key, value) }
                })
            }

            post("credential", OpenId4VciRoutesDocs.credential()) {
                val authorizationHeaders = call.request.headers.getAll(HttpHeaders.Authorization).orEmpty()
                val dpopProofHeaderValues = call.request.headers.getAll(DPoPConstants.HEADER_NAME).orEmpty()
                val response =
                    if (call.isEncryptedCredentialRequest()) {
                        protocolService.processCredentialRequest(
                            authorizationHeaders = authorizationHeaders,
                            dpopProofHeaderValues = dpopProofHeaderValues,
                            encryptedCredentialRequest = call.receiveText(),
                        )
                    } else {
                        protocolService.processCredentialRequest(
                            authorizationHeaders = authorizationHeaders,
                            dpopProofHeaderValues = dpopProofHeaderValues,
                            parameters = call.receive<JsonObject>(),
                        )
                    }
                call.respondCredentialResponse(response)
            }
        }
    }

    private fun ApplicationCall.isEncryptedCredentialRequest(): Boolean =
        request.headers[HttpHeaders.ContentType]
            ?.substringBefore(';')
            ?.trim()
            ?.equals(CredentialEncryptionProfile.MEDIA_TYPE_JWT, ignoreCase = true) == true

    private fun ApplicationCall.requestedSignedCredentialIssuerMetadataContentType(): ContentType? {
        val selectedMediaType = parseAndSortContentTypeHeader(request.headers[HttpHeaders.Accept])
            .asSequence()
            .filter { it.quality > 0.0 }
            .map { it.value.lowercase() }
            .firstOrNull { mediaType ->
                mediaType == CredentialIssuerMetadataJwt.MEDIA_TYPE ||
                    mediaType == CredentialIssuerMetadataJwt.TYPED_MEDIA_TYPE ||
                    mediaType == ContentType.Application.Json.toString() ||
                    mediaType == "application/*" ||
                    mediaType == "*/*"
            }

        return when (selectedMediaType) {
            CredentialIssuerMetadataJwt.MEDIA_TYPE,
            CredentialIssuerMetadataJwt.TYPED_MEDIA_TYPE -> ContentType.parse(selectedMediaType)

            else -> null
        }
    }

    private suspend fun ApplicationCall.respondCredentialResponse(response: CredentialResponseHttp) {
        response.headers.forEach { (name, value) -> this.response.headers.append(name, value) }
        val status = HttpStatusCode.fromValue(response.status)

        when (val body = response.body) {
            is CredentialResponseBody.Json -> respond(status, body.payload)
            is CredentialResponseBody.EncryptedJwt ->
                respondText(
                    text = body.value,
                    contentType = ContentType.parse(body.contentType),
                    status = status,
                )
        }
    }
}
