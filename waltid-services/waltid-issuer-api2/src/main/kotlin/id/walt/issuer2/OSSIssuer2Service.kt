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
                if (sessionId == null) {
                    log.warn { "GET /credential-offer - Missing session id parameter" }
                    return@get call.respond(HttpStatusCode.BadRequest, "Missing session id")
                }
                log.debug { "GET /credential-offer - Session ID: $sessionId" }

                val session = OSSIssuer2Manager.getSession(sessionId)
                if (session == null) {
                    log.warn { "GET /credential-offer - Session not found: $sessionId" }
                    return@get call.respond(HttpStatusCode.NotFound, "Session not found")
                }

                if (session.isExpired()) {
                    log.info { "GET /credential-offer - Session expired: $sessionId" }
                    OSSIssuer2Manager.updateSessionStatus(sessionId, IssuanceSessionStatus.EXPIRED)
                    val updatedSession = OSSIssuer2Manager.getSession(sessionId)!!
                    updatedSession.toSessionUpdate(IssuanceSessionEvent.SESSION_EXPIRED)
                        .notifySessionUpdate(sessionId, session.notifications)
                    return@get call.respond(HttpStatusCode.Gone, "Session expired")
                }

                log.info { "GET /credential-offer - Credential offer resolved for session: $sessionId" }
                OSSIssuer2Manager.updateSessionStatus(sessionId, IssuanceSessionStatus.CREDENTIAL_OFFER_RESOLVED)
                val updatedSession = OSSIssuer2Manager.getSession(sessionId)!!
                updatedSession.toSessionUpdate(IssuanceSessionEvent.OFFER_RESOLVED)
                    .notifySessionUpdate(sessionId, session.notifications)
                call.respond(session.credentialOffer)
            }

            get("authorize", IssuerRoutesDocs.getAuthorizationEndpointDocs()) {
                log.debug { "GET /authorize - Received authorization request" }
                val parameters = call.request.queryParameters.entries()
                    .associate { it.key to it.value }
                log.debug { "GET /authorize - Parameters: ${parameters.keys}" }

                val result = OSSIssuer2Manager.oauth2Provider.createAuthorizationRequest(parameters)

                when (result) {
                    is AuthorizationRequestResult.Success -> {
                        val request = result.request
                        val issuerState = request.issuerState
                        log.debug { "GET /authorize - Issuer state: $issuerState" }

                        if (issuerState != null) {
                            val session = OSSIssuer2Manager.getSession(issuerState)
                            if (session != null && session.authMethod == AuthenticationMethod.AUTHORIZED) {
                                log.debug { "GET /authorize - Found authorized session: ${session.id}" }
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
                                        log.info { "GET /authorize - Authorization successful for session: $issuerState" }
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
                                        log.warn { "GET /authorize - Authorization failed: ${responseResult.error.description}" }
                                        val httpResponse = OSSIssuer2Manager.oauth2Provider.writeAuthorizationError(
                                            request,
                                            responseResult.error
                                        )
                                        httpResponse.redirectUri?.let { call.respondRedirect(it) }
                                            ?: call.respond(HttpStatusCode.BadRequest, responseResult.error.description ?: "Error")
                                        return@get
                                    }
                                }
                            } else {
                                log.warn { "GET /authorize - Session not found or not authorized: $issuerState" }
                            }
                        } else {
                            log.warn { "GET /authorize - Missing issuer_state parameter" }
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
                log.debug { "POST /token - Received token request" }
                
                val formParameters = call.receiveParameters()
                val parameters = formParameters.entries()
                    .associate { it.key to it.value }
                log.debug { "POST /token - Parameters: ${parameters.mapValues { if (it.key.contains("code", ignoreCase = true)) "[REDACTED]" else it.value }}" }

                val grantType = parameters["grant_type"]?.firstOrNull()
                val preAuthCode = parameters["pre-authorized_code"]?.firstOrNull()
                log.debug { "POST /token - Grant type: $grantType" }

                var sessionId: String? = null
                if (grantType == "urn:ietf:params:oauth:grant-type:pre-authorized_code" && preAuthCode != null) {
                    val record = OSSIssuer2Manager.preAuthorizedCodeRepository.getRecord(preAuthCode)
                    sessionId = (record as? id.walt.issuer2.oauth.InMemoryPreAuthorizedCodeRecord)?.sessionId
                    log.debug { "POST /token - Pre-auth code lookup: sessionId=$sessionId" }
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
                        log.debug { "POST /token - Access token request validated" }
                        val tokenResult = OSSIssuer2Manager.oauth2Provider.createAccessTokenResponse(result.request)
                        when (tokenResult) {
                            is AccessTokenResponseResult.Success -> {
                                log.info { "POST /token - Token issued successfully for session: $sessionId" }
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
                                log.warn { "POST /token - Token response failed: ${tokenResult.error}" }
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
                        log.warn { "POST /token - Token request validation failed: ${result.error.error} - ${result.error.description}" }
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
                log.debug { "POST /credential - Received credential request" }
                
                val authHeader = call.request.header(HttpHeaders.Authorization)
                if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                    log.warn { "POST /credential - Missing or invalid Authorization header" }
                    call.respond(HttpStatusCode.Unauthorized, "Missing or invalid Authorization header")
                    return@post
                }

                val accessToken = authHeader.removePrefix("Bearer ")
                log.debug { "POST /credential - Access token received (length: ${accessToken.length})" }

                val body = call.receive<JsonObject>()
                log.debug { "POST /credential - Request body: $body" }
                
                val parameters = body.mapValues { listOf(it.value.toString().trim('"')) }

                val credentialConfigId = body["credential_configuration_id"]?.jsonPrimitive?.content
                    ?: body["credential_identifier"]?.jsonPrimitive?.content

                if (credentialConfigId == null) {
                    log.warn { "POST /credential - Missing credential_configuration_id in request" }
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "invalid_request", "error_description" to "Missing credential_configuration_id")
                    )
                    return@post
                }
                log.debug { "POST /credential - Credential config ID: $credentialConfigId" }

                val credentialConfig = OSSIssuer2Manager.getCredentialConfiguration(credentialConfigId)
                if (credentialConfig == null) {
                    log.warn { "POST /credential - Unknown credential configuration: $credentialConfigId" }
                    log.debug { "POST /credential - Available configurations: ${OSSIssuer2Manager.getCredentialConfigurations().keys}" }
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "invalid_request", "error_description" to "Unknown credential configuration: $credentialConfigId")
                    )
                    return@post
                }

                val session = findSessionByCredentialConfig(credentialConfigId)
                if (session == null) {
                    log.warn { "POST /credential - No active session for credential config: $credentialConfigId" }
                    log.debug { "POST /credential - Active sessions: ${OSSIssuer2Manager.sessions.values.map { "${it.id} -> ${it.credentialConfigurationId} (${it.status})" }}" }
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "invalid_request", "error_description" to "No active session for this credential")
                    )
                    return@post
                }
                log.debug { "POST /credential - Found session: ${session.id} (status: ${session.status})" }

                val profile = OSSIssuer2Manager.getProfile(session.profileId)
                if (profile == null) {
                    log.error { "POST /credential - Profile not found: ${session.profileId}" }
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "server_error", "error_description" to "Profile not found")
                    )
                    return@post
                }

                try {
                    log.debug { "POST /credential - Resolving issuer key for profile: ${profile.profileId}" }
                    val issuerKey = KeyManager.resolveSerializedKey(session.issuanceRequest.issuerKey)
                    val issuerId = session.issuanceRequest.issuerDid ?: OSSIssuer2Manager.getBaseUrl()
                    log.debug { "POST /credential - Issuer ID: $issuerId" }

                    val oauthSession = DefaultSession().withSubject(session.id)
                    val credentialResult = OSSIssuer2Manager.oauth2Provider.createCredentialRequest(
                        parameters,
                        oauthSession,
                        null
                    )

                    when (credentialResult) {
                        is CredentialRequestResult.Success -> {
                            log.debug { "POST /credential - Credential request validated successfully" }
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
                                    log.info { "POST /credential - Credential issued successfully for session: ${session.id}" }
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
                                    log.warn { "POST /credential - Credential response failed: ${responseResult.error}" }
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
                            log.warn { "POST /credential - Credential request validation failed: ${credentialResult.error.error} - ${credentialResult.error.description}" }
                            call.respond(
                                HttpStatusCode.BadRequest,
                                mapOf("error" to credentialResult.error.error, "error_description" to credentialResult.error.description)
                            )
                        }
                    }
                } catch (e: Exception) {
                    log.error(e) { "POST /credential - Error issuing credential for session ${session.id}" }
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
                log.debug { "GET /profiles - Listing all profiles" }
                val profiles = OSSIssuer2Manager.getProfiles().map { profile ->
                    ProfileSummary(
                        profileId = profile.profileId,
                        name = profile.name,
                        credentialConfigurationId = profile.credentialConfigurationId,
                    )
                }
                log.debug { "GET /profiles - Found ${profiles.size} profiles" }
                call.respond(profiles)
            }

            get("{profileId}", IssuerRoutesDocs.getViewProfileDocs()) {
                val profileId = call.parameters.getOrFail("profileId")
                log.debug { "GET /profiles/$profileId - Fetching profile details" }
                val profile = OSSIssuer2Manager.getProfile(profileId)
                if (profile == null) {
                    log.warn { "GET /profiles/$profileId - Profile not found" }
                    return@get call.respond(HttpStatusCode.NotFound, "Profile not found")
                }

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
                log.debug { "POST /profiles/$profileId/offers - Creating credential offer" }
                val body = call.receive<CredentialOfferCreateRequestBody>()
                log.debug { "POST /profiles/$profileId/offers - Request body: authMethod=${body.authMethod}, valueMode=${body.valueMode}" }

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
                    log.info { "POST /profiles/$profileId/offers - Created session: ${session.id}" }
                    
                    session.toSessionUpdate(IssuanceSessionEvent.OFFER_CREATED)
                        .notifySessionUpdate(session.id, session.notifications)
                    
                    call.respond(HttpStatusCode.Created, session.toCreationResponse())
                } catch (e: IllegalArgumentException) {
                    log.warn { "POST /profiles/$profileId/offers - Invalid request: ${e.message}" }
                    call.respond(HttpStatusCode.NotFound, e.message ?: "Profile not found")
                } catch (e: Exception) {
                    log.error(e) { "POST /profiles/$profileId/offers - Error creating credential offer" }
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
