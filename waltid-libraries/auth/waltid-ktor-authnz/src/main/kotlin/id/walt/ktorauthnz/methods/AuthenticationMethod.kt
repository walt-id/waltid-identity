package id.walt.ktorauthnz.methods

import id.walt.ktorauthnz.attempts.AttemptLimiter
import id.walt.ktorauthnz.attempts.AttemptLimiter.attemptOnSession
import id.walt.ktorauthnz.attempts.AuthAttemptTracking
import id.walt.ktorauthnz.exceptions.AccountDataNotFoundException
import id.walt.ktorauthnz.exceptions.AuthSessionNotFoundException
import id.walt.ktorauthnz.exceptions.AuthSessionStateException
import id.walt.ktorauthnz.exceptions.TooManyAttemptsException
import id.walt.ktorauthnz.sessions.AuthSessionStatus
import id.walt.ktorauthnz.AuthContext
import id.walt.ktorauthnz.KtorAuthnzManager
import id.walt.ktorauthnz.accounts.identifiers.methods.AccountIdentifier
import id.walt.ktorauthnz.amendmends.AuthMethodFunctionAmendments
import id.walt.ktorauthnz.methods.config.AuthMethodConfiguration
import id.walt.ktorauthnz.methods.storeddata.AuthMethodStoredData
import id.walt.ktorauthnz.sessions.AuthSession
import id.walt.ktorauthnz.sessions.AuthSessionNextStep
import id.walt.ktorauthnz.sessions.SessionManager
import id.walt.ktorauthnz.sessions.SessionTokenCookieHandler
import id.walt.ktorauthnz.utils.HtmlRedirect.htmlBasedRedirect
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.application.DuplicatePluginException
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

@OptIn(ExperimentalSerializationApi::class)
@Serializable
sealed interface MethodInstance {
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val data: AuthMethodStoredData?

    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val config: AuthMethodConfiguration?

    fun authMethod(): AuthenticationMethod
}

abstract class AuthenticationMethod(open val id: String) {

    // Auth

    /** Login routes */
    abstract fun Route.registerAuthenticationRoutes(
        authContext: ApplicationCall.() -> AuthContext,
        functionAmendments: Map<AuthMethodFunctionAmendments, suspend (Any) -> Unit>? = null
    )


    private suspend fun ApplicationCall.handleSessionAuthSuccess(session: AuthSession, authContext: AuthContext, accountId: String?) {
        accountId?.let { session.accountId = it }
        session.progressFlow(this@AuthenticationMethod)
        AttemptLimiter.recordSuccess(this)

        if (session.status.isSuccess()) {
            session.currentlyActiveMethod = null // No longer any method active, authentication is done for this session
            check(session.token != null) { "Session token does not exist after successful authentication?" }

            SessionTokenCookieHandler.run { setCookie(session.token!!) }
        }
    }

    /**
     * Helper function, called when login was successful, will handle the proceeding actions.
     * - progresses the auth flow of the provided auth session (switch to next method or handle auth success)
     * - update session token cookie
     * - respond with updated session information
     * */
    suspend fun ApplicationCall.handleAuthSuccess(session: AuthSession, authContext: AuthContext, accountId: String?) {
        handleSessionAuthSuccess(session, authContext, accountId)

        val revealTokenToClient = authContext.revealTokenToClient

        this.respond(session.toInformation(revealTokenToClient = revealTokenToClient))
    }

    suspend fun ApplicationCall.handleAuthSuccessAndRedirect(
        session: AuthSession,
        authContext: AuthContext,
        accountId: String?,
        redirectUrl: Url
    ) {
        handleSessionAuthSuccess(session, authContext, accountId)

        /*
        this.respondRedirect(redirectUrl)
        ^^^ We cannot redirect like this, as the SameSite=Strict cookie
        would not be served if the navigation chain starts cross site:
         */

        // Custom HTML-based redirect (correctly breaks cross-site navigation chain):
        this.htmlBasedRedirect(redirectUrl)
    }


    /**
     *
     * */
    suspend fun ApplicationCall.handleAuthNextStep(session: AuthSession, nextStepInfo: AuthSessionNextStep, nextStepDescription: String) {
        session.progressStep(this@AuthenticationMethod, nextStepInfo)

        this.respond(session.toInformation(nextStepDescription = nextStepDescription))
    }


    // Registration

    /**
     * Select if this authentication method supports registration.
     * If this method supports registration, either:
     * - authentication and registration has to be a combined step ([authenticationHandlesRegistration] set to true), or
     * - automatic registration routes have to be provided ([registerRegistrationRoutes] implemented)
     */
    open val supportsRegistration: Boolean = false

    /**
     * Is login and registration a combined step (e.g.: most signature-based challenge-response methods)?
     * -> in this case, no separate registration routes ([registerRegistrationRoutes]) are needed.
     */
    open val authenticationHandlesRegistration: Boolean = supportsRegistration

    /**
     * Automatic registration routes (if this method supports automatic registration routes), requires:
     * - [supportsRegistration] does this method support automatic registration (set to true)
     * - [authenticationHandlesRegistration] Login & registration is not a combined step (set to false)
     */
    open fun Route.registerRegistrationRoutes(authContext: ApplicationCall.() -> AuthContext): Unit =
        throw NotImplementedError("Authentication method ${this::class.simpleName} does not offer registration routes. Authentication routes handle registration: $authenticationHandlesRegistration")


    // Data functions
    suspend inline fun <reified V : AuthMethodStoredData> lookupAccountIdentifierStoredData(identifier: AccountIdentifier): V {
        val storedData =
            KtorAuthnzManager.accountStore.lookupStoredDataForAccountIdentifier(identifier, this) ?: throw AccountDataNotFoundException(
                id
            )
        return (storedData as? V) ?: error("${storedData::class.simpleName} is not requested ${V::class.simpleName}")
    }

    suspend inline fun <reified V : AuthMethodStoredData> lookupAccountStoredData(accountId: String): V {
        val storedData =
            KtorAuthnzManager.accountStore.lookupStoredDataForAccount(accountId, this) ?: throw AccountDataNotFoundException(id)
        return (storedData as? V) ?: error("${storedData::class.simpleName} is not requested ${V::class.simpleName}")
    }

    /**
     * The session this call works on: a new one for the first step of an implicit flow (stored once a step succeeds),
     * otherwise the one named in the context - refused if it failed, is complete, or used up its attempts.
     */
    suspend fun ApplicationCall.getAuthSession(authContext: ApplicationCall.() -> AuthContext): AuthSession {
        val currentContext = authContext.invoke(this)
        if (currentContext.implicitSessionGeneration && currentContext.sessionId == null) {
            return SessionManager.openImplicitGlobalSession(currentContext.initialFlow!!, tenant = currentContext.tenant)
        }

        val sessionId = currentContext.sessionId ?: throw AuthSessionStateException("No authentication session id given")
        val session = SessionManager.getSessionById(sessionId)
        when {
            // Another tenant's session is not continued here, and not revealed either.
            session.tenant != null && session.tenant != currentContext.tenant -> throw AuthSessionNotFoundException(sessionId)
            session.status == AuthSessionStatus.FAILURE ->
                throw TooManyAttemptsException("This authentication session failed; start a new one")
            session.status.isSuccess() || session.flows == null ->
                throw AuthSessionStateException("This authentication session is already complete")
        }
        attemptOnSession(session.id)
        return session
    }

    // Relations
    open val relatedAuthMethodStoredData: KClass<out AuthMethodStoredData>? = null
    open val relatedAuthMethodConfiguration: KClass<out AuthMethodConfiguration>? = null
}


/**
 * Registers method routes here and tracks their failed attempts. The tracking goes on this route itself - a grouping
 * child route would show up in the OpenAPI paths - and only counts calls a method marked as an attempt, so other routes
 * here are unaffected.
 */
private fun Route.authenticationMethodRoutes(block: Route.() -> Unit) {
    try {
        install(AuthAttemptTracking)
    } catch (_: DuplicatePluginException) {
        // already tracked: methods registered here before
    }
    block()
}

fun Route.registerAuthenticationMethod(
    method: AuthenticationMethod,
    authContext: ApplicationCall.() -> AuthContext,
    functionAmendments: Map<AuthMethodFunctionAmendments, suspend (Any) -> Unit>? = null
) {
    authenticationMethodRoutes {
        method.apply {
            registerAuthenticationRoutes(authContext, functionAmendments)
        }
    }
}

fun Route.registerAuthenticationMethods(
    methods: List<AuthenticationMethod>,
    authContext: ApplicationCall.() -> AuthContext,
    functionAmendments: Map<AuthenticationMethod, Map<AuthMethodFunctionAmendments, suspend (Any) -> Unit>>? = null
) {
    authenticationMethodRoutes {
        methods.forEach { method ->
            method.apply {
                registerAuthenticationRoutes(authContext, functionAmendments?.get(method))
            }
        }
    }
}
