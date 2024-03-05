package id.walt.webwallet.service

import com.auth0.jwk.JwkProviderBuilder
import id.walt.webwallet.config.ConfigManager
import id.walt.webwallet.config.OidcConfiguration
import java.net.URL
import java.util.concurrent.TimeUnit

object OidcLoginService {
    private val oidcConfig = ConfigManager.getConfig<OidcConfiguration>()
    val jwkProvider: JwkProvider = JwkProviderBuilder(URL(oidcConfig.oidcJwks))
        .cached(oidcConfig.jwksCache.cacheSize.toLong(), oidcConfig.jwksCache.cacheExpirationHours.toLong(), TimeUnit.HOURS)
        .rateLimited(oidcConfig.jwksCache.rateLimit.bucketSize.toLong(), oidcConfig.jwksCache.rateLimit.refillRateMinutes.toLong(), TimeUnit.MINUTES)
        .build()
    val oidcRealm = oidcConfig.oidcRealm

}
