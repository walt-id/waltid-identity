package id.walt.webwallet.web.plugins

import id.walt.commons.config.ConfigManager
import id.walt.commons.config.list.WebConfig
import id.walt.commons.featureflag.FeatureManager
import id.walt.commons.web.modules.AuthenticationServiceModule
import id.walt.ktorauthnz.KtorAuthnzManager
import id.walt.ktorauthnz.auth.ktorAuthnz
import id.walt.ktorauthnz.sessions.SessionTokenCookieHandler
import id.walt.ktorauthnz.tokens.jwttoken.JwtTokenHandler
import id.walt.webwallet.FeatureCatalog
import id.walt.webwallet.config.AuthConfig
import id.walt.webwallet.config.KtorAuthnzConfig
import id.walt.webwallet.service.OidcLoginService
import id.walt.webwallet.service.WalletServiceManager.oidcConfig
import id.walt.webwallet.service.account.authnz.AuthenticationService
import id.walt.webwallet.web.controllers.auth.*
import id.walt.webwallet.web.model.EmailAccountRequest
import id.walt.webwallet.web.model.KeycloakAccountRequest
import io.ktor.client.*
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

        else -> throw IllegalStateException("Neither the waltid-ktor-authnz authentication system nor the legacy authentication system were enabled.")
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
        val configSigningKey = config.configuredSigningKey
        val configVerificationKey = config.configuredVerificationKey

        KtorAuthnzManager.tokenHandler = JwtTokenHandler().apply {
            if (configSigningKey != null) {
                signingKey = configSigningKey
            }
            verificationKey = configVerificationKey
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
                        )
                    }
                    urlProvider = { "${oidcConfig.publicBaseUrl}/wallet-api/auth/oidc-session" }
                }


                if (FeatureManager.isFeatureEnabled(FeatureCatalog.oidcAuthenticationFeature)) {
                    jwt("auth-oauth-jwt") {
                        realm = OidcLoginService.oidcRealm
                        verifier(OidcLoginService.jwkProvider)

                        validate { credential -> JWTPrincipal(credential.payload) }
                        challenge { _, _ ->
                            call.respond(HttpStatusCode.Unauthorized, "Token is not valid or has expired")
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
