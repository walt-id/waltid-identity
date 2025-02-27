package id.walt.issuer.issuance


import id.walt.oid4vc.OpenID4VC
import id.walt.oid4vc.data.*
import id.walt.oid4vc.data.dif.PresentationDefinition
import id.walt.oid4vc.data.dif.PresentationDefinition.Companion.generateDefaultEBSIV3InputDescriptor
import id.walt.oid4vc.data.dif.PresentationSubmission
import id.walt.oid4vc.definitions.JWTClaims
import id.walt.oid4vc.errors.*
import id.walt.oid4vc.providers.TokenTarget
import id.walt.oid4vc.requests.AuthorizationRequest
import id.walt.oid4vc.requests.BatchCredentialRequest
import id.walt.oid4vc.requests.CredentialRequest
import id.walt.oid4vc.requests.TokenRequest
import id.walt.oid4vc.responses.AuthorizationErrorCode
import id.walt.oid4vc.responses.CredentialErrorCode
import id.walt.oid4vc.responses.PushedAuthorizationResponse
import id.walt.oid4vc.util.randomUUID
import id.walt.policies.Verifier
import id.walt.policies.models.PolicyRequest.Companion.parsePolicyRequests
import id.walt.sdjwt.JWTVCIssuerMetadata
import id.walt.sdjwt.SDJWTVCTypeMetadata
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.smiley4.ktorswaggerui.dsl.routes.OpenApiRequest
import io.github.smiley4.ktorswaggerui.dsl.routing.get
import io.github.smiley4.ktorswaggerui.dsl.routing.route
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import io.ktor.util.pipeline.*
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.serialization.json.*
import kotlin.time.Duration.Companion.minutes

object OidcApi : CIProvider() {

    private val logger = KotlinLogging.logger { }

    private fun Application.oidcRoute(build: Route.() -> Unit) {
        routing {
            //   authenticate("authenticated") {
            /*route("oidc", {
                tags = listOf("oidc")
            }) {*/
            build.invoke(this)
            /*}*/
        }
        //}
    }

    fun Application.oidcApi() = oidcRoute {
        route("", {
            tags = listOf("oidc")
        }) {
            get("{standardVersion}/.well-known/openid-configuration", {
                request {
                    standardVersionPathParameter()
                }
            }) {
                val metadata = getMetadataForVersion(
                    standardVersion = call.parameters["standardVersion"] ?: throw IllegalArgumentException("standardVersion parameter is required"),
                )

                call.respond(metadata.toJSON())
            }

            get("{standardVersion}/.well-known/openid-credential-issuer", {
                request {
                    standardVersionPathParameter()
                }
            }) {
                val metadata = getMetadataForVersion(
                    standardVersion = call.parameters["standardVersion"] ?: throw IllegalArgumentException("standardVersion parameter is required"),
                )

                call.respond(metadata.toJSON())
            }

            get("{standardVersion}/.well-known/oauth-authorization-server", {
                request {
                    standardVersionPathParameter()
                }
            }) {
                val metadata = getMetadataForVersion(
                    standardVersion = call.parameters["standardVersion"] ?: throw IllegalArgumentException("standardVersion parameter is required"),
                )

                call.respond(metadata.toJSON())
            }

            get("/.well-known/jwt-vc-issuer/{standardVersion}", {
                request {
                    standardVersionPathParameter()
                }
            }) {
                call.respond(HttpStatusCode.OK, JWTVCIssuerMetadata(issuer = metadata.issuer, jwksUri = metadata.jwksUri))
            }

            get("/.well-known/vct/{standardVersion}/{type}", {
                request{
                    standardVersionPathParameter()
                    typePathParameter()
                }
            }) {
                val credType = call.parameters["type"] ?: throw IllegalArgumentException("Type required")

                // issuer api is the <authority>
                val vctMetadata = metadata.getVctBySupportedCredentialConfiguration(baseUrl, credType)
                call.respond(
                    HttpStatusCode.OK,
                    when (vctMetadata.sdJwtVcTypeMetadata != null) {
                        true -> SDJWTVCTypeMetadata.fromJSON(vctMetadata.sdJwtVcTypeMetadata!!.toJSON())
                        else -> SDJWTVCTypeMetadata(vct = vctMetadata.vct!!, name = credType, description = "$credType Verifiable Credential")
                    }
                )
            }
        }

        route("", {
            tags = listOf("oidc")
        }) {

            post("{standardVersion}/par") {
                val authReq = AuthorizationRequest.fromHttpParameters(call.receiveParameters().toMap())
                try {
                    val session = initializeIssuanceSession(authReq, 5.minutes, null)
                    call.respond(
                        PushedAuthorizationResponse.success(
                        requestUri = "${OpenID4VC.PUSHED_AUTHORIZATION_REQUEST_URI_PREFIX}${session.id}",
                        expiresIn = session.expirationTimestamp - Clock.System.now()
                    ).toJSON())
                } catch (exc: AuthorizationError) {
                    logger.error(exc) { "Authorization error: " }
                    call.respond(HttpStatusCode.BadRequest, exc.toPushedAuthorizationErrorResponse().toJSON())
                }
            }

            get("{standardVersion}/jwks", {
                request {
                    standardVersionPathParameter()
                }
            }) {
                call.respond(HttpStatusCode.OK, getJwksSessions())
            }

            get("{standardVersion}/authorize") {
                val standardVersion = call.parameters["standardVersion"] ?: throw IllegalArgumentException("standardVersion parameter is required")
                val authReq = runBlocking { AuthorizationRequest.fromHttpParametersAuto(call.parameters.toMap()) }

                try {
                    val issuanceSession = authReq.issuerState?.let { getSession(it) } ?: error("No issuance session found for given issuer state, or issuer state was empty: ${authReq.issuerState}")
                    val authMethod = issuanceSession.issuanceRequests.firstOrNull()?.authenticationMethod ?: AuthenticationMethod.NONE
                    val authResp: Any = when {
                        ResponseType.Code in authReq.responseType -> {
                            when (authMethod) {
                                AuthenticationMethod.PWD -> {
                                    call.response.apply {
                                        status(HttpStatusCode.Found)
                                        header(
                                            name = HttpHeaders.Location,
                                            value = "${metadata.issuer}/external_login/${authReq.toHttpQueryString()}"
                                        )
                                    }
                                    return@get
                                }

                                AuthenticationMethod.ID_TOKEN -> {
                                    val authServerState = randomUUID()

                                    initializeIssuanceSession(
                                        authorizationRequest = authReq,
                                        expiresIn = 5.minutes,
                                        authServerState = authServerState
                                    )

                                    OpenID4VC.processCodeFlowAuthorizationWithAuthorizationRequest(
                                        authorizationRequest = authReq,
                                        authServerState = authServerState,
                                        responseType = ResponseType.IdToken,
                                        providerMetadata = getMetadataForVersion(standardVersion),
                                        tokenKey = CI_TOKEN_KEY,
                                        isJar = issuanceSession.issuanceRequests.first().useJar
                                    )
                                }

                                AuthenticationMethod.VP_TOKEN -> {
                                    val authServerState = randomUUID()

                                    initializeIssuanceSession(
                                        authorizationRequest = authReq,
                                        expiresIn = 5.minutes,
                                        authServerState = authServerState
                                    )

                                    val vpProfile = issuanceSession.issuanceRequests.first().vpProfile ?: OpenId4VPProfile.DEFAULT

                                    val credFormat = issuanceSession.issuanceRequests.first().credentialFormat
                                        ?: when(vpProfile) {
                                            OpenId4VPProfile.HAIP -> CredentialFormat.sd_jwt_vc
                                            OpenId4VPProfile.ISO_18013_7_MDOC -> CredentialFormat.mso_mdoc
                                            OpenId4VPProfile.EBSIV3 -> CredentialFormat.jwt_vc
                                            else -> CredentialFormat.jwt_vc_json
                                        }

                                    val vpRequestValue = issuanceSession.issuanceRequests.first().vpRequestValue
                                        ?: throw IllegalArgumentException("missing vpRequestValue parameter")

                                    // Generate Presentation Definition
                                    val requestCredentialsArr = buildJsonArray { add(vpRequestValue) }
                                    val requestedTypes = requestCredentialsArr.map {
                                        when (it) {
                                            is JsonPrimitive -> it.contentOrNull
                                            is JsonObject -> it["credential"]?.jsonPrimitive?.contentOrNull
                                            else -> throw IllegalArgumentException("Invalid JSON type for requested credential: $it")
                                        } ?: throw IllegalArgumentException("Invalid VC type for requested credential: $it")
                                    }

                                    val presentationDefinition = when(vpProfile) {
                                        OpenId4VPProfile.EBSIV3 -> PresentationDefinition(
                                            inputDescriptors = requestedTypes.map { type ->
                                                generateDefaultEBSIV3InputDescriptor(type)
                                            }
                                        )
                                        else ->  PresentationDefinition.defaultGenerationFromVcTypesForCredentialFormat(
                                            types = requestedTypes,
                                            format = credFormat
                                        )
                                    }

                                    PresentationDefinition.defaultGenerationFromVcTypesForCredentialFormat(
                                        types = requestedTypes,
                                        format = credFormat
                                    )

                                    OpenID4VC.processCodeFlowAuthorizationWithAuthorizationRequest(
                                        authorizationRequest = authReq,
                                        authServerState = authServerState,
                                        responseType = ResponseType.VpToken,
                                        providerMetadata = getMetadataForVersion(standardVersion),
                                        tokenKey =  CI_TOKEN_KEY,
                                        isJar = issuanceSession.issuanceRequests.first().useJar,
                                        presentationDefinition = presentationDefinition
                                    )
                                }

                                AuthenticationMethod.NONE -> OpenID4VC.processCodeFlowAuthorization(
                                    authorizationRequest = authReq,
                                    sessionId = issuanceSession.id,
                                    providerMetadata = metadata,
                                    tokenKey = CI_TOKEN_KEY)

                                else -> {
                                    throw AuthorizationError(
                                        authorizationRequest = authReq,
                                        errorCode = AuthorizationErrorCode.invalid_request,
                                        message = "Request Authentication Method is invalid"
                                    )
                                }
                            }
                        }

                        ResponseType.Token in authReq.responseType -> OpenID4VC.processImplicitFlowAuthorization(
                            authorizationRequest = authReq,
                            sessionId = issuanceSession.id,
                            providerMetadata = metadata,
                            tokenKey = CI_TOKEN_KEY
                        )

                        else -> {
                            throw AuthorizationError(
                                authorizationRequest = authReq,
                                errorCode = AuthorizationErrorCode.unsupported_response_type,
                                message = "Response type not supported"
                            )
                        }
                    }

                    val redirectUri = when (authMethod) {
                        AuthenticationMethod.VP_TOKEN, AuthenticationMethod.ID_TOKEN -> authReq.clientMetadata!!.customParameters["authorization_endpoint"]?.jsonPrimitive?.content ?: "openid://"
                        else -> if (authReq.isReferenceToPAR) {
                                    val pushedSession = getPushedAuthorizationSession(authReq)
                                    pushedSession.authorizationRequest?.redirectUri
                                } else {
                                    authReq.redirectUri
                                } ?: throw AuthorizationError(
                                        authorizationRequest = authReq,
                                        errorCode = AuthorizationErrorCode.invalid_request,
                                        message = "No redirect_uri found for this authorization request"
                                )
                    }

                    logger.info { "Redirect Uri is: $redirectUri" }

                    call.response.apply {

                        status(HttpStatusCode.Found)

                        val defaultResponseMode = if (authReq.responseType.contains(ResponseType.Code)) ResponseMode.query else ResponseMode.fragment

                        authResp as IHTTPDataObject

                        header(
                            name = HttpHeaders.Location,
                            value = authResp.toRedirectUri(
                                redirectUri = redirectUri,
                                responseMode = authReq.responseMode ?: defaultResponseMode
                            )
                        )
                    }

                } catch (authExc: AuthorizationError) {
                    logger.error(authExc) { "Authorization error: " }

                    call.response.apply {

                        status(HttpStatusCode.Found)

                        header(
                            name = HttpHeaders.Location,
                            value = URLBuilder(authExc.authorizationRequest.redirectUri!!).apply {
                                parameters.appendAll(
                                    parametersOf(
                                        authExc.toAuthorizationErrorResponse().toHttpParameters()
                                    )
                                )
                            }.buildString())
                    }
                }
            }

            post("{standardVersion}/direct_post") {
                val params = call.receiveParameters().toMap()
                logger.info { "/direct_post params: $params" }

                if (params["state"]?.get(0) == null || (params["id_token"]?.get(0) == null && params["vp_token"]?.get(0) == null)) {
                    call.respond(HttpStatusCode.BadRequest, "missing state/id_token/vp_token parameter")
                    throw IllegalArgumentException("missing missing state/id_token/vp_token  parameter")
                }

                try {
                    val state = params["state"]?.get(0)!!
                    if (params["id_token"]?.get(0) != null) {
                        val idToken = params["id_token"]?.get(0)!!

                        // Verify and Parse ID Token
                        OpenID4VC.verifyAndParseIdToken(idToken)

                    } else {
                        val vpToken = params["vp_token"]?.get(0)!!
                        val presSub = params["presentation_submission"]?.get(0)!!
                        val presentationFormat = PresentationSubmission.fromJSONString(presSub).descriptorMap.firstOrNull()?.format ?: throw IllegalArgumentException("No presentation submission or presentation format found.")

                        // Verify and Parse VP Token
                        val policies = Json.parseToJsonElement("""["signature", "expired", "not-before"]""").jsonArray.parsePolicyRequests()

                        Verifier.verifyPresentation(
                            format = presentationFormat,
                            vpToken = vpToken,
                            vpPolicies = policies,
                            globalVcPolicies = policies,
                            specificCredentialPolicies = emptyMap(),
                            presentationContext = mapOf("presentationSubmission" to presSub)
                        )
                    }

                    // Process response
                    val session = getSessionByAuthServerState(state) ?: throw IllegalStateException("No session found for given state parameter")
                    val resp = OpenID4VC.processDirectPost(
                        authorizationRequest = session.authorizationRequest ?: throw IllegalStateException("Session for given state has no authorization request"),
                        sessionId = session.id,
                        providerMetadata = metadata,
                        tokenKey = CI_TOKEN_KEY
                    )

                    // Get the authorization_endpoint parameter which is the redirect_uri from the Authorization Request Parameter
                    val redirectUri = getSessionByAuthServerState(state)!!.authorizationRequest!!.redirectUri!!

                    call.response.apply {
                        status(HttpStatusCode.Found)
                        header(
                            name = HttpHeaders.Location,
                            value = resp.toRedirectUri(
                                redirectUri = redirectUri,
                                responseMode = ResponseMode.query
                            )
                        )
                    }

                } catch (exc: TokenError) {
                    call.respond(HttpStatusCode.BadRequest, exc.toAuthorizationErrorResponse().toJSON())
                    throw IllegalArgumentException(exc.toAuthorizationErrorResponse().toString())
                }
            }

            post("{standardVersion}/token") {
                val params = call.receiveParameters().toMap()

                logger.info { "/token params: $params" }

                val tokenReq = TokenRequest.fromHttpParameters(params)
                logger.info { "/token tokenReq from params: $tokenReq" }

                try {
                    val tokenResp = processTokenRequest(tokenReq)
                    logger.info { "/token tokenResp: $tokenResp" }
                    call.respond(tokenResp.toJSON())
                } catch (exc: TokenError) {
                    logger.error(exc) { "Token error: " }
                    call.respond(HttpStatusCode.BadRequest, exc.toAuthorizationErrorResponse().toJSON())
                }
            }

            get("{standardVersion}/credentialOffer", {
                summary = "Gets a credential offer based on the session id"
                request {
                    queryParameter<String>("id") { required = true }
                }
            }) {
                val sessionId = call.parameters["id"] ?: throw BadRequestException("Missing parameter \"id\"")
                val issuanceSession = getSession(sessionId)
                    ?: throw NotFoundException("No active issuance session found by the given id")
                val credentialOffer = issuanceSession.credentialOffer
                    ?: throw BadRequestException("Session has no credential offer set")

                issuanceSession.callbackUrl?.let {
                    sendCallback(
                        sessionId, "resolved_credential_offer", credentialOffer.toJSON(), it
                    )
                }

                call.respond(credentialOffer.toJSON())
            }

            post("{standardVersion}/credential") {
                val accessToken = call.request.header(HttpHeaders.Authorization)?.substringAfter(" ") ?: call.respond(HttpStatusCode.Unauthorized)

                try {

                    val parsedToken = OpenID4VC.verifyAndParseToken(
                        token = accessToken.toString(),
                        issuer = metadata.issuer!!,
                        target = TokenTarget.ACCESS,
                        tokenKey = CI_TOKEN_KEY
                    )

                    val credentialRequest = CredentialRequest.fromJSON(call.receive<JsonObject>())

                    val session = parsedToken[JWTClaims.Payload.subject]?.jsonPrimitive?.content?.let { getSession(it) }
                        ?: throw CredentialError(
                            credentialRequest = credentialRequest,
                            errorCode = CredentialErrorCode.invalid_request,
                            message = "Session not found for access token"
                        )

                    call.respond(
                        generateCredentialResponse(
                            credentialRequest = credentialRequest,
                            session = session
                        ).toJSON()
                    )
                } catch (exc: CredentialError) {
                    logger.error(exc) { "Credential error: " }
                    call.respond(HttpStatusCode.BadRequest, exc.toCredentialErrorResponse().toJSON())
                }

            }

            post("{standardVersion}/credential_deferred") {
                val accessToken = call.request.header(HttpHeaders.Authorization)?.substringAfter(" ")
                if (accessToken.isNullOrEmpty() || !OpenID4VC.verifyTokenSignature(
                        TokenTarget.DEFERRED_CREDENTIAL,
                        accessToken
                    )
                ) {
                    call.respond(HttpStatusCode.Unauthorized)
                } else {
                    try {
                        call.respond(generateDeferredCredentialResponse(accessToken).toJSON())
                    } catch (exc: DeferredCredentialError) {
                        logger.error(exc) { "DeferredCredentialError: " }
                        call.respond(HttpStatusCode.BadRequest, exc.toCredentialErrorResponse().toJSON())
                    }
                }
            }

            post("{standardVersion}/batch_credential") {
                val accessToken = call.request.header(HttpHeaders.Authorization)?.substringAfter(" ")
                val parsedToken = accessToken?.let { OpenID4VC.verifyAndParseToken(it, metadata.issuer!!, TokenTarget.ACCESS, CI_TOKEN_KEY) }
                if (parsedToken == null) {
                    call.respond(HttpStatusCode.Unauthorized)
                } else {
                    val req = BatchCredentialRequest.fromJSON(call.receive())
                    try {
                        val session = parsedToken[JWTClaims.Payload.subject]?.jsonPrimitive?.content?.let { getSession(it) }
                            ?: throw BatchCredentialError(req, CredentialErrorCode.invalid_request, "Session not found for access token")
                        call.respond(generateBatchCredentialResponse(req, session).toJSON())
                    } catch (exc: BatchCredentialError) {
                        logger.error(exc) { "BatchCredentialError: " }
                        call.respond(HttpStatusCode.BadRequest, exc.toBatchCredentialErrorResponse().toJSON())
                    }
                }
            }

            val authorizationPhase = PipelinePhase("Authorization")

            authenticate("auth-oauth") {
                // intercept request and store the state
                this.insertPhaseBefore(ApplicationCallPipeline.Call, authorizationPhase)
                this.intercept(authorizationPhase) {
                    val externalAuthReq =
                        when (val externalAuthReqStr = call.response.headers.allValues().toMap()["Location"]) {
                            null -> null
                            else -> runBlocking {
                                AuthorizationRequest.fromHttpQueryString(externalAuthReqStr[0].substringAfter("?"))
                            }
                        }
                    val authReq = when (val internalAuthReqParams = call.parameters["internalAuthReq"]) {
                        null -> null
                        else -> runBlocking { AuthorizationRequest.fromHttpQueryString(internalAuthReqParams) }
                    }
                    if (authReq != null && externalAuthReq != null) {
                        initializeIssuanceSession(authReq, 5.minutes, externalAuthReq.state)
                    }

                }

                get("/external_login/{internalAuthReq}") {
                    // Redirects to 'authorizeUrl' automatically
                }

                get("/callback") {
                    // currentPrincipal contains the Access/ID/Refresh tokens from the Authorization Server
                    val currentPrincipal: OAuthAccessTokenResponse.OAuth2? = call.principal()

                    // should redirect to authorization request redirect uri with the code
                    val session = getSessionByAuthServerState(call.request.rawQueryParameters.toMap()["state"]!![0])
                    val authResp = OpenID4VC.processCodeFlowAuthorization(session?.authorizationRequest!!, session.id, metadata, CI_TOKEN_KEY)

                    val redirectUri = when (session.authorizationRequest.isReferenceToPAR) {
                        true -> getPushedAuthorizationSession(session.authorizationRequest).authorizationRequest?.redirectUri
                        false -> session.authorizationRequest.redirectUri
                    } ?: throw AuthorizationError(
                        session.authorizationRequest,
                        AuthorizationErrorCode.invalid_request,
                        "No redirect_uri found for this authorization request"
                    )

                    logger.info { "Redirect Uri is: $redirectUri" }

                    call.response.apply {
                        status(HttpStatusCode.Found)
                        val defaultResponseMode =
                            if (session.authorizationRequest.responseType.contains(ResponseType.Code)) ResponseMode.query else ResponseMode.fragment
                        header(
                            HttpHeaders.Location,
                            authResp.toRedirectUri(
                                redirectUri,
                                session.authorizationRequest.responseMode ?: defaultResponseMode
                            )
                        )
                    }
                }
            }
        }
    }

    private fun OpenApiRequest.standardVersionPathParameter() = pathParameter<String>("standardVersion") {
        description = "The value of the standard version. Supported values are: draft13 and draft11"
        example("Example") { value = "draft13" }
        required = true
    }

    private fun OpenApiRequest.typePathParameter() = pathParameter<String>("type") {
        description = "The value of the credential type."
        example("Example") { value = "identity_credential" }
        required = true
    }

    private fun getPushedAuthorizationSession(authorizationRequest: AuthorizationRequest): IssuanceSession {
        return authorizationRequest.requestUri?.let {
            getVerifiedSession(OpenID4VC.getPushedAuthorizationSessionId(it)) ?: throw AuthorizationError(
                authorizationRequest,
                AuthorizationErrorCode.invalid_request,
                "No session found for given request URI, or session expired"
            )
        } ?: throw AuthorizationError(
            authorizationRequest,
            AuthorizationErrorCode.invalid_request,
            "Authorization request does not refer to a pushed authorization session"
        )
    }
}
