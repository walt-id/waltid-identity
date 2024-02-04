package id.walt.webwallet.config

data class OidcConfiguration(
    val enableOidcLogin: Boolean,
    val oidcRealm: String,
    val oidcJwks: String,

    val jwksCache: OidcJwksCacheConfiguration,
) : WalletConfig {
    data class OidcJwksCacheConfiguration(
        val cacheSize: Int,
        val cacheExpirationHours: Int,
        val rateLimit: JwksRateLimit
    ) {
        data class JwksRateLimit(
            val bucketSize: Int,
            val refillRateMinutes: Int
        )
    }
}
