package id.walt.issuer.web.plugins

import id.walt.commons.config.ConfigManager
import id.walt.commons.web.modules.AuthenticationServiceModule
import id.walt.issuer.config.AuthenticationServiceConfig
import id.walt.issuer.config.OIDCIssuerServiceConfig
import io.ktor.client.*
import io.ktor.http.*
import io.ktor.server.auth.*

val issuerAuthenticationPluginAmendment: suspend () -> Unit = suspend {
    val authenticationServiceConfig = ConfigManager.getConfig<AuthenticationServiceConfig>()
    val issuerServiceConfig = ConfigManager.getConfig<OIDCIssuerServiceConfig>()

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
                        requestMethod = HttpMethod.Post,
                    )
                }
                urlProvider = { "${issuerServiceConfig.baseUrl}/callback" }
            }
        }
    }
}
