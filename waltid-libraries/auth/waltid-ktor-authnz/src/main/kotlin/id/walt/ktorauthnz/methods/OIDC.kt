package id.walt.ktorauthnz.methods

import id.walt.crypto.keys.jwk.JwkKeyProvider
import id.walt.crypto.utils.JwsUtils.decodeJws
import id.walt.ktorauthnz.AuthContext
import id.walt.ktorauthnz.accounts.identifiers.methods.OIDCIdentifier
import id.walt.ktorauthnz.amendmends.AuthMethodFunctionAmendments
import id.walt.ktorauthnz.methods.config.OidcAuthConfiguration
import id.walt.ktorauthnz.methods.sessiondata.OidcSessionAuthenticatedData
import id.walt.ktorauthnz.methods.sessiondata.OidcSessionAuthenticatedData.TokenValidationData
import id.walt.ktorauthnz.methods.sessiondata.OidcSessionAuthenticationStepData
import id.walt.ktorauthnz.sessions.AuthSession
import id.walt.ktorauthnz.sessions.AuthSessionNextStepRedirectData
import id.walt.ktorauthnz.sessions.SessionManager
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
import org.kotlincrypto.hash.sha2.SHA256
import org.kotlincrypto.random.CryptoRand
import kotlin.io.encoding.Base64
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
object OIDC : AuthenticationMethod("oidc") {

    private val log = logger("OIDC")

    private val http = HttpClient {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    internal const val OIDC_SESSION_NAMESPACE = "oidc-session"
    internal const val OIDC_STATE_NAMESPACE = "oidc-state"


    private val configurationCache = mutableMapOf<Url, OpenIdConfiguration>()

    private val keyProviderCache = mutableMapOf<String, JwkKeyProvider>()
    private fun getJwkProvider(jwksUri: String) = keyProviderCache.getOrPut(jwksUri) {
        JwkKeyProvider(jwksUri)
    }

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
     * @return Auth URL to be returned to the user
     */
    suspend fun startOidcSession(createdSession: AuthSession): Url {
        val config = createdSession.lookupFlowMethodConfiguration<OidcAuthConfiguration>(OIDC)
        val oidcConfig = config.getOpenIdConfiguration()

        val state = generateSecureRandomString()
        val nonce = generateSecureRandomString()
        var codeChallenge: String? = null
        var codeVerifier: String? = null

        if (config.pkceEnabled) {
            codeVerifier = generateSecureRandomString(64)
            codeChallenge = generatePkceChallenge(codeVerifier)
        }

        createdSession.setSessionData(OIDC, OidcSessionAuthenticationStepData(state, nonce, codeVerifier))
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
                val authUrl = startOidcSession(session)

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
                val idTokenPayload = validateIdToken(oidcConfig, tokenResponse.idToken, config.clientId, tempSessionData.nonce)

                val userInfo = userInfo(oidcConfig, tokenResponse.accessToken)
                log.trace { "OIDC UserInfo Response: $userInfo" }

                val subject = idTokenPayload["sub"]?.jsonPrimitive?.content
                    ?: throw IllegalStateException("ID Token is missing 'sub' claim.")

                val sid = idTokenPayload["sid"]?.jsonPrimitive?.content ?: throw IllegalStateException("ID Token is missing 'sid' claim.")

                // --- Our logout is successful

                session.dropExternalIdMapping(OIDC_STATE_NAMESPACE, returnedState) // No longer need this
                session.storeExternalIdMapping(OIDC_SESSION_NAMESPACE, sid) // sid

                val identifier = OIDCIdentifier(oidcConfig.issuer, subject)

                session.setSessionData(
                    this@OIDC, OidcSessionAuthenticatedData(
                        tokenValidationData = TokenValidationData(
                            idpJwksUrl = oidcConfig.jwksUri,
                            idpIss = oidcConfig.issuer,
                        ),
                        oidcIdentifier = identifier
                    )
                )

                val accountId = identifier.resolveIfExists()

                /*if (accountId == null) {

                    // TODO: Create account if it does not exist
                    Account("", "")
                    ExampleAccountStore.registerAccount()
                }*/

                val authContext = authContext(call)

                // Redirect if a URL was configured:
                if (config.redirectAfterLogin != null) {
                    call.handleAuthSuccessAndRedirect(
                        session = session,
                        authContext = authContext,
                        accountId = accountId,
                        redirectUrl = config.redirectAfterLogin
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

            post("logout/backchannel") {
                log.trace { "OIDC: Backchannel-logout" }
                val logoutTokenStr = call.receiveParameters()["logout_token"]
                    ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing logout_token")

                // TODO: logout token can also be encrypted

                val jwsParts = logoutTokenStr.decodeJws() // Decode without validation to get clientId
                val clientId = jwsParts.payload["aud"]?.jsonPrimitive?.content
                    ?: return@post call.respond(HttpStatusCode.BadRequest, "Logout token missing 'aud' claim")

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

                validateLogoutToken(sessionOidcConfig, logoutTokenStr, clientId)

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
        oidcConfig: TokenValidationData,
        token: String,
        clientId: String
    ): JsonObject {
        val jwsParts = token.decodeJws()
        val header = jwsParts.header
        val payload = jwsParts.payload

        // 1. Verify Signature
        val kid = header["kid"]?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("JWS header is missing 'kid' (Key ID).")

        val keyProvider = getJwkProvider(oidcConfig.idpJwksUrl)
        val validationKey = keyProvider.getKey(kid).getOrThrow()

        val verificationResult = validationKey.verifyJws(token)
        if (verificationResult.isFailure) {
            throw IllegalArgumentException("JWS signature verification failed: ${verificationResult.exceptionOrNull()?.message}")
        }

        // 2. Validate Common Claims
        if (payload["iss"]?.jsonPrimitive?.content != oidcConfig.idpIss) {
            throw IllegalArgumentException("Invalid issuer ('iss') in token.")
        }
        if (payload["aud"]?.jsonPrimitive?.content != clientId) {
            throw IllegalArgumentException("Invalid audience ('aud') in token.")
        }

        val exp = payload["exp"]?.jsonPrimitive?.long ?: 0
        if (Instant.fromEpochSeconds(exp) < Clock.System.now()) {
            throw IllegalArgumentException("Token has expired.")
        }

        return payload
    }

    private suspend fun validateIdToken(
        oidcConfig: OpenIdConfiguration,
        token: String,
        clientId: String,
        expectedNonce: String
    ): JsonObject {
        // Perform common signature and claim validation
        val payload = validateOidcJws(TokenValidationData(oidcConfig), token, clientId)

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

    private suspend fun validateLogoutToken(oidcConfig: OidcSessionAuthenticatedData, token: String, clientId: String): JsonObject {
        // Perform common signature and claim validation
        val payload = validateOidcJws(oidcConfig.tokenValidationData, token, clientId)

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
