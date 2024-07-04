package id.walt.issuer.web.plugins

import id.walt.commons.featureflag.FeatureManager
import id.walt.issuer.FeatureCatalog
import io.ktor.client.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*

fun Application.configureExternalAuth() {
        install(Authentication) {
            oauth("external-oauth-server") {
                client = HttpClient()
                providerLookup = {
                    OAuthServerSettings.OAuth2ServerSettings(
                        name = "keycloak",
                        authorizeUrl = "https://keycloak.walt-test.cloud/realms/waltid-keycloak-ktor/protocol/openid-connect/auth",
                        accessTokenUrl = "https://keycloak.walt-test.cloud/realms/waltid-keycloak-ktor/protocol/openid-connect/token",
                        clientId = "issuer_api",
                        clientSecret = "G9istkjAEOqk9WpZGLyEXuTf34eIRPob",
                        accessTokenRequiresBasicAuth = false,
                        requestMethod = HttpMethod.Post,
                    )
                }
                urlProvider = { "http://localhost:7002/callback" }
            }
        }
    }
