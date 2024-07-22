package id.walt.webwallet.web.plugins

import id.walt.commons.featureflag.FeatureManager
import id.walt.commons.web.modules.AuthenticationServiceModule
import id.walt.webwallet.FeatureCatalog
import id.walt.webwallet.service.OidcLoginService
import id.walt.webwallet.service.WalletServiceManager.oidcConfig
import id.walt.webwallet.web.controllers.*
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


fun Application.configureSecurity() {
    install(Sessions) {
        cookie<LoginTokenSession>("login") {
            // cookie.encoding = CookieEncoding.BASE64_ENCODING

            // cookie.httpOnly = true
            cookie.httpOnly = false // FIXME
            // TODO cookie.secure = true
            cookie.maxAge = 1.days
            cookie.extensions["SameSite"] = "Strict"
            transform(SessionTransportTransformerEncrypt(AuthKeys.encryptionKey, AuthKeys.signKey))
        }
        cookie<OidcTokenSession>("oidc-login") {
            // cookie.encoding = CookieEncoding.BASE64_ENCODING

            // cookie.httpOnly = true
            cookie.httpOnly = false // FIXME
            // TODO cookie.secure = true
            cookie.maxAge = 1.days
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

val walletAuthenticationPluginAmendment: suspend () -> Unit = suspend {
    AuthenticationServiceModule.AuthenticationServiceConfig.apply {
        customAuthentication = {
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