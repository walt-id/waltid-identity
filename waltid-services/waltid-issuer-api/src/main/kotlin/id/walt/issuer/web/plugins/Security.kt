package id.walt.issuer.web.plugins

import id.walt.commons.config.ConfigManager
import id.walt.commons.featureflag.FeatureManager
import id.walt.issuer.FeatureCatalog
import id.walt.issuer.config.AuthenticationServiceConfig
import id.walt.issuer.config.OIDCIssuerServiceConfig
import io.ktor.client.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*

fun Application.configureExternalAuth() {
    val authenticationServiceConfig = ConfigManager.getConfig<AuthenticationServiceConfig>()
    val issuerServiceConfig = ConfigManager.getConfig<OIDCIssuerServiceConfig>()

    install(Authentication) {
            oauth("external-oauth-server") {
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
