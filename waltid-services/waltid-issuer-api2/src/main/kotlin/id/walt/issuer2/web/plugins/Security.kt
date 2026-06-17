package id.walt.issuer2.web.plugins

import id.walt.commons.config.ConfigManager
import id.walt.commons.web.modules.AuthenticationServiceModule
import id.walt.issuer2.config.AuthenticationServiceConfig
import id.walt.issuer2.config.Issuer2ServiceConfig
import io.ktor.client.*
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.ApplicationRequest
import io.ktor.server.request.path
import io.klogging.noCoLogger

val issuer2AuthenticationPluginAmendment: suspend () -> Unit = suspend {
    val authenticationServiceConfig = ConfigManager.getConfig<AuthenticationServiceConfig>()
    val issuerServiceConfig = ConfigManager.getConfig<Issuer2ServiceConfig>()

    AuthenticationServiceModule.AuthenticationServiceConfig.apply {
        customAuthentication = {
            oauth("auth-oauth") {
                client = HttpClient()
                providerLookup = {
                    OAuthServerSettings.OAuth2ServerSettings(
                        name = authenticationServiceConfig.name,
                        authorizeUrl = authenticationServiceConfig.authorizeUrl,
                        accessTokenUrl = authenticationServiceConfig.accessTokenUrl,
                        clientId = authenticationServiceConfig.clientId,
                        clientSecret = authenticationServiceConfig.clientSecret,
                        defaultScopes = authenticationServiceConfig.defaultScopes,
                        requestMethod = HttpMethod.Post,
                        authorizeUrlInterceptor = { request ->
                            if (authenticationServiceConfig.forwardIssuerStateToAuthorizationServer) {
                                appendForwardedIssuerState(request)
                            }
                        },
                    )
                }
                urlProvider = { "${issuerServiceConfig.baseUrl.trimEnd('/')}/openid4vci/external/oauth/callback" }
            }
        }
    }
}

private const val FORWARDED_ISSUER_STATE_PARAMETER = "issuer_state"
private const val EXTERNAL_LOGIN_PATH_SEGMENT = "/external_login/"
private val logger = noCoLogger("ExternalLoginProvider")

private fun URLBuilder.appendForwardedIssuerState(request: ApplicationRequest) {
    if (parameters[FORWARDED_ISSUER_STATE_PARAMETER] != null) {
        logger.trace("Skipping forwarded issuer state append: parameter already present")
        return
    }

    val requestPath = request.path()
    val externalLoginPathIndex = requestPath.indexOf(EXTERNAL_LOGIN_PATH_SEGMENT)
    if (externalLoginPathIndex == -1) {
        logger.trace("Skipping forwarded issuer state append: external login path segment not found")
        return
    }

    val internalAuthReqStartIndex = externalLoginPathIndex + EXTERNAL_LOGIN_PATH_SEGMENT.length
    val internalAuthReq = requestPath.substring(internalAuthReqStartIndex)
    if (internalAuthReq.isBlank()) {
        logger.debug("Skipping forwarded issuer state append: internal auth request segment is blank")
        return
    }

    val issuerStateValues = try {
        parseQueryString(internalAuthReq).getAll(FORWARDED_ISSUER_STATE_PARAMETER)
    } catch (e: Exception) {
        logger.error("Failed to extract forwarded issuer state from internal auth request", e)
        return
    }

    if (issuerStateValues == null) {
        logger.trace("Skipping forwarded issuer state append: parameter not found")
        return
    }

    if (issuerStateValues.size != 1) {
        logger.warn("Skipping forwarded issuer state append: expected exactly 1 value, got ${issuerStateValues.size}")
        return
    }

    val issuerState = issuerStateValues.single()
    if (issuerState.isBlank()) {
        logger.warn("Skipping forwarded issuer state append: value is blank")
        return
    }

    try {
        parameters.append(FORWARDED_ISSUER_STATE_PARAMETER, issuerState)
        logger.info("Appended forwarded issuer state parameter")
    } catch (e: Exception) {
        logger.error("Failed to append forwarded issuer state parameter", e)
    }
}