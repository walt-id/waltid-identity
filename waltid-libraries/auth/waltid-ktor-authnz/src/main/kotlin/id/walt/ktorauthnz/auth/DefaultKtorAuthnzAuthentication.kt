package id.walt.ktorauthnz.auth

import id.walt.ktorauthnz.tenants.authnzTenant
import id.walt.ktorauthnz.KtorAuthnzManager
import io.klogging.logger
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.response.*

/**
 * The authenticated caller: its login token, account and session (the session is unknown for foreign JWTs), and the
 * tenant it logged in to, if any.
 */
data class KtorAuthnzPrincipal(
    val token: String,
    val accountId: String,
    val sessionId: String?,
    val tenant: String? = null,
)

/**
 * The ktor-authnz [Authentication] provider: takes the token from the `ktor-authnz-auth` header, a Bearer
 * `Authorization` header, or the session cookie, validates it with the configured token handler, and sets a
 * [KtorAuthnzPrincipal].
 *
 * @see [ktorAuthnz]
 */
class DefaultKtorAuthnzAuthentication internal constructor(
    private val config: Config,
) : KtorAuthnzAuthenticationProvider(config) {

    val log = logger<DefaultKtorAuthnzAuthentication>()

    suspend fun fail(context: AuthenticationContext, cause: AuthenticationFailedCause) {
        log.debug { "Fail http request auth for: $cause" }
        context.challenge("ktor-authnz-challenge", cause) { challenge, call ->
            call.respond(HttpStatusCode.Unauthorized, "Unauthorized ($cause)")
            if (!challenge.completed && call.response.status() != null) {
                challenge.complete()
            }
        }
    }

    override suspend fun onAuthenticate(context: AuthenticationContext) {
        val call = context.call

        val token = call.getEffectiveRequestAuthToken()
        if (token == null) {
            log.debug { "Missing authentication token for request" }
            fail(context, AuthenticationFailedCause.NoCredentials)
            return
        }

        val handler = KtorAuthnzManager.tokenHandler
        val principal = runCatching {
            if (!handler.validateToken(token)) return@runCatching null
            val principal = KtorAuthnzPrincipal(
                token = token,
                accountId = handler.getTokenAccountId(token),
                sessionId = runCatching { handler.getTokenSessionId(token) }.getOrNull(),
                tenant = runCatching { handler.getTokenTenant(token) }.getOrNull(),
            )
            // Under a tenant scope, a login to another tenant (or to none) does not count.
            val tenant = call.authnzTenant
            if (tenant != null && principal.tenant != tenant) {
                log.debug { "Token is for tenant ${principal.tenant}, not $tenant" }
                return@runCatching null
            }
            principal
        }.getOrElse {
            log.debug { "Token rejected: ${it.message}" }
            null
        }?.let { config.validate(call, it) }

        if (principal == null) {
            log.debug { "Missing principal (Invalid Credentials) for request" }
            fail(context, AuthenticationFailedCause.InvalidCredentials)
            return
        }
        context.principal(name, principal)
    }

    /**
     * A configuration for the ktor-authnz authentication provider.
     */
    class Config internal constructor(name: String?) : AuthenticationProvider.Config(name) {
        internal var validate: suspend (io.ktor.server.application.ApplicationCall, KtorAuthnzPrincipal) -> Any? = { _, p -> p }

        /**
         * Checks or enriches an authenticated caller: return the principal to use (the given one, or your own), or
         * null to answer 401. Runs after the token was validated, e.g. to check the account is active or to meter use.
         */
        fun validate(block: suspend io.ktor.server.application.ApplicationCall.(KtorAuthnzPrincipal) -> Any?) {
            validate = { call, principal -> call.block(principal) }
        }
    }
}

/**
 * Installs the ktor-authnz [Authentication] provider.
 */
fun AuthenticationConfig.ktorAuthnz(
    name: String? = null,
    configure: DefaultKtorAuthnzAuthentication.Config.() -> Unit = {},
) {
    val provider = DefaultKtorAuthnzAuthentication(DefaultKtorAuthnzAuthentication.Config(name).apply(configure))
    register(provider)
}
