package id.walt.ktorauthnz.methods

import id.walt.commons.web.AccountDataNotFoundException
import id.walt.ktorauthnz.AuthContext
import id.walt.ktorauthnz.KtorAuthnzManager
import id.walt.ktorauthnz.accounts.identifiers.methods.AccountIdentifier
import id.walt.ktorauthnz.amendmends.AuthMethodFunctionAmendments
import id.walt.ktorauthnz.methods.config.AuthMethodConfiguration
import id.walt.ktorauthnz.methods.storeddata.AuthMethodStoredData
import id.walt.ktorauthnz.sessions.AuthSession
import id.walt.ktorauthnz.sessions.AuthSessionStatus
import id.walt.ktorauthnz.sessions.SessionManager
import id.walt.ktorauthnz.sessions.SessionTokenCookieHandler
import io.ktor.server.application.*
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

    /**
     * Helper function, called when login was successful, will handle the proceeding actions.
     * - progresses the auth flow of the provided auth session (switch to next method or handle auth success)
     * - update session token cookie
     * - respond with updated session information
     * */
    suspend fun ApplicationCall.handleAuthSuccess(session: AuthSession, accountId: String?) {
        accountId?.let { session.accountId = it }
        session.progressFlow(this@AuthenticationMethod)

        if (session.status == AuthSessionStatus.OK) {
            check(session.token != null) { "Session token does not exist after successful authentication?" }

            SessionTokenCookieHandler.run { setCookie(session.token!!) }
        }

        this.respond(session.toInformation())
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
            KtorAuthnzManager.accountStore.lookupStoredDataForAccount(accountId, this) ?: error("No stored data for method: $id")
        return (storedData as? V) ?: error("${storedData::class.simpleName} is not requested ${V::class.simpleName}")
    }

    private suspend fun sessionForAuthContext(currentContext: AuthContext): AuthSession {
        val session = if (currentContext.implicitSessionGeneration && currentContext.sessionId == null) {
            // Implicit session start
            SessionManager.openImplicitGlobalSession(currentContext.initialFlow!!)
        } else {
            // Session was started explicitly
            KtorAuthnzManager.sessionStore.resolveSessionById(currentContext.sessionId ?: error("No session id"))
        }

        return session
    }

    suspend fun ApplicationCall.getAuthSession(authContext: ApplicationCall.() -> AuthContext): AuthSession =
        sessionForAuthContext(currentContext = authContext.invoke(this))

    // Relations
    open val relatedAuthMethodStoredData: KClass<out AuthMethodStoredData>? = null
    open val relatedAuthMethodConfiguration: KClass<out AuthMethodConfiguration>? = null
}


fun Route.registerAuthenticationMethod(
    method: AuthenticationMethod,
    authContext: ApplicationCall.() -> AuthContext,
    functionAmendments: Map<AuthMethodFunctionAmendments, suspend (Any) -> Unit>? = null
) {
    method.apply {
        registerAuthenticationRoutes(authContext, functionAmendments)
    }
}

fun Route.registerAuthenticationMethods(
    methods: List<AuthenticationMethod>,
    authContext: ApplicationCall.() -> AuthContext,
    functionAmendments: Map<AuthenticationMethod, Map<AuthMethodFunctionAmendments, suspend (Any) -> Unit>>? = null
) {
    methods.forEach { method ->
        method.apply {
            registerAuthenticationRoutes(authContext, functionAmendments?.get(method))
        }
    }
}
