package id.walt.ktorauthnz.methods

import id.walt.crypto.utils.JwsUtils.decodeJws
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.Jwk
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.Key as Crypto2Key
import id.walt.crypto2.keys.toStoredSoftwareKey
import id.walt.crypto2.providers.cryptography.CryptographySoftwareKeyProvider
import id.walt.crypto2.serialization.BinaryData
import id.walt.ktorauthnz.AuthContext
import id.walt.ktorauthnz.accounts.identifiers.methods.OIDCIdentifier
import id.walt.ktorauthnz.amendmends.AuthMethodFunctionAmendments
import id.walt.ktorauthnz.KtorAuthnzManager
import id.walt.ktorauthnz.methods.config.OidcAuthConfiguration
import id.walt.ktorauthnz.methods.sessiondata.OidcSessionAuthenticatedData
import id.walt.ktorauthnz.methods.sessiondata.OidcSessionAuthenticatedData.TokenValidationData
import id.walt.ktorauthnz.methods.sessiondata.OidcSessionAuthenticationStepData
import id.walt.ktorauthnz.methods.sessiondata.OidcTokenValidationPolicyData
import id.walt.ktorauthnz.sessions.AuthSession
import id.walt.ktorauthnz.sessions.AuthSessionNextStepRedirectData
import id.walt.ktorauthnz.sessions.SessionManager
import id.walt.ktorauthnz.sessions.SessionTokenCookieHandler
import io.github.smiley4.ktoropenapi.route
import io.klogging.logger
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.util.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import kotlinx.coroutines.CancellationException
import org.kotlincrypto.hash.sha2.SHA256
import org.kotlincrypto.random.CryptoRand
import kotlin.io.encoding.Base64
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant


object OIDC : AuthenticationMethod("oidc") {

    private val log = logger("OIDC")

    private val http = HttpClient {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    internal const val OIDC_SESSION_NAMESPACE = "oidc-session"
    internal const val OIDC_STATE_NAMESPACE = "oidc-state"
    internal const val OIDC_TOKEN_VALIDATION_NAMESPACE = "oidc-token-validation-v2"


    private val configurationCache = mutableMapOf<Url, OpenIdConfiguration>()

    private val jwksCache = mutableMapOf<String, List<JsonObject>>()
    private val crypto2Runtime = CryptoRuntime(listOf(CryptographySoftwareKeyProvider()))

    suspend fun resolveConfiguration(configUrl: Url): OpenIdConfiguration {
        return configurationCache.getOrPut(configUrl) {
            http.get(configUrl).body<OpenIdConfiguration>()
        }
    }

    // --- Data Classes ---

    @Serializable
    data class OpenIdConfiguration(
        val issuer: String,
        @SerialName("authorization_endpoint") val authorizationEndpoint: String,
        @SerialName("token_endpoint") val tokenEndpoint: String,
        @SerialName("userinfo_endpoint") val userinfoEndpoint: String? = null,
        @SerialName("jwks_uri") val jwksUri: String,
        @SerialName("end_session_endpoint") val endSessionEndpoint: String? = null,

        /** note: backchannel_logout_session_required is required! */
        @SerialName("backchannel_logout_supported") val backchannelLogoutSupported: Boolean = false,
        @SerialName("frontchannel_logout_supported") val frontchannelLogoutSupported: Boolean = false,
        @SerialName("response_types_supported") val responseTypesSupported: List<String> = emptyList(),
        @SerialName("subject_types_supported") val subjectTypesSupported: List<String> = emptyList(),
        @SerialName("id_token_signing_alg_values_supported") val idTokenSigningAlgValuesSupported: List<String> = emptyList(),
        @SerialName("scopes_supported") val scopesSupported: List<String> = emptyList(),
        @SerialName("token_endpoint_auth_methods_supported") val tokenEndpointAuthMethodsSupported: List<String> = emptyList(),
        @SerialName("claims_supported") val claimsSupported: List<String> = emptyList(),
    )

    @Serializable
    private data class TokenResponse(
        @SerialName("id_token") val idToken: String,
        @SerialName("access_token") val accessToken: String,
        @SerialName("token_type") val tokenType: String,
        @SerialName("exp") val expiresIn: Int? = null,
        @SerialName("refresh_token") val refreshToken: String? = null,
    )

    // --- Core Authentication Flow ---

    /**
     * @param createdSession: Implicitly created session (containing flow method OIDC)
     * @param redirectTo: Optional client-specified redirect URL after successful auth
     * @return Auth URL to be returned to the user
     */
    suspend fun startOidcSession(createdSession: AuthSession, redirectTo: String? = null): Url {
        val config = createdSession.lookupFlowMethodConfiguration<OidcAuthConfiguration>(OIDC)
        val oidcConfig = config.getOpenIdConfiguration()

        // Validate client-specified redirect URL against allowlist
        val validatedRedirectTo = config.validateRedirectUrl(redirectTo)?.toString()

        val state = generateSecureRandomString()
        val nonce = generateSecureRandomString()
        var codeChallenge: String? = null
        var codeVerifier: String? = null

        if (config.pkceEnabled) {
            codeVerifier = generateSecureRandomString(64)
            codeChallenge = generatePkceChallenge(codeVerifier)
        }

        createdSession.setSessionData(OIDC, OidcSessionAuthenticationStepData(state, nonce, codeVerifier, validatedRedirectTo))
        createdSession.storeExternalIdMapping(OIDC_STATE_NAMESPACE, state)

        val authUrl = URLBuilder(oidcConfig.authorizationEndpoint).apply {
            parameters.apply {
                append("response_type", "code")
                append("scope", "openid profile email")
                append("client_id", config.clientId)
                append("redirect_uri", config.callbackUri)
                append("state", state)
                append("nonce", nonce)
                if (codeChallenge != null) {
                    append("code_challenge", codeChallenge)
                    append("code_challenge_method", "S256")
                }
            }
        }.build()

        return authUrl
    }

    override fun Route.registerAuthenticationRoutes(
        authContext: ApplicationCall.() -> AuthContext,
        functionAmendments: Map<AuthMethodFunctionAmendments, suspend (Any) -> Unit>?
    ) {
        route("oidc") {
            // 1. Start of the login flow
            get("auth") {
                val session = call.getAuthSession(authContext) // starts implicit session
                val redirectTo = call.request.queryParameters["redirect_to"]
                val authUrl = startOidcSession(session, redirectTo)

                val nextStepInfo = AuthSessionNextStepRedirectData(
                    url = authUrl
                )

                call.handleAuthNextStep(
                    session = session,
                    nextStepInfo = nextStepInfo,
                    nextStepDescription = "The OIDC method requires you to open the Authentication URL in your web browser, and follow the steps of your Identity Provider from there. Please go ahead and open the authentication URL \"$authUrl\" in your web browser."
                )
                //call.respondRedirect(authUrl.toString())
            }

            // 2. Callback from the IdP
            get("callback") {
                val params = call.request.queryParameters
                val returnedCode = params["code"] ?: throw IllegalArgumentException("Missing 'code' in callback.")
                val returnedState = params["state"] ?: throw IllegalArgumentException("Missing 'state' in callback.")

                val sessionId = SessionManager.getSessionIdByExternalId(OIDC_STATE_NAMESPACE, returnedState)
                    ?: throw IllegalArgumentException("Unknown OIDC state in callback: $returnedState")
                val session = SessionManager.getSessionById(sessionId)
                val config = session.lookupFlowMethodConfiguration<OidcAuthConfiguration>(OIDC)
                val oidcConfig = config.getOpenIdConfiguration()

                val tempSessionData = session.getSessionData<OidcSessionAuthenticationStepData>(OIDC)
                    ?: throw IllegalStateException("No OIDC session data found or session expired.")

                if (tempSessionData.state != returnedState) {
                    throw IllegalArgumentException("Invalid 'state' parameter. Possible CSRF attack.")
                }

                val tokenResponse = exchangeCodeForTokens(config, returnedCode, tempSessionData.codeVerifier)
                log.trace { "OIDC Token Response: $tokenResponse" }
                val tokenValidationPolicy = OidcTokenValidationPolicyData(oidcConfig)
                val idTokenPayload = validateIdToken(
                    tokenValidationPolicy,
                    tokenResponse.idToken,
                    config.clientId,
                    tempSessionData.nonce,
                )

                val userInfo = userInfo(oidcConfig, tokenResponse.accessToken)
                log.trace { "OIDC UserInfo Response: $userInfo" }

                val subject = idTokenPayload["sub"]?.jsonPrimitive?.content
                    ?: throw IllegalStateException("ID Token is missing 'sub' claim.")

                val sid = idTokenPayload["sid"]?.jsonPrimitive?.content ?: throw IllegalStateException("ID Token is missing 'sid' claim.")

                // --- Our logout is successful

                session.dropExternalIdMapping(OIDC_STATE_NAMESPACE, returnedState) // No longer need this
                session.storeExternalIdMapping(OIDC_SESSION_NAMESPACE, sid) // sid

                val identifier = OIDCIdentifier(oidcConfig.issuer, subject)

                val externalRoles = OidcExternalRoleExtractor.extract(
                    idTokenPayload = idTokenPayload,
                    config = config,
                    issuer = oidcConfig.issuer,
                    subject = subject,
                )

                session.setSessionData(
                    this@OIDC, OidcSessionAuthenticatedData(
                        tokenValidationData = TokenValidationData(
                            oidcConfig,
                        ),
                        oidcIdentifier = identifier,
                        externalRoles = externalRoles,
                        idTokenClaims = idTokenPayload,
                        userInfoClaims = userInfo,
                        idTokenRaw = tokenResponse.idToken,
                    )
                )
                session.setOidcTokenValidationPolicy(tokenValidationPolicy)

                val accountId = identifier.resolveIfExists() ?: run {
                    // No existing account found - use addAccountIdentifierToAccount to create one
                    // The AccountStore implementation (e.g., Enterprise) handles JIT provisioning
                    KtorAuthnzManager.accountStore.addAccountIdentifierToAccount(subject, identifier)
                    identifier.resolveToAccountId()
                }

                val authContext = authContext(call)

                // Determine redirect URL: client-specified (from session) > config default > none
                val redirectUrl = tempSessionData.redirectTo?.let { Url(it) } ?: config.redirectAfterLogin

                if (redirectUrl != null) {
                    call.handleAuthSuccessAndRedirect(
                        session = session,
                        authContext = authContext,
                        accountId = accountId,
                        redirectUrl = redirectUrl
                    )
                } else {
                    call.handleAuthSuccess(
                        session = session,
                        authContext = authContext,
                        accountId = accountId
                    )
                }
            }

            // --- Provider-Initiated Logout Routes ---
            // NOTE: These endpoints are for OIDC provider-initiated logout interoperability.
            // - logout/backchannel is called by the IdP with a logout_token.
            // - logout/frontchannel is called by the IdP/browser flow with iss + sid.
            // They should not be used as the primary user-triggered logout endpoints in client apps.
            post("logout/backchannel") {
                log.trace { "OIDC: Backchannel-logout" }
                val logoutTokenStr = call.receiveParameters()["logout_token"]
                    ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing logout_token")

                // TODO: logout token can also be encrypted

                val jwsParts = logoutTokenStr.decodeJws()

                val sid =
                    jwsParts.payload["sid"]?.jsonPrimitive?.content ?: throw IllegalStateException("Logout token is missing 'sid' claim.")
                log.trace { "Requested back-channel logout for OIDC session: $sid" }

                val sessionId = SessionManager.getSessionIdByExternalId(OIDC_SESSION_NAMESPACE, sid)
                log.trace { "This will logout authnz session: $sessionId" }
                requireNotNull(sessionId) { "Could not find session for external OIDC session ID (sid): $sid" }

                val session = SessionManager.getSessionById(sessionId)
                val sessionOidcConfig = session.getSessionData<OidcSessionAuthenticatedData>(this@OIDC)
                log.trace { "Retrieved data to verify logout token: $sessionOidcConfig" }
                requireNotNull(sessionOidcConfig) { "Could not get OIDC data for session" }
                val tokenValidationPolicy = session.getOidcTokenValidationPolicy()
                    ?: return@post call.respond(
                        HttpStatusCode.BadRequest,
                        "OIDC session has no versioned token validation policy",
                    )
                val clientId = sessionOidcConfig.idTokenClaims?.let(::validatedClientId)
                    ?: return@post call.respond(
                        HttpStatusCode.BadRequest,
                        "OIDC session does not contain a validated client audience",
                    )

                validateLogoutToken(tokenValidationPolicy, logoutTokenStr, clientId)

                session.run {
                    call.logoutAndDeleteCookie()
                }

                call.respond(HttpStatusCode.OK)
            }

            get("logout/frontchannel") {
                log.trace { "OIDC: Frontchannel-logout" }

                val iss = call.parameters.getOrFail("iss")
                val oidcSessionId = call.parameters.getOrFail("sid")

                val sessionId = SessionManager.getSessionIdByExternalId(OIDC_SESSION_NAMESPACE, oidcSessionId)
                requireNotNull(sessionId) { "Could not find session for external OIDC session ID (sid): $oidcSessionId" }

                val session = SessionManager.getSessionById(sessionId)
                val oidcSessionData = session.getSessionData<OidcSessionAuthenticatedData>(this@OIDC)
                requireNotNull(oidcSessionData) { "Could not get OIDC data for session" }
                require(oidcSessionData.tokenValidationData.idpIss == iss) { "Invalid issuer for front channel logout" }

                session.run {
                    call.logoutAndDeleteCookie()
                }

                call.respond(HttpStatusCode.OK, "Session cookie cleared.")
            }

            // --- RP-Initiated Logout (User-triggered) ---
            // Terminates local session and returns IdP's end_session_endpoint for full SSO logout.
            // Client should redirect to end_session_url if provided.
            get("logout") {
                log.trace { "OIDC: RP-initiated logout" }

                val postLogoutRedirect = call.request.queryParameters["post_logout_redirect_uri"]

                // Get token from cookie or header
                val token = call.request.cookies[SessionTokenCookieHandler.cookieName]
                    ?: call.request.headers["Authorization"]?.removePrefix("Bearer ")?.trim()

                var idTokenRaw: String? = null
                var endSessionEndpoint: String? = null
                var clientId: String? = null
                var validatedPostLogout: String? = null

                if (token != null) {
                    try {
                        val session = KtorAuthnzManager.tokenHandler.resolveTokenToSession(token)
                        val oidcSessionData = session.getSessionData<OidcSessionAuthenticatedData>(this@OIDC)

                        // Get id_token for logout hint
                        idTokenRaw = oidcSessionData?.idTokenRaw

                        // Resolve OIDC config to get end_session_endpoint
                        val issuer = oidcSessionData?.tokenValidationData?.idpIss
                        if (issuer != null) {
                            // Construct discovery URL from issuer
                            val discoveryUrl = Url("$issuer/.well-known/openid-configuration")
                            val oidcConfig = resolveConfiguration(discoveryUrl)
                            endSessionEndpoint = oidcConfig.endSessionEndpoint
                        }

                        // Get clientId from registered flows (if available in session)
                        session.flows?.firstOrNull { it.method == id }?.config?.let { flowConfig ->
                            try {
                                val config = Json.decodeFromJsonElement<OidcAuthConfiguration>(flowConfig)
                                clientId = config.clientId
                                validatedPostLogout = postLogoutRedirect?.let { uri ->
                                    config.allowedPostLogoutRedirectUrls.takeIf { it.isNotEmpty() }?.let {
                                        config.validateRedirectUrl(uri)?.toString()
                                    }
                                } ?: config.postLogoutRedirectUri?.toString()
                            } catch (e: Exception) {
                                log.debug { "Could not parse OIDC config from session flow: ${e.message}" }
                            }
                        }

                        // Terminate local session
                        session.run {
                            call.logoutAndDeleteCookie()
                        }
                        log.trace { "OIDC: Local session terminated" }
                    } catch (e: Exception) {
                        log.debug { "OIDC logout: Could not resolve session: ${e.message}" }
                        // Still delete the cookie even if session resolution fails
                        SessionTokenCookieHandler.run { call.deleteCookie() }
                    }
                } else {
                    // No token - just clear cookie if present
                    SessionTokenCookieHandler.run { call.deleteCookie() }
                }

                // Build response
                if (endSessionEndpoint == null) {
                    call.respond(HttpStatusCode.OK, mapOf(
                        "status" to "logged_out",
                        "message" to "Local session terminated. No IdP end_session_endpoint available."
                    ))
                    return@get
                }

                val endSessionUrl = URLBuilder(endSessionEndpoint).apply {
                    idTokenRaw?.let { parameters.append("id_token_hint", it) }
                    clientId?.let { parameters.append("client_id", it) }
                    validatedPostLogout?.let { parameters.append("post_logout_redirect_uri", it) }
                }.build()

                log.trace { "OIDC: IdP end_session_endpoint: $endSessionUrl" }

                call.respond(HttpStatusCode.OK, mapOf(
                    "status" to "logged_out",
                    "end_session_url" to endSessionUrl.toString(),
                    "message" to "Local session terminated. Redirect to end_session_url for full SSO logout."
                ))
            }
        }
    }

    // --- Helper & Validation Functions ---

    /**
     * Fetches user information from the UserInfo endpoint using the Access Token.
     */
    suspend fun userInfo(oidcConfig: OpenIdConfiguration, accessToken: String): JsonObject {
        val endpoint = oidcConfig.userinfoEndpoint
            ?: throw IllegalStateException("UserInfo endpoint is not configured in the OpenID provider metadata.")

        val response = http.get(endpoint) {
            bearerAuth(accessToken)
        }

        if (!response.status.isSuccess()) {
            throw IllegalArgumentException("UserInfo request failed: ${response.bodyAsText()}")
        }

        val jsonObject = response.body<JsonObject>()
        return jsonObject
    }

    private suspend fun exchangeCodeForTokens(config: OidcAuthConfiguration, code: String, codeVerifier: String?): TokenResponse {
        val oidcConfig = config.getOpenIdConfiguration()
        val formParameters = Parameters.build {
            append("grant_type", "authorization_code")
            append("code", code)
            append("redirect_uri", config.callbackUri)
            if (codeVerifier != null) append("code_verifier", codeVerifier)
        }
        val response = http.post(oidcConfig.tokenEndpoint) {
            setBody(FormDataContent(formParameters))
            basicAuth(config.clientId, config.clientSecret)
        }
        if (!response.status.isSuccess()) throw IllegalArgumentException("Token exchange failed: ${response.body<String>()}")
        return response.body()
    }

    /**
     * A generic function to validate a JWS token's signature and common claims.
     * It handles fetching the correct key from the JWKS endpoint and verifying the signature.
     *
     * @param oidcConfig The configuration of the OpenID Provider.
     * @param token The JWS token string to validate.
     * @param clientId The expected audience ('aud') of the token.
     * @return The validated JSON payload of the token.
     * @throws IllegalArgumentException if validation fails.
     */
    private suspend fun validateOidcJws(
        oidcConfig: OidcTokenValidationPolicyData,
        token: String,
        clientId: String
    ): JsonObject {
        val decoded = CompactJws.decodeUnverified(token)
        val algorithm = decoded.algorithm
        require(oidcConfig.idTokenSigningAlgorithms.isNotEmpty()) {
            "OpenID Provider metadata did not advertise ID Token signing algorithms"
        }
        require(algorithm.identifier in oidcConfig.idTokenSigningAlgorithms) {
            "JWS algorithm is not advertised by the OpenID Provider"
        }
        val kid = decoded.protectedHeader["kid"]?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("JWS header is missing 'kid' (Key ID).")
        val verifiedPayload = verifyOidcSignatureWithRefresh(token, algorithm) { forceRefresh ->
            resolveOidcVerificationKey(oidcConfig.idpJwksUrl, kid, forceRefresh)
        }
        val payload = Json.parseToJsonElement(verifiedPayload.decodeToString()).jsonObject

        if (payload["iss"]?.jsonPrimitive?.content != oidcConfig.idpIss) {
            throw IllegalArgumentException("Invalid issuer ('iss') in token.")
        }
        validateAudience(payload, clientId)

        val exp = payload["exp"]?.jsonPrimitive?.long ?: 0
        if (Instant.fromEpochSeconds(exp) < Clock.System.now()) {
            throw IllegalArgumentException("Token has expired.")
        }

        return payload
    }

    internal fun validateAudience(payload: JsonObject, clientId: String) {
        val audiences = when (val audience = payload["aud"]) {
            is JsonPrimitive -> listOf(audience.content)
            is JsonArray -> audience.map { it.jsonPrimitive.content }
            else -> emptyList()
        }
        require(clientId in audiences) { "Invalid audience ('aud') in token." }
        val authorizedParty = payload["azp"]?.jsonPrimitive?.contentOrNull
        if (authorizedParty != null) {
            require(authorizedParty == clientId) {
                "Invalid authorized party ('azp') in token."
            }
        }
        if (audiences.size > 1) {
            require(authorizedParty != null) {
                "Invalid or missing authorized party ('azp') in multi-audience token."
            }
        }
    }

    private fun validatedClientId(payload: JsonObject): String? {
        val audiences = when (val audience = payload["aud"]) {
            is JsonPrimitive -> listOf(audience.content)
            is JsonArray -> audience.map { it.jsonPrimitive.content }
            else -> emptyList()
        }
        return when (audiences.size) {
            0 -> null
            1 -> audiences.single()
            else -> payload["azp"]?.jsonPrimitive?.contentOrNull
        }
    }

    internal suspend fun verifyOidcSignatureWithRefresh(
        token: String,
        algorithm: JwsAlgorithm,
        resolveKey: suspend (forceRefresh: Boolean) -> Crypto2Key,
    ): ByteArray {
        return try {
            CompactJws.verify(token, resolveKey(false), setOf(algorithm)).payload
        } catch (cause: CancellationException) {
            throw cause
        } catch (_: IllegalArgumentException) {
            CompactJws.verify(token, resolveKey(true), setOf(algorithm)).payload
        }
    }

    private suspend fun resolveOidcVerificationKey(
        jwksUrl: String,
        kid: String,
        forceRefresh: Boolean = false,
    ): Crypto2Key {
        var keys = jwksCache[jwksUrl]
        if (forceRefresh || keys == null || keys.none { it["kid"]?.jsonPrimitive?.contentOrNull == kid }) {
            val jwks = http.get(jwksUrl).body<JsonObject>()
            keys = jwks["keys"]?.jsonArray?.map { it.jsonObject }
                ?: throw IllegalArgumentException("OIDC JWKS response is missing keys")
            jwksCache[jwksUrl] = keys
        }
        val matches = keys.filter { it["kid"]?.jsonPrimitive?.contentOrNull == kid }
        require(matches.size == 1) { "OIDC JWKS must contain exactly one key for kid: $kid" }
        val jwk = matches.single()
        require(!Jwk.containsPrivateMaterial(jwk)) { "OIDC JWKS verification key must be public" }
        val encoded = EncodedKey.Jwk(
            data = BinaryData(Json.encodeToString(jwk).encodeToByteArray()),
            privateMaterial = false,
        )
        val stored = encoded.toStoredSoftwareKey(KeyId(kid), setOf(KeyUsage.VERIFY))
        return crypto2Runtime.restore(stored)
    }

    private suspend fun validateIdToken(
        oidcConfig: OidcTokenValidationPolicyData,
        token: String,
        clientId: String,
        expectedNonce: String
    ): JsonObject {
        // Perform common signature and claim validation
        val payload = validateOidcJws(oidcConfig, token, clientId)

        // Perform ID Token-specific claim validation
        if (payload["nonce"]?.jsonPrimitive?.content != expectedNonce) {
            throw IllegalArgumentException("Invalid nonce in ID Token.")
        }

        // Optional: Check 'iat' (issued at) to prevent tokens from far in the future
        val iat = payload["iat"]?.jsonPrimitive?.long ?: 0
        if (Instant.fromEpochSeconds(iat) > Clock.System.now().plus(300.seconds)) {
            throw IllegalArgumentException("ID Token 'iat' is in the future.")
        }

        return payload
    }

    private suspend fun validateLogoutToken(
        oidcConfig: OidcTokenValidationPolicyData,
        token: String,
        clientId: String,
    ): JsonObject {
        // Perform common signature and claim validation
        val payload = validateOidcJws(oidcConfig, token, clientId)

        // Perform Logout Token-specific claim validation
        if (payload.containsKey("nonce")) {
            throw IllegalArgumentException("Logout Token must not contain a nonce.")
        }
        if (payload["events"]?.jsonObject?.containsKey("http://schemas.openid.net/event/backchannel-logout") != true) {
            throw IllegalArgumentException("Token is not a back-channel logout event token.")
        }
        if (!payload.containsKey("sub") && !payload.containsKey("sid")) {
            throw IllegalArgumentException("Logout Token must contain 'sub' or 'sid' claim.")
        }

        return payload
    }

    private suspend fun AuthSession.setOidcTokenValidationPolicy(policy: OidcTokenValidationPolicyData) {
        if (sessionData == null) sessionData = HashMap()
        sessionData!![OIDC_TOKEN_VALIDATION_NAMESPACE] = policy
        SessionManager.updateSession(this)
    }

    private fun AuthSession.getOidcTokenValidationPolicy(): OidcTokenValidationPolicyData? =
        sessionData?.get(OIDC_TOKEN_VALIDATION_NAMESPACE) as? OidcTokenValidationPolicyData

    private val base64Url = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)

    private fun generateSecureRandomString(length: Int = 32): String {
        val bytes = CryptoRand.nextBytes(ByteArray(length))
        return base64Url.encode(bytes)
    }

    private fun generatePkceChallenge(codeVerifier: String): String {
        val bytes = codeVerifier.toByteArray(Charsets.US_ASCII)
        val digest = SHA256().digest(bytes)
        return base64Url.encode(digest)
    }
}
