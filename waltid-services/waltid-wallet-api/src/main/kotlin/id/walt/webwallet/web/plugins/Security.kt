package id.walt.webwallet.web.plugins

import id.walt.commons.featureflag.FeatureManager
import id.walt.commons.web.modules.AuthenticationServiceModule
import id.walt.webwallet.FeatureCatalog
import id.walt.webwallet.service.OidcLoginService
import id.walt.webwallet.service.WalletServiceManager.oidcConfig
import id.walt.webwallet.web.controllers.LoginTokenSession
import id.walt.webwallet.web.controllers.verifyToken
import io.ktor.client.*
import io.ktor.http.*
import io.ktor.http.auth.*
import io.ktor.http.parsing.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.sessions.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal val walletAuthenticationPluginAmendment: suspend () -> Unit = suspend {
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