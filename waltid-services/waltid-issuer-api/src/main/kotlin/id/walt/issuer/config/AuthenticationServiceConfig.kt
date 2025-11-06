package id.walt.issuer.config

import id.walt.commons.config.WaltConfig

data class AuthenticationServiceConfig(
    val name: String = "Keycloak",
    val authorizeUrl: String = "https://keycloak.walt-test.cloud/realms/waltid-keycloak-ktor/protocol/openid-connect/auth",
    val accessTokenUrl: String = "https://keycloak.walt-test.cloud/realms/waltid-keycloak-ktor/protocol/openid-connect/token",
    val clientId: String = "issuer_api",
    val clientSecret: String = "<your-client-secret>",
)  : WaltConfig()