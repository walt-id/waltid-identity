package id.walt.webwallet.web.plugins

import id.walt.commons.config.ConfigManager
import id.walt.commons.config.list.WebConfig
import id.walt.commons.featureflag.FeatureManager
import id.walt.commons.web.modules.AuthenticationServiceModule
import id.walt.ktorauthnz.KtorAuthnzManager
import id.walt.ktorauthnz.auth.ktorAuthnz
import id.walt.ktorauthnz.sessions.InMemorySessionStore
import id.walt.ktorauthnz.sessions.SessionTokenCookieHandler
import id.walt.ktorauthnz.sessions.ValkeySessionStore
import id.walt.ktorauthnz.tokens.jwttoken.JwtTokenHandler
import id.walt.ktorauthnz.tokens.ktorauthnztoken.InMemoryKtorAuthNzTokenStore
import id.walt.ktorauthnz.tokens.ktorauthnztoken.KtorAuthNzTokenHandler
import id.walt.ktorauthnz.tokens.ktorauthnztoken.ValkeyAuthnzTokenStore
import id.walt.oid4vc.util.http
import id.walt.webwallet.FeatureCatalog
import id.walt.webwallet.config.AuthConfig
import id.walt.webwallet.config.KtorAuthnzConfig
import id.walt.webwallet.service.OidcLoginService
import id.walt.webwallet.service.WalletServiceManager.oidcConfig
import id.walt.webwallet.service.account.authnz.AuthenticationService
import id.walt.webwallet.web.controllers.auth.*
import id.walt.webwallet.web.controllers.auth.oidc.oidcLog
import id.walt.webwallet.web.controllers.auth.oidc.oidcLogNoco
import id.walt.webwallet.web.model.EmailAccountRequest
import id.walt.webwallet.web.model.KeycloakAccountRequest
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import io.ktor.http.auth.*
import io.ktor.http.parsing.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.sessions.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds

val KTOR_AUTHNZ_CONFIG_NAME: String? = null

val authConfigNames by lazy {
    when {
        FeatureManager.isFeatureEnabled(FeatureCatalog.ktorAuthnzAuthenticationFeature) -> listOf(KTOR_AUTHNZ_CONFIG_NAME)
        FeatureManager.isFeatureEnabled(FeatureCatalog.legacyAuthenticationFeature) -> listOf(
            "auth-session",
            "auth-bearer",
            "auth-bearer-alternative"
        )

        else -> error("Neither the waltid-ktor-authnz authentication system nor the legacy authentication system were enabled.")
    }.toTypedArray()
}

fun Application.configureSecurity() {
    if (FeatureManager.isFeatureEnabled(FeatureCatalog.legacyAuthenticationFeature)) {
        install(Sessions) {
            val config = ConfigManager.getConfig<AuthConfig>()
            val tokenLifetime: Long = config.tokenLifetime.toLongOrNull() ?: 1
            cookie<LoginTokenSession>("login") {
                // cookie.encoding = CookieEncoding.BASE64_ENCODING

                // cookie.httpOnly = true
                cookie.httpOnly = false // FIXME
                // TODO cookie.secure = true
                cookie.maxAge = tokenLifetime.days
                cookie.extensions["SameSite"] = "Strict"
                transform(SessionTransportTransformerEncrypt(AuthKeys.encryptionKey, AuthKeys.signKey))
            }
            cookie<OidcTokenSession>("oidc-login") {
                // cookie.encoding = CookieEncoding.BASE64_ENCODING

                // cookie.httpOnly = true
                cookie.httpOnly = false // FIXME
                // TODO cookie.secure = true
                cookie.maxAge = tokenLifetime.days
                cookie.extensions["SameSite"] = "Strict"
                transform(SessionTransportTransformerEncrypt(AuthKeys.encryptionKey, AuthKeys.signKey))
            }
        }

        install(RateLimit) {
            register(RateLimitName("login")) {
                rateLimiter(limit = 30, refillPeriod = 60.seconds) // allows 30 requests per minute
                requestWeight { call, key ->
                    val req = call.getLoginRequest()
                    if (req is EmailAccountRequest || req is KeycloakAccountRequest) 1 else 0
                }
                requestKey { call ->
                    when (val req = call.getLoginRequest()) {
                        is EmailAccountRequest -> req.email
                        is KeycloakAccountRequest -> req.username ?: Unit
                        else -> Unit
                    }
                }
            }
        }
    }
}

val walletAuthenticationPluginAmendment: suspend () -> Unit = suspend {

    if (FeatureManager.isFeatureEnabled(FeatureCatalog.ktorAuthnzAuthenticationFeature)) {
        KtorAuthnzManager.accountStore = AuthenticationService().editableAccountStore

        val config = ConfigManager.getConfig<KtorAuthnzConfig>()

        val configuredExpiration = Duration.parse(config.valkeyRetention ?: config.flowConfigs.firstOrNull()?.expiration ?: "1d")

        KtorAuthnzManager.tokenHandler = when (config.tokenType) {
            KtorAuthnzConfig.AuthnzTokens.STORE_IN_MEMORY -> {
                KtorAuthNzTokenHandler().apply { tokenStore = InMemoryKtorAuthNzTokenStore() }
            }
            KtorAuthnzConfig.AuthnzTokens.STORE_VALKEY ->{
                KtorAuthNzTokenHandler().apply { tokenStore = ValkeyAuthnzTokenStore(
                    unixsocket = config.valkeyUnixSocket,
                    host = config.valkeyHost,
                    port = config.valkeyPort,
                    expiration = configuredExpiration,
                    username = config.valkeyAuthUsername,
                    password = config.valkeyAuthPassword,
                ).also { it.tryConnect() } }
            }
            KtorAuthnzConfig.AuthnzTokens.JWT -> {
                JwtTokenHandler().apply {
                    val configSigningKey = config.configuredSigningKey
                    val configVerificationKey = config.configuredVerificationKey

                    if (configSigningKey != null) {
                        signingKey = configSigningKey
                    }
                    verificationKey = configVerificationKey
                }
            }
        }

        KtorAuthnzManager.sessionStore = when {
            config.valkeyUnixSocket != null || config.valkeyHost != null -> {
                ValkeySessionStore(
                    unixsocket = config.valkeyUnixSocket,
                    host = config.valkeyHost,
                    port = config.valkeyPort,
                    username = config.valkeyAuthUsername,
                    password = config.valkeyAuthPassword,
                    expiration = configuredExpiration
                ).also { it.tryConnect() }
            }
            else -> InMemorySessionStore()
        }

        val webConfig = ConfigManager.getConfig<WebConfig>()
        SessionTokenCookieHandler.domain = config.cookieDomain

        // if not in dev mode, add extra cookie security:
        if (!FeatureManager.isFeatureEnabled(FeatureCatalog.devModeFeature)) {
            SessionTokenCookieHandler.secure = config.requireHttps
        }
    }

    AuthenticationServiceModule.AuthenticationServiceConfig.apply {
        customAuthentication = {

            if (FeatureManager.isFeatureEnabled(FeatureCatalog.ktorAuthnzAuthenticationFeature)) {
                ktorAuthnz(KTOR_AUTHNZ_CONFIG_NAME) { }
            }

            if (FeatureManager.isFeatureEnabled(FeatureCatalog.legacyAuthenticationFeature)) {
                oauth("auth-oauth") {
                    client = HttpClient()
                    providerLookup = {
                        OAuthServerSettings.OAuth2ServerSettings(
                            name = oidcConfig.providerName,
                            authorizeUrl = oidcConfig.authorizeUrl,
                            accessTokenUrl = oidcConfig.accessTokenUrl,
                            clientId = oidcConfig.clientId,
                            clientSecret = oidcConfig.clientSecret,
                            accessTokenRequiresBasicAuth = false,
                            requestMethod = HttpMethod.Post,
                            defaultScopes = oidcConfig.oidcScopes
                        ).also {
                            oidcLogNoco.trace {
                                """Provider lookup for auth-oauth =
                                    OAuth2ServerSettings:
                                        name: ${it.name}
                                        authorizeUrl: ${it.authorizeUrl}
                                        accessTokenUrl: ${it.accessTokenUrl}
                                        requestMethod: ${it.requestMethod.value}
                                        clientId: ${it.clientId}
                                        clientSecret: ${it.clientSecret}
                                        defaultScopes: ${it.defaultScopes.joinToString(", ")}
                                        accessTokenRequiresBasicAuth: ${it.accessTokenRequiresBasicAuth}
                                        nonceManager: ${it.nonceManager::class.simpleName}
                                        passParamsInURL: ${it.passParamsInURL}
                                        extraAuthParameters: ${it.extraAuthParameters.joinToString { "${it.first}=${it.second}" }}
                                        extraTokenParameters: ${it.extraTokenParameters.joinToString { "${it.first}=${it.second}" }}
                                """.trimIndent()
                            }
                        }
                    }
                    urlProvider = { "${oidcConfig.publicBaseUrl}/wallet-api/auth/oidc-session" }
                }


                if (FeatureManager.isFeatureEnabled(FeatureCatalog.oidcAuthenticationFeature)) {
                    jwt("auth-oauth-jwt") {
                        realm = OidcLoginService.oidcRealm
                        verifier(OidcLoginService.jwkProvider)

                        validate { credential ->
                            oidcLog.trace { "Validating oauth-jwt: Payload ${credential.payload} (from $credential)" }
                            JWTPrincipal(credential.payload)
                        }
                        challenge { defaultScheme, realm ->
                            // The verification of the JWT was not successful. Either none was provided, or an
                            // opaque token (not a JWT verifiable with the JWKS) was provided. If it is opaque,
                            // we can try to retrieve the id_token through the token endpoint.

                            val query = call.request.queryParameters

                            if (query.contains("code")) { // try to redeem against token endpoint:
                                oidcLog.trace { "Oauth-jwt challenge: Opaque token was received, will try to redeem - Scheme $defaultScheme, Realm $realm" }
                                val code = call.request.queryParameters["code"]
                                //val state = call.request.queryParameters["state"] // State is not required for this endpoint
                                //val sessionState = call.request.queryParameters["session_state"] // Entra ID specific

                                val parameters = ParametersBuilder().apply {
                                    append("client_id", oidcConfig.clientId)
                                    append("client_secret", oidcConfig.clientSecret)
                                    append("scope", oidcConfig.oidcScopes.joinToString(" "))
                                    append("code", code!!)
                                    append("redirect_uri", "${oidcConfig.publicBaseUrl}/wallet-api/auth/oidc-session")
                                    append("grant_type", "authorization_code")
                                }.build()
                                val resp = http.submitForm(oidcConfig.accessTokenUrl, parameters)

                                val idToken = resp.body<JsonObject>()["id_token"]?.jsonPrimitive?.content ?: throw IllegalArgumentException("OIDC Login failed: Missing id_token from token endpoint response")

                                call.sessions.set(OidcTokenSession(idToken))
                                call.respondRedirect("/login?oidc_login=true")
                            } else {
                                oidcLog.trace { "Oauth-jwt challenge: Invalid token - Scheme $defaultScheme, Realm $realm" }
                                call.respond(HttpStatusCode.Unauthorized, "Token is not valid or has expired")
                            }
                        }
                    }
                }

                bearer("auth-bearer") {
                    authenticate { tokenCredential ->
                        val verificationResult = verifyToken(tokenCredential.token)
                        verificationResult.getOrNull()?.let { UserIdPrincipal(it) }
                    }
                }

                bearer("auth-bearer-alternative") {
                    authHeader { call ->
                        call.request.header("waltid-authorization")?.let {
                            try {
                                parseAuthorizationHeader(it)
                            } catch (cause: ParseException) {
                                throw BadRequestException("Invalid auth header", cause)
                            }
                        }
                    }
                    authenticate { tokenCredential ->
                        val verificationResult = verifyToken(tokenCredential.token)
                        verificationResult.getOrNull()?.let { UserIdPrincipal(it) }
                    }
                }

                session<LoginTokenSession>("auth-session") {
                    validate { session ->
                        val verificationResult = verifyToken(session.token)
                        val userId = verificationResult.getOrNull()

                        if (userId != null) {
                            UserIdPrincipal(userId)
                        } else {
                            sessions.clear("login")
                            null
                        }
                    }

                    challenge {
                        call.respond(
                            HttpStatusCode.Unauthorized,
                            JsonObject(mapOf("message" to JsonPrimitive("Login Required")))
                        )
                    }
                }
            }
        }
    }
}
