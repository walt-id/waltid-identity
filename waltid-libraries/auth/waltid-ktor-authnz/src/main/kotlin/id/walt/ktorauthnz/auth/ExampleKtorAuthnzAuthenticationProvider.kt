package id.walt.ktorauthnz.auth

import id.walt.ktorauthnz.auth.ExampleKtorAuthnzAuthenticationProvider.ExampleKtorAuthnzConfig
import io.ktor.server.auth.*

/**
 * Mock authentication for development purposes, all requests will appear
 * as logged in with a pre-defined token.
 */
class ExampleKtorAuthnzAuthenticationProvider(val config: ExampleKtorAuthnzConfig) :
    KtorAuthnzAuthenticationProvider(config) {

    override suspend fun onAuthenticate(context: AuthenticationContext) {
        context.principal(name, UserIdPrincipal(config.token))
    }

    /**
     * Config for (development purpose) mocked authentication provider
     * @param token token to always use
     */
    class ExampleKtorAuthnzConfig(name: String? = null, val token: String) : Config(name)
}

/**
 * Installs a mocked ktor-authnz [Authentication] provider.
 */
fun AuthenticationConfig.devKtorAuthnzMocked(
    name: String?,
    token: String,
    configure: ExampleKtorAuthnzConfig.() -> Unit,
) {
    val provider = ExampleKtorAuthnzAuthenticationProvider(ExampleKtorAuthnzConfig(name, token).apply(configure))
    register(provider)
}
