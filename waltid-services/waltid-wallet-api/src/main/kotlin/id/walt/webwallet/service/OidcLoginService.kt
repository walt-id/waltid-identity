package id.walt.webwallet.service

import com.auth0.jwk.JwkProvider
import com.auth0.jwk.JwkProviderBuilder
import id.walt.webwallet.service.WalletServiceManager.oidcConfig
import java.net.URI
import java.util.concurrent.TimeUnit

object OidcLoginService {
    val jwkProvider: JwkProvider by lazy {
        JwkProviderBuilder(URI(oidcConfig.oidcJwks).toURL())
            .cached(oidcConfig.jwksCache.cacheSize.toLong(), oidcConfig.jwksCache.cacheExpirationHours.toLong(), TimeUnit.HOURS)
            .rateLimited(
                oidcConfig.jwksCache.rateLimit.bucketSize.toLong(),
                oidcConfig.jwksCache.rateLimit.refillRateMinutes.toLong(),
                TimeUnit.MINUTES
            )
            .build()
    }
    val oidcRealm by lazy { oidcConfig.oidcRealm }

}
