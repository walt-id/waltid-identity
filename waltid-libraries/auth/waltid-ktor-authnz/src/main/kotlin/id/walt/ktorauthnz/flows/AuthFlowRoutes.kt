package id.walt.ktorauthnz.flows

import id.walt.ktorauthnz.accounts.registerAccount
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import id.walt.ktorauthnz.methods.config.IdentifyConfiguration
import id.walt.ktorauthnz.accounts.identifiers.methods.AccountIdentifier
import id.walt.ktorauthnz.tenants.authnzTenant
import id.walt.ktorauthnz.AuthContext
import id.walt.ktorauthnz.amendments.AuthMethodFunctionAmendments
import id.walt.ktorauthnz.exceptions.AuthSessionStateException
import id.walt.ktorauthnz.methods.AuthMethodManager
import id.walt.ktorauthnz.methods.AuthenticationMethod
import id.walt.ktorauthnz.methods.registerAuthenticationMethod
import id.walt.ktorauthnz.sessions.AuthSessionInformation
import id.walt.ktorauthnz.sessions.SessionManager
import io.github.smiley4.ktoropenapi.post
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * Every method of a flow tree, each once. For an `identify` step, those its configuration names; methods that only
 * accounts' own flows use are not known here - list them in `authFlows(methods = ...)`.
 */
fun AuthFlow.allMethods(): Set<String> {
    val identified = if (method == AuthFlow.IDENTIFY && config != null) {
        val identify = Json.decodeFromJsonElement<IdentifyConfiguration>(config)
        identify.methods.keys + (identify.domains.values.flatten() + identify.default.orEmpty() + identify.unknown.orEmpty()).flatMap { it.allMethods() }
    } else emptySet()
    return setOf(method) + identified + continueWith.orEmpty().flatMap { it.allMethods() }
}

/** Options of [authFlows]. */
class AuthFlowRoutesConfig {
    /** Tenant of a call, in multi-tenant services; sessions are bound to it. Default: the [authnzTenant] scope, if any. */
    var tenant: ApplicationCall.() -> String? = { authnzTenant }

    /** Whether the login token is also returned in the response body, besides the cookie. */
    var revealTokenToClient: Boolean = true

    /** Register `POST start`, which opens a session explicitly before its first step. */
    var explicitStart: Boolean = true

    /** Function amendments per method, e.g. the registration function Web3 needs. */
    var functionAmendments: Map<AuthenticationMethod, Map<AuthMethodFunctionAmendments, suspend (Any) -> Unit>> = emptyMap()

    /**
     * Logs in identities the account store does not know yet, for [methods] that support it (LDAP users, VC
     * holders, Web3 addresses, `email-code` sign-ups): [register] adds an account for the identifier - by default with
     * [registerAccount], so the `onAccountRegistered` hook runs - and the login continues with it. Without this, they
     * are refused.
     */
    fun registerUnknownAccounts(
        vararg methods: AuthenticationMethod,
        register: suspend (AccountIdentifier) -> Unit = { identifier -> registerAccount { identifier(identifier) } },
    ) {
        functionAmendments = functionAmendments + methods.associateWith { method ->
            functionAmendments[method].orEmpty() + (AuthMethodFunctionAmendments.Registration to { identifier: Any ->
                register(identifier as AccountIdentifier)
            })
        }
    }
}

/**
 * Serves every step of [flows] under this route:
 *
 * - `{method}` for each method a flow starts with: opens the session implicitly, with the flow starting there
 * - `{sessionId}/{method}` for each method of any step: continues the session
 * - `POST start` (optional): opens a session explicitly; with several flows, `?flow=<first method>` picks one
 *
 * Paths are the method ids, so a client follows `next_method` of a response to the next URL.
 */
fun Route.authFlows(flows: List<AuthFlow>, configure: AuthFlowRoutesConfig.() -> Unit = {}) {
    require(flows.isNotEmpty()) { "No auth flows to serve" }
    require(flows.map { it.method }.toSet().size == flows.size) {
        "Two auth flows start with the same method (${flows.map { it.method }}); the first step could not tell them apart"
    }
    val allMethods = flows.flatMap { it.allMethods() }.distinct()
    authFlows(
        methods = allMethods.map(AuthMethodManager::getAuthenticationMethodById),
        firstMethods = flows.map { AuthMethodManager.getAuthenticationMethodById(it.method) },
        flowsFor = { flows },
        configure = configure,
    )
}

/**
 * Like [authFlows] with flows chosen per call - e.g. per tenant. [methods] are all methods the flows may use, and
 * [firstMethods] those they may start with; routes are fixed at startup, flows are resolved per call by [flowsFor].
 */
fun Route.authFlows(
    methods: Collection<AuthenticationMethod>,
    firstMethods: Collection<AuthenticationMethod> = methods,
    flowsFor: ApplicationCall.() -> List<AuthFlow>,
    configure: AuthFlowRoutesConfig.() -> Unit = {},
) {
    val config = AuthFlowRoutesConfig().apply(configure)

    fun ApplicationCall.flowStartingWith(method: AuthenticationMethod): AuthFlow =
        flowsFor().firstOrNull { it.method == method.id }
            ?: throw AuthSessionStateException("No auth flow starts with ${method.id}")

    firstMethods.distinct().forEach { method ->
        registerAuthenticationMethod(method, {
            AuthContext(
                tenant = config.tenant(this),
                implicitSessionGeneration = true,
                initialFlow = flowStartingWith(method),
                revealTokenToClient = config.revealTokenToClient,
            )
        }, config.functionAmendments[method])
    }

    // Routes the IdP or a device calls without the session id, for methods that only continue flows.
    methods.distinct().filter { it.hasSessionlessRoutes && it !in firstMethods }.forEach { method ->
        registerAuthenticationMethod(method, {
            AuthContext(tenant = config.tenant(this), revealTokenToClient = config.revealTokenToClient)
        }, config.functionAmendments[method])
    }

    route("{sessionId}") {
        methods.distinct().forEach { method ->
            registerAuthenticationMethod(method, {
                AuthContext(
                    tenant = config.tenant(this),
                    sessionId = parameters["sessionId"],
                    revealTokenToClient = config.revealTokenToClient,
                )
            }, config.functionAmendments[method])
        }
    }

    if (config.explicitStart) {
        post("start", {
            summary = "Start an authentication session"
            description = "Opens a session before its first step; with several flows, `flow` names the first method of the one to use."
            request { queryParameter<String>("flow") { required = false } }
            response { HttpStatusCode.OK to { body<AuthSessionInformation>() } }
        }) {
            val flows = call.flowsFor()
            val requested = call.request.queryParameters["flow"]
            val flow = when {
                requested != null -> flows.firstOrNull { it.method == requested }
                    ?: throw AuthSessionStateException("No auth flow starts with $requested")
                flows.size == 1 -> flows.single()
                else -> throw AuthSessionStateException("Several auth flows: choose one with ?flow=${flows.map { it.method }}")
            }
            val session = SessionManager.openExplicitGlobalSession(flow, tenant = config.tenant(call))
            call.respond(session.toInformation())
        }
    }
}
