package id.walt.webwallet.service

import com.auth0.jwk.JwkProviderBuilder
import java.net.URL
import java.util.concurrent.TimeUnit

object OidcLoginService {

    val jwkProvider = JwkProviderBuilder(URL("http://localhost:8080/realms/waltid-keycloak-ktor/protocol/openid-connect/certs"))
        .cached(10, 24, TimeUnit.HOURS)
        .rateLimited(10, 1, TimeUnit.MINUTES)
        .build()
    val oidcRealm = "http://localhost:8080/realms/waltid-keycloak-ktor"

}
