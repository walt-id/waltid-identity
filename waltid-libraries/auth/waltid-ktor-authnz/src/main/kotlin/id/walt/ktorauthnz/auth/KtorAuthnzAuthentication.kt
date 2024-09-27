package id.walt.ktorauthnz.auth

import id.walt.ktorauthnz.KtorAuthnzManager
import id.walt.ktorauthnz.sessions.AuthSession
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
public class KtorAuthnzAuthenticationProvider internal constructor(
    config: Config,
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

        val ktorAuthnzHeader = call.request.headers.get("ktor-authnz-auth")
        val cookie = call.request.cookies.get("ktor-authnz-auth")
        val authHeader = call.request.headers[HttpHeaders.Authorization]?.removePrefix("Bearer ")

        val effectiveToken = ktorAuthnzHeader ?: cookie ?: authHeader

        if (effectiveToken == null) {
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
            fail(context, AuthenticationFailedCause.InvalidCredentials)
        }
    }

    /**
     * A configuration for the ktor-authnz authentication provider.
     */
    public class Config internal constructor(name: String?) : AuthenticationProvider.Config(name) {
        /*internal var challengeFunction: FormAuthChallengeFunction = {
            call.respond(UnauthorizedResponse())
        }*/
    }
}

/**
 * Installs the ktor-authnz [Authentication] provider.
 */
public fun AuthenticationConfig.ktorAuthnz(
    name: String? = null,
    configure: KtorAuthnzAuthenticationProvider.Config.() -> Unit,
) {
    val provider = KtorAuthnzAuthenticationProvider(KtorAuthnzAuthenticationProvider.Config(name).apply(configure))
    register(provider)
}

fun PipelineContext<Unit, ApplicationCall>.getAuthToken(): String {
    val token = call.principal<UserIdPrincipal>()?.name
    check(token != null) { "No token for request principal" }

    return token
}

// TODO: switch to @OptIn instead of @Deprecated
@Deprecated("Externally provided JWT token cannot resolve to authenticated session")
suspend fun PipelineContext<Unit, ApplicationCall>.getAuthenticatedSession(): AuthSession {
    val token = getAuthToken()

    return KtorAuthnzManager.tokenHandler.resolveTokenToSession(token)
}

suspend fun PipelineContext<Unit, ApplicationCall>.getAuthenticatedAccount(): String {
    val token = getAuthToken()

    return KtorAuthnzManager.tokenHandler.getTokenAccountId(token)
}
