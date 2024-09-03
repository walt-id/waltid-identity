package id.walt.authkit.auth

import id.walt.authkit.AuthKitManager
import id.walt.authkit.sessions.AuthSession
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.util.pipeline.*

/**
 * A `basic` [Authentication] provider.
 *
 * @see [basic]
 * @property name is the name of the provider, or `null` for a default provider.
 */
public class AuthKitAuthenticationProvider internal constructor(
    config: Config
) : AuthenticationProvider(config) {

//    private val challengeFunction: FormAuthChallengeFunction = config.challengeFunction


    fun fail(context: AuthenticationContext, cause: AuthenticationFailedCause) {
        @Suppress("NAME_SHADOWING")
        context.challenge("auth-kit-challenge", cause) { challenge, call ->
            // TODO: better error messages
            call.respond(HttpStatusCode.Unauthorized, "Unauthorized ($cause)")
            if (!challenge.completed && call.response.status() != null) {
                challenge.complete()
            }
        }
    }

    override suspend fun onAuthenticate(context: AuthenticationContext) {
        val call = context.call

        val authKitHeader = call.request.headers.get("authkit-auth")
        val cookie = call.request.cookies.get("authkit-auth")
        val authHeader = call.request.headers[HttpHeaders.Authorization]

        val effectiveToken = authKitHeader ?: cookie ?: authHeader

        if (effectiveToken == null) {
            fail(context, AuthenticationFailedCause.NoCredentials)
            return
        }

        val principal = if (AuthKitManager.tokenStore.validateToken(effectiveToken))
            UserIdPrincipal(effectiveToken)
        else null

        if (principal != null) {
            context.principal(name, principal)
            return
        } else {
            fail(context, AuthenticationFailedCause.InvalidCredentials)
        }
    }

    /**
     * A configuration for the Auth Kit authentication provider.
     */
    public class Config internal constructor(name: String?) : AuthenticationProvider.Config(name) {
        /*internal var challengeFunction: FormAuthChallengeFunction = {
            call.respond(UnauthorizedResponse())
        }*/
    }
}

/**
 * Installs the Auth Kit [Authentication] provider.
 */
public fun AuthenticationConfig.authKit(
    name: String? = null,
    configure: AuthKitAuthenticationProvider.Config.() -> Unit
) {
    val provider = AuthKitAuthenticationProvider(AuthKitAuthenticationProvider.Config(name).apply(configure))
    register(provider)
}

fun PipelineContext<Unit, ApplicationCall>.getAuthenticatedSession(): AuthSession {
    val token = call.principal<UserIdPrincipal>()?.name
    check(token != null) { "No token for authenticated session" }

    return AuthKitManager.tokenStore.resolveTokenToSession(token)
}
