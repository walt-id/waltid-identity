package id.walt.issuer2.controller

import id.walt.issuer2.controller.openapi.OpenId4VciRoutesDocs
import id.walt.issuer2.notifications.IssuanceNotificationService
import id.walt.issuer2.notifications.IssuanceSessionEvent
import id.walt.issuer2.service.CredentialOfferService
import id.walt.issuer2.service.openid4vci.MetadataService
import id.walt.issuer2.service.openid4vci.OpenId4VciProtocolService
import id.walt.openid4vci.dpop.DPoPConstants
import id.walt.openid4vci.errors.CredentialErrorCodes
import id.walt.openid4vci.metadata.issuer.CredentialIssuerMetadataJwt
import id.walt.openid4vci.requests.credential.encryption.CredentialEncryptionProfile
import id.walt.openid4vci.responses.credential.CredentialResponseBody
import id.walt.openid4vci.responses.credential.CredentialResponseHttp
import id.walt.openid4vci.responses.notification.NotificationResponseHttp
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
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.plugins.callid.callId
import io.ktor.server.request.ContentTransformationException
import io.ktor.server.request.receive
import io.ktor.server.request.receiveParameters
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.util.toMap
import kotlinx.serialization.json.JsonObject
import kotlin.coroutines.cancellation.CancellationException

class OpenId4VciController(
    private val metadataService: MetadataService,
    private val protocolService: OpenId4VciProtocolService,
    private val offerService: CredentialOfferService,
    private val notificationService: IssuanceNotificationService,
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
                val requestId = requireNotNull(call.callId) { "Missing call ID" }
                val sessionId = requireNotNull(call.parameters["id"]) { "Missing credential offer id" }
                call.respond(
                    offerService.getCredentialOffer(sessionId, requestId)
                        ?: throw NotFoundException("Credential offer not found: $sessionId")
                )
            }

            post("par", OpenId4VciRoutesDocs.pushedAuthorizationRequest()) {
                val requestId = requireNotNull(call.callId) { "Missing call ID" }
                val parameters = try {
                    call.receiveParameters().toMap()
                } catch (_: ContentTransformationException) {
                    emptyMap()
                }
                val response = protocolService.processPushedAuthorizationRequest(
                    parameters = parameters,
                    headers = call.request.headers.toMap(),
                    requestId = requestId,
                )
                response.headers.forEach { (name, value) -> call.response.headers.append(name, value) }
                call.respond(HttpStatusCode.fromValue(response.status), response.payload)
            }

            get("authorize", OpenId4VciRoutesDocs.authorize()) {
                val requestId = requireNotNull(call.callId) { "Missing call ID" }
                val response = protocolService.processAuthorizeRequest(
                    parameters = call.parameters.toMap(),
                    requestId = requestId,
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

            val authOAuthInterceptor = createRouteScopedPlugin("issuer2AuthOAuthInterceptor") {
                onCallRespond { call ->
                    val authorizationRequestEnvelope = call.parameters["internalAuthReq"]
                        ?: return@onCallRespond
                    val requestId = requireNotNull(call.callId) { "Missing call ID" }
                    protocolService.processExternalLoginInterception(
                        externalAuthorizationRequest = call.response.headers.allValues().toMap()["Location"]?.firstOrNull(),
                        authorizationRequestEnvelope = authorizationRequestEnvelope,
                        requestId = requestId,
                    )
                }
            }

            authenticate("auth-oauth") {
                install(authOAuthInterceptor)

                get("external_login/{internalAuthReq}", OpenId4VciRoutesDocs.externalLogin()) {
                    // Ktor OAuth redirects to the configured external authorization server.
                }

                get("external/oauth/callback", OpenId4VciRoutesDocs.externalOAuthCallback()) {
                    val requestId = requireNotNull(call.callId) { "Missing call ID" }
                    val principal = call.principal<OAuthAccessTokenResponse.OAuth2>()
                    val idToken = principal?.extraParameters?.get("id_token")
                    val state = call.request.queryParameters["state"]

                    val response = protocolService.processExternalAuthorizationCallback(
                        authServerState = state,
                        idToken = idToken,
                        requestId = requestId,
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
                val requestId = requireNotNull(call.callId) { "Missing call ID" }
                val parameters = try {
                    call.receiveParameters().toMap()
                } catch (_: ContentTransformationException) {
                    emptyMap()
                }
                val response = protocolService.processTokenRequest(
                    parameters = parameters,
                    headers = call.request.headers.toMap(),
                    requestId = requestId,
                )
                response.headers.forEach { (name, value) -> call.response.headers.append(name, value) }
                call.respond(HttpStatusCode.fromValue(response.status), response.payload)
            }

            post("nonce", OpenId4VciRoutesDocs.nonce()) {
                val requestId = requireNotNull(call.callId) { "Missing call ID" }
                val response = protocolService.processNonceRequest(requestId)
                response.headers.forEach { (name, value) -> call.response.headers.append(name, value) }
                call.respond(HttpStatusCode.fromValue(response.status), response.payload)
            }

            post("credential", OpenId4VciRoutesDocs.credential()) {
                val requestId = requireNotNull(call.callId) { "Missing call ID" }
                val authorizationHeaders = call.request.headers.getAll(HttpHeaders.Authorization).orEmpty()
                val dpopProofHeaderValues = call.request.headers.getAll(DPoPConstants.HEADER_NAME).orEmpty()
                val response =
                    if (call.isEncryptedCredentialRequest()) {
                        protocolService.processCredentialRequest(
                            authorizationHeaders = authorizationHeaders,
                            dpopProofHeaderValues = dpopProofHeaderValues,
                            encryptedCredentialRequest = call.receiveText(),
                            requestId = requestId,
                        )
                    } else {
                        val parameters = try {
                            call.receive<JsonObject>()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: ContentTransformationException) {
                            notificationService.notify(
                                requestId = requestId,
                                session = null,
                                event = IssuanceSessionEvent.CREDENTIAL_REQUEST_FAILED,
                                error = CredentialErrorCodes.INVALID_CREDENTIAL_REQUEST,
                                errorDescription = "Invalid credential request",
                            )
                            throw e
                        }
                        protocolService.processCredentialRequest(
                            authorizationHeaders = authorizationHeaders,
                            dpopProofHeaderValues = dpopProofHeaderValues,
                            parameters = parameters,
                            requestId = requestId,
                        )
                    }
                call.respondCredentialResponse(response)
            }


            if (metadataService.walletNotificationEndpointEnabled()) {
                post("notification", OpenId4VciRoutesDocs.notification()) {
                    val authorizationHeaders = call.request.headers.getAll(HttpHeaders.Authorization).orEmpty()
                    val dpopProofHeaderValues = call.request.headers.getAll(DPoPConstants.HEADER_NAME).orEmpty()
                    val response = protocolService.processNotificationRequest(
                        authorizationHeaders = authorizationHeaders,
                        dpopProofHeaderValues = dpopProofHeaderValues,
                        requestBody = call.receiveText(),
                    )
                    call.respondNotificationResponse(response)
                }
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

    private suspend fun ApplicationCall.respondNotificationResponse(response: NotificationResponseHttp) {
        response.headers.forEach { (name, value) -> this.response.headers.append(name, value) }
        val status = HttpStatusCode.fromValue(response.status)
        val payload = response.payload

        if (payload == null) {
            respond(status)
        } else {
            respond(status, payload)
        }
    }
}
