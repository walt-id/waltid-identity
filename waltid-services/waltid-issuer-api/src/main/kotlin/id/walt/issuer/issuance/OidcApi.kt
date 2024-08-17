package id.walt.issuer.issuance


import id.walt.crypto.utils.Base64Utils.base64UrlDecode
import id.walt.credentials.verification.Verifier
import id.walt.credentials.verification.models.PolicyRequest.Companion.parsePolicyRequests
import id.walt.oid4vc.data.*
import id.walt.oid4vc.data.dif.PresentationDefinition
import id.walt.oid4vc.errors.*
import id.walt.oid4vc.providers.TokenTarget
import id.walt.oid4vc.requests.AuthorizationRequest
import id.walt.oid4vc.requests.BatchCredentialRequest
import id.walt.oid4vc.requests.CredentialRequest
import id.walt.oid4vc.requests.TokenRequest
import id.walt.oid4vc.responses.AuthorizationErrorCode
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.smiley4.ktorswaggerui.dsl.routing.get
import io.github.smiley4.ktorswaggerui.dsl.routing.route
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import io.ktor.util.pipeline.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.Duration.Companion.minutes


@Serializable
data class UserData(val email: String, val password: String, val id: String? = null)

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

    @OptIn(ExperimentalEncodingApi::class)
    fun Application.oidcApi() = oidcRoute {
        route("", {
            tags = listOf("oidc")
        }) {
            get("/.well-known/openid-configuration") {
                call.respond(metadata.toJSON())
            }
            get("/.well-known/openid-credential-issuer") {
                call.respond(metadata.toJSON())
            }
            get("/.well-known/oauth-authorization-server") {
                call.respond(metadata.toJSON())
            }
            get("/.well-known/jwt-issuer") {
                call.respond(metadata.toJSON())
            }
        }

        route("", {
            tags = listOf("oidc")
        }) {

            post("/par") {
                val authReq = AuthorizationRequest.fromHttpParameters(call.receiveParameters().toMap())
                try {
                    val session = initializeAuthorization(authReq, 5.minutes, null)
                    call.respond(getPushedAuthorizationSuccessResponse(session).toJSON())
                } catch (exc: AuthorizationError) {
                    logger.error(exc) { "Authorization error: " }
                    call.respond(HttpStatusCode.BadRequest, exc.toPushedAuthorizationErrorResponse().toJSON())
                }
            }

            get("/jwks") {
                var jwks = buildJsonObject {}
                OidcApi.sessionCredentialPreMapping.getAll().forEach {
                    it.forEach {
                        jwks = buildJsonObject {
                            put("keys", buildJsonArray {
                                val jwkWithKid = buildJsonObject {
                                    it.issuerKey.key.getPublicKey().exportJWKObject().forEach {
                                        put(it.key, it.value)
                                    }
                                    put("kid", it.issuerKey.key.getPublicKey().getKeyId())
                                }
                                add(jwkWithKid)
                                jwks.forEach {
                                    it.value.jsonArray.forEach {
                                        add(it)
                                    }
                                }
                            })
                        }
                    }
                }
                call.respond(HttpStatusCode.OK, jwks)
            }

            get("/authorize") {
                val authReq = runBlocking { AuthorizationRequest.fromHttpParametersAuto(call.parameters.toMap()) }
                try {
                    val issuanceSessionData = OidcApi.sessionCredentialPreMapping[authReq.issuerState!!] ?: error("No such pre mapping: ${authReq.issuerState}")
                    val authMethod = issuanceSessionData.first().request.authenticationMethod ?: AuthenticationMethod.NONE
                    val authResp: Any = when {
                        ResponseType.Code in authReq.responseType -> {
                            when (authMethod) {
                                AuthenticationMethod.PWD -> {
                                    call.response.apply {
                                        status(HttpStatusCode.Found)
                                        header(
                                            HttpHeaders.Location,
                                            "${metadata.issuer}/external_login/${authReq.toHttpQueryString()}"
                                        )
                                    }
                                    return@get
                                }

                                AuthenticationMethod.ID_TOKEN -> {
                                    val idTokenRequestJwtKid = issuanceSessionData.first().issuerKey.key.getKeyId()
                                    val idTokenRequestJwtPrivKey = issuanceSessionData.first().issuerKey
                                    processCodeFlowAuthorizationWithAuthorizationRequest(
                                        authReq,
                                        idTokenRequestJwtKid,
                                        idTokenRequestJwtPrivKey.key,
                                        ResponseType.IdToken,
                                        issuanceSessionData.first().request.useJar
                                    )
                                }

                                AuthenticationMethod.VP_TOKEN -> {
                                    val vpTokenRequestJwtKid = issuanceSessionData.first().issuerKey.key.getKeyId()
                                    val vpTokenRequestJwtPrivKey = issuanceSessionData.first().issuerKey
                                    val vpProfile  = issuanceSessionData.first().request.vpProfile ?: OpenId4VPProfile.DEFAULT
                                    val vpRequestValue  = issuanceSessionData.first().request.vpRequestValue ?: throw IllegalArgumentException("missing vpRequestValue parameter")

                                    // Generate Presentation Definition
                                    val requestCredentialsArr = buildJsonArray { add(vpRequestValue) }
                                    val requestedTypes = requestCredentialsArr.map {
                                        when (it) {
                                            is JsonPrimitive -> it.contentOrNull
                                            is JsonObject -> it["credential"]?.jsonPrimitive?.contentOrNull
                                            else -> throw IllegalArgumentException("Invalid JSON type for requested credential: $it")
                                        } ?: throw IllegalArgumentException("Invalid VC type for requested credential: $it")
                                    }
                                    val presentationDefinition = PresentationDefinition.primitiveGenerationFromVcTypes(requestedTypes, vpProfile)

                                    processCodeFlowAuthorizationWithAuthorizationRequest(
                                        authReq,
                                        vpTokenRequestJwtKid,
                                        vpTokenRequestJwtPrivKey.key,
                                        ResponseType.VpToken,
                                        issuanceSessionData.first().request.useJar,
                                        presentationDefinition
                                    )
                                }

                                AuthenticationMethod.NONE -> processCodeFlowAuthorization(authReq)

                                else -> {
                                    throw AuthorizationError(
                                        authReq,
                                        AuthorizationErrorCode.invalid_request,
                                        "Request Authentication Method is invalid"
                                    )
                                }
                            }
                        }

                        ResponseType.Token in authReq.responseType -> processImplicitFlowAuthorization(authReq)

                        else -> {
                            throw AuthorizationError(
                                authReq,
                                AuthorizationErrorCode.unsupported_response_type,
                                "Response type not supported"
                            )
                        }
                    }

                    val redirectUri = when(authMethod) {
                        AuthenticationMethod.VP_TOKEN, AuthenticationMethod.ID_TOKEN -> authReq.clientMetadata!!.customParameters["authorization_endpoint"]?.jsonPrimitive?.content ?: "openid://"
                        else -> if (authReq.isReferenceToPAR) {
                                    getPushedAuthorizationSession(authReq).authorizationRequest?.redirectUri
                                } else {
                                    authReq.redirectUri
                                } ?: throw AuthorizationError(
                                    authReq,
                                    AuthorizationErrorCode.invalid_request,
                                    "No redirect_uri found for this authorization request"
                                )
                    }

                    logger.info { "Redirect Uri is: $redirectUri" }

                    call.response.apply {
                        status(HttpStatusCode.Found)
                        val defaultResponseMode =
                            if (authReq.responseType.contains(ResponseType.Code)) ResponseMode.query else ResponseMode.fragment
                        authResp as IHTTPDataObject
                        header(
                            HttpHeaders.Location,
                            authResp.toRedirectUri(redirectUri, authReq.responseMode ?: defaultResponseMode)
                        )
                    }
                } catch (authExc: AuthorizationError) {
                    logger.error(authExc) { "Authorization error: " }
                    call.response.apply {
                        status(HttpStatusCode.Found)
                        header(HttpHeaders.Location, URLBuilder(authExc.authorizationRequest.redirectUri!!).apply {
                            parameters.appendAll(
                                parametersOf(
                                    authExc.toAuthorizationErrorResponse().toHttpParameters()
                                )
                            )
                        }.buildString())
                    }
                }
            }
        }

        post("/direct_post") {
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
                   verifyAndParseIdToken(idToken)

                } else  {
                    val vpToken = params["vp_token"]?.get(0)!!
                    val presSub = params["presentation_submission"]?.get(0)!!

                    // Verify and Parse VP Token
                    val policies = Json.parseToJsonElement("""["signature", "expired", "not-before"]""").jsonArray.parsePolicyRequests()

                   Verifier.verifyPresentation(vpTokenJwt = vpToken, vpPolicies = policies, globalVcPolicies = policies, specificCredentialPolicies = emptyMap(), mapOf("presentationSubmission" to presSub))
                }

                // Process response
                val resp = processDirectPost(state, buildJsonObject { })

                // Get the authorization_endpoint parameter which is the redirect_uri from the Authorization Request Parameter
                val redirectUri = getSessionByAuthServerState(state)!!.authorizationRequest!!.redirectUri!!

                call.response.apply {
                    status(HttpStatusCode.Found)
                    header(HttpHeaders.Location, resp.toRedirectUri(redirectUri, ResponseMode.query))
                }

            } catch (exc: TokenError) {
                call.respond(HttpStatusCode.BadRequest, exc.toAuthorizationErrorResponse().toJSON())
                throw IllegalArgumentException(exc.toAuthorizationErrorResponse().toString())
            }
        }

        post("/token") {
            val params = call.receiveParameters().toMap()

            logger.info { "/token params: $params" }

            val tokenReq = TokenRequest.fromHttpParameters(params)
            logger.info { "/token tokenReq from params: $tokenReq" }

            try {
                val tokenResp = processTokenRequest(tokenReq)
                logger.info { "/token tokenResp: $tokenResp" }

                    val sessionId = Json.parseToJsonElement(
                        (tokenResp.accessToken
                            ?: throw IllegalArgumentException("No access token was responded with tokenResp?")).split(
                            "."
                        )[1].base64UrlDecode().decodeToString()
                    ).jsonObject["sub"]?.jsonPrimitive?.contentOrNull
                        ?: throw IllegalArgumentException("Could not get session ID from token response!")
                    val nonceToken = tokenResp.cNonce
                        ?: throw IllegalArgumentException("No nonce token was responded with the tokenResp?")
                    OidcApi.mapSessionIdToToken(
                        sessionId,
                        nonceToken
                    )  // TODO: Hack as this is non stateless because of oidc4vc lib API

                call.respond(tokenResp.toJSON())
            } catch (exc: TokenError) {
                logger.error(exc) { "Token error: " }
                call.respond(HttpStatusCode.BadRequest, exc.toAuthorizationErrorResponse().toJSON())
            }
        }
        post("/credential") {
            val accessToken = call.request.header(HttpHeaders.Authorization)?.substringAfter(" ")
            if (accessToken.isNullOrEmpty() || !verifyTokenSignature(TokenTarget.ACCESS, accessToken)) {
                call.respond(HttpStatusCode.Unauthorized)
            } else {
                val credReq = CredentialRequest.fromJSON(call.receive<JsonObject>())
                try {
                    call.respond(generateCredentialResponse(credReq, accessToken).toJSON())
                } catch (exc: CredentialError) {
                    logger.error(exc) { "Credential error: " }
                    call.respond(HttpStatusCode.BadRequest, exc.toCredentialErrorResponse().toJSON())
                }
            }
        }
        post("/credential_deferred") {
            val accessToken = call.request.header(HttpHeaders.Authorization)?.substringAfter(" ")
            if (accessToken.isNullOrEmpty() || !verifyTokenSignature(
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
        post("/batch_credential") {
            val accessToken = call.request.header(HttpHeaders.Authorization)?.substringAfter(" ")
            if (accessToken.isNullOrEmpty() || !verifyTokenSignature(TokenTarget.ACCESS, accessToken)) {
                call.respond(HttpStatusCode.Unauthorized)
            } else {
                val req = BatchCredentialRequest.fromJSON(call.receive())
                try {
                    call.respond(generateBatchCredentialResponse(req, accessToken).toJSON())
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
                                AuthorizationRequest.fromHttpQueryString( externalAuthReqStr[0].substringAfter( "?"))
                            }
                        }
                    val authReq = when (val internalAuthReqParams = call.parameters["internalAuthReq"]) {
                        null -> null
                        else -> runBlocking { AuthorizationRequest.fromHttpQueryString(internalAuthReqParams) }
                    }
                    if (authReq != null && externalAuthReq != null) {
                        initializeAuthorization(authReq, 5.minutes, externalAuthReq.state)
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
                    val authResp = processCodeFlowAuthorization(session?.authorizationRequest!!)

                    val redirectUri = when (session.authorizationRequest!!.isReferenceToPAR) {
                        true -> getPushedAuthorizationSession(session.authorizationRequest!!).authorizationRequest?.redirectUri
                        false-> session.authorizationRequest!!.redirectUri
                    } ?: throw AuthorizationError(
                        session.authorizationRequest!!,
                        AuthorizationErrorCode.invalid_request,
                        "No redirect_uri found for this authorization request"
                    )

                    logger.info { "Redirect Uri is: $redirectUri" }

                    call.response.apply {
                        status(HttpStatusCode.Found)
                        val defaultResponseMode = if (session.authorizationRequest!!.responseType.contains(ResponseType.Code)) ResponseMode.query else ResponseMode.fragment
                        header(
                            HttpHeaders.Location,
                            authResp.toRedirectUri(
                                redirectUri,
                                session.authorizationRequest!!.responseMode ?: defaultResponseMode
                            )
                        )
                    }
                }
            }
        }


    /*
    private val sessionCache = mutableMapOf<String, IssuanceSession>()

    override fun generateCredential(credentialRequest: CredentialRequest): CredentialResult {
        return doGenerateCredential(credentialRequest)
    }

    override fun getDeferredCredential(credentialID: String): CredentialResult {
        TODO("Not yet implemented")
    }

    override fun getSession(id: String): IssuanceSession? {
        return sessionCache[id]
    }

    override fun putSession(id: String, session: IssuanceSession): IssuanceSession? {
        return sessionCache.put(id, session)
    }

    override fun removeSession(id: String): IssuanceSession? {
        return sessionCache.remove(id)
    }

    //private val CI_TOKEN_KEY = KeyService.getService().generate(KeyAlgorithm.RSA)
    //private val CI_DID_KEY = KeyService.getService().generate(KeyAlgorithm.EdDSA_Ed25519)
    //val CI_ISSUER_DID = DidService.create(DidMethod.key, CI_DID_KEY.id)
    override fun signToken(target: TokenTarget, payload: JsonObject, header: JsonObject?, keyId: String?): String {

        TODO()
        //return JwtService.getService().sign(keyId ?: CI_TOKEN_KEY.id, payload.toString())
    }

    override fun verifyTokenSignature(target: TokenTarget, token: String): Boolean = TODO() //JwtService.getService().verify(token).verified

    private fun doGenerateCredential(credentialRequest: CredentialRequest): CredentialResult {
        TODO()
        /*if(credentialRequest.format == CredentialFormat.mso_mdoc) throw CredentialError(credentialRequest, CredentialErrorCode.unsupported_credential_format)
        val types = credentialRequest.types ?: credentialRequest.credentialDefinition?.types ?: throw CredentialError(credentialRequest, CredentialErrorCode.unsupported_credential_type)
        val proofHeader = credentialRequest.proof?.jwt?.let { parseTokenHeader(it) } ?: throw CredentialError(credentialRequest, CredentialErrorCode.invalid_or_missing_proof, message = "Proof must be JWT proof")
        val holderKid = proofHeader[JWTClaims.Header.keyID]?.jsonPrimitive?.content ?: throw CredentialError(credentialRequest, CredentialErrorCode.invalid_or_missing_proof, message = "Proof JWT header must contain kid claim")
        return Signatory.getService().issue(
            types.last(),
            ProofConfig(CI_ISSUER_DID, subjectDid = resolveDIDFor(holderKid)),
            issuer = W3CIssuer(baseUrl),
            storeCredential = false).let {
            when(credentialRequest.format) {
                CredentialFormat.ldp_vc -> Json.decodeFromString<JsonObject>(it)
                else -> JsonPrimitive(it)
            }
        }.let { CredentialResult(credentialRequest.format, it) }*/
    }

    private fun resolveDIDFor(keyId: String): String {
        TODO()

        //return DidUrl.from(keyId).did
    }

     */
}
