package id.walt.issuer2

import id.walt.commons.web.plugins.httpJson
import id.walt.crypto.keys.KeyManager
import id.walt.issuer2.models.*
import id.walt.issuer2.openapi.IssuerRoutesDocs
import id.walt.ktornotifications.KtorNotifications.notifySessionUpdate
import id.walt.ktornotifications.SseNotifier
import id.walt.openid4vci.CredentialFormat
import id.walt.openid4vci.DefaultSession
import id.walt.openid4vci.TokenType
import id.walt.openid4vci.metadata.issuer.CredentialConfiguration
import id.walt.openid4vci.metadata.issuer.CredentialIssuerMetadata
import id.walt.openid4vci.metadata.oauth.AuthorizationServerMetadata
import id.walt.openid4vci.offers.CredentialOffer
import id.walt.openid4vci.requests.authorization.AuthorizationRequestResult
import id.walt.openid4vci.requests.credential.CredentialRequestResult
import id.walt.openid4vci.requests.token.AccessTokenRequestResult
import id.walt.openid4vci.responses.credential.CredentialResponseResult
import id.walt.openid4vci.responses.token.AccessTokenResponseResult
import id.walt.sdjwt.SDMap
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.github.smiley4.ktoropenapi.route
import io.klogging.logger
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sse.*
import io.ktor.server.util.*
import kotlinx.serialization.json.*
import kotlinx.serialization.serializer
import kotlin.time.Duration.Companion.seconds

private val log = logger("OSSIssuer2Service")

object OSSIssuer2Service {

    fun Route.registerRoutes() {
        registerMetadataRoutes()
        registerProtocolRoutes()
        registerIssuanceRoutes()
    }

    private fun Route.registerMetadataRoutes() {
        route(".well-known", {
            tags("OpenID4VCI Metadata")
        }) {
            get("openid-credential-issuer", IssuerRoutesDocs.getCredentialIssuerMetadataDocs()) {
                val metadata = OSSIssuer2Manager.getCredentialIssuerMetadata()
                call.respond(metadata)
            }

            get("oauth-authorization-server", IssuerRoutesDocs.getAuthorizationServerMetadataDocs()) {
                val metadata = OSSIssuer2Manager.getAuthorizationServerMetadata()
                call.respond(metadata)
            }

            get("openid-configuration", IssuerRoutesDocs.getOpenIdConfigurationDocs()) {
                val metadata = OSSIssuer2Manager.getAuthorizationServerMetadata()
                call.respond(metadata)
            }
        }
    }

    private fun Route.registerProtocolRoutes() {
        route("", {
            tags("OpenID4VCI Protocol")
        }) {
            get("credential-offer", IssuerRoutesDocs.getCredentialOfferDocs()) {
                val sessionId = call.request.queryParameters["id"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing session id")

                val session = OSSIssuer2Manager.getSession(sessionId)
                    ?: return@get call.respond(HttpStatusCode.NotFound, "Session not found")

                if (session.isExpired()) {
                    OSSIssuer2Manager.updateSessionStatus(sessionId, IssuanceSessionStatus.EXPIRED)
                    val updatedSession = OSSIssuer2Manager.getSession(sessionId)!!
                    updatedSession.toSessionUpdate(IssuanceSessionEvent.SESSION_EXPIRED)
                        .notifySessionUpdate(sessionId, session.notifications)
                    return@get call.respond(HttpStatusCode.Gone, "Session expired")
                }

                OSSIssuer2Manager.updateSessionStatus(sessionId, IssuanceSessionStatus.CREDENTIAL_OFFER_RESOLVED)
                val updatedSession = OSSIssuer2Manager.getSession(sessionId)!!
                updatedSession.toSessionUpdate(IssuanceSessionEvent.OFFER_RESOLVED)
                    .notifySessionUpdate(sessionId, session.notifications)
                call.respond(session.credentialOffer)
            }

            get("authorize", IssuerRoutesDocs.getAuthorizationEndpointDocs()) {
                val parameters = call.request.queryParameters.entries()
                    .associate { it.key to it.value }

                val result = OSSIssuer2Manager.oauth2Provider.createAuthorizationRequest(parameters)

                when (result) {
                    is AuthorizationRequestResult.Success -> {
                        val request = result.request
                        val issuerState = request.issuerState

                        if (issuerState != null) {
                            val session = OSSIssuer2Manager.getSession(issuerState)
                            if (session != null && session.authMethod == AuthenticationMethod.AUTHORIZED) {
                                val oauthSession = DefaultSession()
                                    .withSubject(issuerState)
                                    .withExpiresAt(TokenType.ACCESS_TOKEN, kotlin.time.Clock.System.now().plus(300.seconds))

                                request.grantScopes(request.requestedScopes)
                                request.grantAudience(setOf(OSSIssuer2Manager.getBaseUrl()))

                                val responseResult = OSSIssuer2Manager.oauth2Provider.createAuthorizationResponse(
                                    request,
                                    oauthSession
                                )

                                when (responseResult) {
                                    is id.walt.openid4vci.responses.authorization.AuthorizationResponseResult.Success -> {
                                        val response = responseResult.response
                                        val httpResponse = OSSIssuer2Manager.oauth2Provider.writeAuthorizationResponse(
                                            request,
                                            response
                                        )
                                        httpResponse.redirectUri?.let { call.respondRedirect(it) }
                                            ?: call.respond(HttpStatusCode.BadRequest, "No redirect URI")
                                        return@get
                                    }
                                    is id.walt.openid4vci.responses.authorization.AuthorizationResponseResult.Failure -> {
                                        val httpResponse = OSSIssuer2Manager.oauth2Provider.writeAuthorizationError(
                                            request,
                                            responseResult.error
                                        )
                                        httpResponse.redirectUri?.let { call.respondRedirect(it) }
                                            ?: call.respond(HttpStatusCode.BadRequest, responseResult.error.description ?: "Error")
                                        return@get
                                    }
                                }
                            }
                        }

                        call.respond(HttpStatusCode.BadRequest, "Invalid or missing issuer_state")
                    }
                    is AuthorizationRequestResult.Failure -> {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            mapOf("error" to result.error.error, "error_description" to result.error.description)
                        )
                    }
                }
            }

            post("token", IssuerRoutesDocs.getTokenEndpointDocs()) {
                val formParameters = call.receiveParameters()
                val parameters = formParameters.entries()
                    .associate { it.key to it.value }

                val grantType = parameters["grant_type"]?.firstOrNull()
                val preAuthCode = parameters["pre-authorized_code"]?.firstOrNull()

                var sessionId: String? = null
                if (grantType == "urn:ietf:params:oauth:grant-type:pre-authorized_code" && preAuthCode != null) {
                    val record = OSSIssuer2Manager.preAuthorizedCodeRepository.getRecord(preAuthCode)
                    sessionId = (record as? id.walt.issuer2.oauth.InMemoryPreAuthorizedCodeRecord)?.sessionId
                }

                val session = sessionId?.let { OSSIssuer2Manager.getSession(it) }
                val oauthSession = session?.let {
                    DefaultSession()
                        .withSubject(it.id)
                        .withExpiresAt(TokenType.ACCESS_TOKEN, kotlin.time.Clock.System.now().plus(300.seconds))
                } ?: DefaultSession()

                val result = OSSIssuer2Manager.oauth2Provider.createAccessTokenRequest(parameters, oauthSession)

                when (result) {
                    is AccessTokenRequestResult.Success -> {
                        val tokenResult = OSSIssuer2Manager.oauth2Provider.createAccessTokenResponse(result.request)
                        when (tokenResult) {
                            is AccessTokenResponseResult.Success -> {
                                sessionId?.let {
                                    OSSIssuer2Manager.updateSessionStatus(it, IssuanceSessionStatus.TOKEN_REQUESTED)
                                    val updatedSession = OSSIssuer2Manager.getSession(it)
                                    updatedSession?.let { s ->
                                        s.toSessionUpdate(IssuanceSessionEvent.TOKEN_REQUESTED)
                                            .notifySessionUpdate(s.id, s.notifications)
                                    }
                                }
                                val httpResponse = OSSIssuer2Manager.oauth2Provider.writeAccessTokenResponse(
                                    result.request,
                                    tokenResult.response
                                )
                                val responseBody = JsonObject(httpResponse.payload)
                                call.respondText(
                                    responseBody.toString(),
                                    ContentType.Application.Json,
                                    HttpStatusCode.OK
                                )
                            }
                            is AccessTokenResponseResult.Failure -> {
                                val httpResponse = OSSIssuer2Manager.oauth2Provider.writeAccessTokenError(
                                    result.request,
                                    tokenResult.error
                                )
                                val responseBody = JsonObject(httpResponse.payload)
                                call.respondText(
                                    responseBody.toString(),
                                    ContentType.Application.Json,
                                    HttpStatusCode.BadRequest
                                )
                            }
                        }
                    }
                    is AccessTokenRequestResult.Failure -> {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            mapOf("error" to result.error.error, "error_description" to result.error.description)
                        )
                    }
                }
            }

            post("nonce", IssuerRoutesDocs.getNonceEndpointDocs()) {
                val nonce = java.util.UUID.randomUUID().toString()
                val expiresIn = 300L
                call.respond(
                    buildJsonObject {
                        put("c_nonce", nonce)
                        put("c_nonce_expires_in", expiresIn)
                    }
                )
            }

            post("credential", IssuerRoutesDocs.getCredentialEndpointDocs()) {
                val authHeader = call.request.header(HttpHeaders.Authorization)
                if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                    call.respond(HttpStatusCode.Unauthorized, "Missing or invalid Authorization header")
                    return@post
                }

                val accessToken = authHeader.removePrefix("Bearer ")

                val body = call.receive<JsonObject>()
                val parameters = body.mapValues { listOf(it.value.toString().trim('"')) }

                val credentialConfigId = body["credential_configuration_id"]?.jsonPrimitive?.content
                    ?: body["credential_identifier"]?.jsonPrimitive?.content

                if (credentialConfigId == null) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "invalid_request", "error_description" to "Missing credential_configuration_id")
                    )
                    return@post
                }

                val credentialConfig = OSSIssuer2Manager.getCredentialConfiguration(credentialConfigId)
                if (credentialConfig == null) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "invalid_request", "error_description" to "Unknown credential configuration")
                    )
                    return@post
                }

                val session = findSessionByCredentialConfig(credentialConfigId)
                if (session == null) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "invalid_request", "error_description" to "No active session for this credential")
                    )
                    return@post
                }

                val profile = OSSIssuer2Manager.getProfile(session.profileId)
                if (profile == null) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "server_error", "error_description" to "Profile not found")
                    )
                    return@post
                }

                try {
                    val issuerKey = KeyManager.resolveSerializedKey(session.issuanceRequest.issuerKey)
                    val issuerId = session.issuanceRequest.issuerDid ?: OSSIssuer2Manager.getBaseUrl()

                    val oauthSession = DefaultSession().withSubject(session.id)
                    val credentialResult = OSSIssuer2Manager.oauth2Provider.createCredentialRequest(
                        parameters,
                        oauthSession,
                        null
                    )

                    when (credentialResult) {
                        is CredentialRequestResult.Success -> {
                            val sdMap = session.issuanceRequest.selectiveDisclosureJson?.let {
                                Json.decodeFromJsonElement(SDMap.serializer(), it)
                            }

                            val responseResult = OSSIssuer2Manager.oauth2Provider.createCredentialResponse(
                                request = credentialResult.request,
                                configuration = credentialConfig,
                                issuerKey = issuerKey,
                                issuerId = issuerId,
                                credentialData = session.issuanceRequest.credentialData,
                                dataMapping = session.issuanceRequest.mapping,
                                selectiveDisclosure = sdMap,
                                x5Chain = session.issuanceRequest.x5Chain,
                                display = null,
                            )

                            when (responseResult) {
                                is CredentialResponseResult.Success -> {
                                    OSSIssuer2Manager.updateSessionStatus(session.id, IssuanceSessionStatus.CREDENTIAL_ISSUED)
                                    val updatedSession = OSSIssuer2Manager.getSession(session.id)!!
                                    updatedSession.toSessionUpdate(IssuanceSessionEvent.CREDENTIAL_ISSUED)
                                        .notifySessionUpdate(session.id, session.notifications)
                                    
                                    val httpResponse = OSSIssuer2Manager.oauth2Provider.writeCredentialResponse(
                                        credentialResult.request,
                                        responseResult.response
                                    )
                                    val responseBody = httpResponse.payload.mapValues { (_, v) ->
                                        when (v) {
                                            is String -> JsonPrimitive(v)
                                            is Number -> JsonPrimitive(v)
                                            is Boolean -> JsonPrimitive(v)
                                            is JsonElement -> v
                                            null -> kotlinx.serialization.json.JsonNull
                                            else -> JsonPrimitive(v.toString())
                                        }
                                    }
                                    call.respondText(
                                        JsonObject(responseBody).toString(),
                                        ContentType.Application.Json,
                                        HttpStatusCode.OK
                                    )
                                }
                                is CredentialResponseResult.Failure -> {
                                    val httpResponse = OSSIssuer2Manager.oauth2Provider.writeCredentialError(
                                        credentialResult.request,
                                        responseResult.error
                                    )
                                    val responseBody = httpResponse.payload.mapValues { (_, v) ->
                                        when (v) {
                                            is String -> JsonPrimitive(v)
                                            is Number -> JsonPrimitive(v)
                                            is Boolean -> JsonPrimitive(v)
                                            is JsonElement -> v
                                            null -> kotlinx.serialization.json.JsonNull
                                            else -> JsonPrimitive(v.toString())
                                        }
                                    }
                                    call.respondText(
                                        JsonObject(responseBody).toString(),
                                        ContentType.Application.Json,
                                        HttpStatusCode.BadRequest
                                    )
                                }
                            }
                        }
                        is CredentialRequestResult.Failure -> {
                            call.respond(
                                HttpStatusCode.BadRequest,
                                mapOf("error" to credentialResult.error.error, "error_description" to credentialResult.error.description)
                            )
                        }
                    }
                } catch (e: Exception) {
                    log.error(e) { "Error issuing credential" }
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        mapOf("error" to "server_error", "error_description" to (e.message ?: "Internal error"))
                    )
                }
            }
        }
    }

    private fun Route.registerIssuanceRoutes() {
        route("profiles", {
            tags("Issuance Management")
        }) {
            get("", IssuerRoutesDocs.getListProfilesDocs()) {
                val profiles = OSSIssuer2Manager.getProfiles().map { profile ->
                    ProfileSummary(
                        profileId = profile.profileId,
                        name = profile.name,
                        credentialConfigurationId = profile.credentialConfigurationId,
                    )
                }
                call.respond(profiles)
            }

            get("{profileId}", IssuerRoutesDocs.getViewProfileDocs()) {
                val profileId = call.parameters.getOrFail("profileId")
                val profile = OSSIssuer2Manager.getProfile(profileId)
                    ?: return@get call.respond(HttpStatusCode.NotFound, "Profile not found")

                val credentialConfig = OSSIssuer2Manager.getCredentialConfiguration(profile.credentialConfigurationId)

                call.respond(
                    ProfileDetails(
                        profileId = profile.profileId,
                        name = profile.name,
                        credentialConfigurationId = profile.credentialConfigurationId,
                        issuerDid = profile.issuerDid,
                        credentialConfiguration = credentialConfig,
                    )
                )
            }

            post("{profileId}/offers", IssuerRoutesDocs.getCreateCredentialOfferDocs()) {
                val profileId = call.parameters.getOrFail("profileId")
                val body = call.receive<CredentialOfferCreateRequestBody>()

                val request = CredentialOfferCreateRequest(
                    profileId = profileId,
                    authMethod = body.authMethod ?: AuthenticationMethod.PRE_AUTHORIZED,
                    issuerStateMode = body.issuerStateMode ?: IssuerStateMode.OMIT,
                    valueMode = body.valueMode ?: CredentialOfferValueMode.BY_REFERENCE,
                    expiresInSeconds = body.expiresInSeconds ?: 300,
                    txCode = body.txCode,
                    txCodeValue = body.txCodeValue,
                    runtimeOverrides = body.runtimeOverrides,
                    notifications = body.notifications,
                )

                try {
                    val session = OSSIssuer2Manager.createCredentialOffer(request)
                    
                    // Send webhook notification for offer creation
                    session.toSessionUpdate(IssuanceSessionEvent.OFFER_CREATED)
                        .notifySessionUpdate(session.id, session.notifications)
                    
                    call.respond(HttpStatusCode.Created, session.toCreationResponse())
                } catch (e: IllegalArgumentException) {
                    call.respond(HttpStatusCode.NotFound, e.message ?: "Profile not found")
                } catch (e: Exception) {
                    log.error(e) { "Error creating credential offer" }
                    call.respond(HttpStatusCode.InternalServerError, e.message ?: "Internal error")
                }
            }
        }

        route("sessions", {
            tags("Session Management")
        }) {
            get("{sessionId}", IssuerRoutesDocs.getSessionDocs()) {
                val sessionId = call.parameters.getOrFail("sessionId")
                val session = OSSIssuer2Manager.getSession(sessionId)
                    ?: return@get call.respond(HttpStatusCode.NotFound, "Session not found")

                call.respond(session)
            }

            route("{sessionId}", IssuerRoutesDocs.getSessionEventsDocs()) {
                sse("events", serialize = { typeInfo, it ->
                    val serializer = httpJson.serializersModule.serializer(typeInfo.kotlinType!!)
                    httpJson.encodeToString(serializer, it)
                }) {
                    val sessionId = call.parameters.getOrFail("sessionId")
                    val session = OSSIssuer2Manager.getSession(sessionId)
                        ?: throw IllegalArgumentException("Unknown session id")

                    val sseFlow = SseNotifier.getSseFlow(sessionId)
                    send(JsonObject(emptyMap()))
                    sseFlow.collect { event -> send(event) }
                }
            }
        }
    }

    private fun findSessionByCredentialConfig(credentialConfigId: String): IssuanceSession? {
        return OSSIssuer2Manager.sessions.values.find {
            it.credentialConfigurationId == credentialConfigId &&
            it.status in listOf(
                IssuanceSessionStatus.ACTIVE,
                IssuanceSessionStatus.CREDENTIAL_OFFER_RESOLVED,
                IssuanceSessionStatus.TOKEN_REQUESTED
            ) &&
            !it.isExpired()
        }
    }
}

@kotlinx.serialization.Serializable
data class ProfileSummary(
    val profileId: String,
    val name: String,
    val credentialConfigurationId: String,
)

@kotlinx.serialization.Serializable
data class ProfileDetails(
    val profileId: String,
    val name: String,
    val credentialConfigurationId: String,
    val issuerDid: String?,
    val credentialConfiguration: CredentialConfiguration?,
)

@kotlinx.serialization.Serializable
data class CredentialOfferCreateRequestBody(
    val authMethod: AuthenticationMethod? = null,
    val issuerStateMode: IssuerStateMode? = null,
    val valueMode: CredentialOfferValueMode? = null,
    val expiresInSeconds: Long? = null,
    val txCode: id.walt.openid4vci.offers.TxCode? = null,
    val txCodeValue: String? = null,
    val runtimeOverrides: CredentialOfferRuntimeOverrides? = null,
    val notifications: id.walt.ktornotifications.core.KtorSessionNotifications? = null,
)
