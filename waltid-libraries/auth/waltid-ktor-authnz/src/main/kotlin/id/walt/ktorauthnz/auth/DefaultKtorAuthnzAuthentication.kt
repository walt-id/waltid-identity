package id.walt.ktorauthnz.auth

import id.walt.ktorauthnz.KtorAuthnzManager
import io.klogging.logger
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.response.*

/**
 * A `basic` [Authentication] provider.
 *
 * @see [basic]
 * @property name is the name of the provider, or `null` for a default provider.
 */
class DefaultKtorAuthnzAuthentication internal constructor(
    config: Config,
) : KtorAuthnzAuthenticationProvider(config) {

    val log = logger<DefaultKtorAuthnzAuthentication>()
//    private val challengeFunction: FormAuthChallengeFunction = config.challengeFunction


    suspend fun fail(context: AuthenticationContext, cause: AuthenticationFailedCause) {
        log.debug { "Fail http request auth for: $cause" }
        @Suppress("NAME_SHADOWING")
        context.challenge("ktor-authnz-challenge", cause) { challenge, call ->
            // TODO: better error messages
            call.respond(HttpStatusCode.Unauthorized, "Unauthorized ($cause)")
            if (!challenge.completed && call.response.status() != null) {
                challenge.complete()
            }
        }
    }

    override suspend fun onAuthenticate(context: AuthenticationContext) {
        val call = context.call

        val effectiveToken = call.getEffectiveRequestAuthToken()

        if (effectiveToken == null) {
            log.debug { "Missing authentication token for request" }
            fail(context, AuthenticationFailedCause.NoCredentials)
            return
        }

        val principal = if (KtorAuthnzManager.tokenHandler.validateToken(effectiveToken))
            UserIdPrincipal(effectiveToken)
        else null

        if (principal != null) {
            context.principal(name, principal)
            return
        } else {
            log.debug { "Missing principal (Invalid Credentials) for request" }
            fail(context, AuthenticationFailedCause.InvalidCredentials)
        }
    }

    /**
     * A configuration for the ktor-authnz authentication provider.
     */
    class Config internal constructor(name: String?) : AuthenticationProvider.Config(name) {
        /*internal var challengeFunction: FormAuthChallengeFunction = {
            call.respond(UnauthorizedResponse())
        }*/
    }
}

/**
 * Installs the ktor-authnz [Authentication] provider.
 */
fun AuthenticationConfig.ktorAuthnz(
    name: String? = null,
    configure: DefaultKtorAuthnzAuthentication.Config.() -> Unit,
) {
    val provider = DefaultKtorAuthnzAuthentication(DefaultKtorAuthnzAuthentication.Config(name).apply(configure))
    register(provider)
}
