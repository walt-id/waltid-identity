package id.walt.issuer2.config

import id.walt.commons.config.WaltConfig

data class AuthenticationServiceConfig(
    val name: String = "keycloak",
    val authorizeUrl: String = "https://keycloak.demo.walt.id/realms/mynewrealm/protocol/openid-connect/auth",
    val accessTokenUrl: String = "https://keycloak.demo.walt.id/realms/mynewrealm/protocol/openid-connect/token",
    val clientId: String = "issuer_api",
    val clientSecret: String = "",
    val defaultScopes: List<String> = listOf("openid", "profile"),
    val forwardIssuerStateToAuthorizationServer: Boolean = false,
) : WaltConfig() {
    override fun toString(): String =
        "AuthenticationServiceConfig(name=$name, authorizeUrl=$authorizeUrl, accessTokenUrl=$accessTokenUrl, " +
            "clientId=$clientId, clientSecret=[REDACTED], defaultScopes=$defaultScopes, " +
            "forwardIssuerStateToAuthorizationServer=$forwardIssuerStateToAuthorizationServer)"
}
