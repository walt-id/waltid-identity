package id.walt.ktorauthnz.auth

import io.ktor.server.auth.*

abstract class KtorAuthnzAuthenticationProvider(config: Config) : AuthenticationProvider(config) {

    abstract override suspend fun onAuthenticate(context: AuthenticationContext)

}
