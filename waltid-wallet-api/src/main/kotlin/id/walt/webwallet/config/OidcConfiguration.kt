package id.walt.webwallet.config

import kotlinx.serialization.Serializable

@Serializable
data class OidcConfiguration(
    val publicBaseUrl: String = "http://localhost:7101",
    val providerName: String,
    val oidcRealm: String,
    val oidcJwks: String,
    val oidcScopes: List<String> = listOf("roles"),
    val jwksCache: OidcJwksCacheConfiguration,
    val authorizeUrl: String,
    val accessTokenUrl: String,
    val logoutUrl: String,
    val clientId: String,
    val clientSecret: String,
    val keycloakUserApi: String,
) : WalletConfig() {
    @Serializable
    data class OidcJwksCacheConfiguration(
        val cacheSize: Int,
        val cacheExpirationHours: Int,
        val rateLimit: JwksRateLimit,
    ) {
        @Serializable
        data class JwksRateLimit(val bucketSize: Int, val refillRateMinutes: Int)
    }
}
