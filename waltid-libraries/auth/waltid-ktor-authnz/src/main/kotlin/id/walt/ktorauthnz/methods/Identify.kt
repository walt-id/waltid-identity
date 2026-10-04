package id.walt.ktorauthnz.methods

import id.walt.ktorauthnz.AuthContext
import id.walt.ktorauthnz.KtorAuthnzManager
import id.walt.ktorauthnz.accounts.identifiers.methods.AccountIdentifier
import id.walt.ktorauthnz.accounts.identifiers.methods.EmailIdentifier
import id.walt.ktorauthnz.accounts.identifiers.methods.UsernameIdentifier
import id.walt.ktorauthnz.amendmends.AuthMethodFunctionAmendments
import id.walt.ktorauthnz.attempts.AttemptLimiter.attemptOnIdentifier
import id.walt.ktorauthnz.exceptions.AccountNotFoundException
import id.walt.ktorauthnz.flows.AuthFlow
import id.walt.ktorauthnz.methods.config.IdentifyConfiguration
import id.walt.ktorauthnz.methods.sessiondata.IdentifiedSessionData
import id.walt.ktorauthnz.methods.storeddata.IdentifyStoredData
import id.walt.ktorauthnz.sessions.AuthSession
import id.walt.ktorauthnz.sessions.AuthSessionInformation
import id.walt.ktorauthnz.sessions.AuthSessionStatus
import id.walt.ktorauthnz.sessions.SessionManager
import io.github.smiley4.ktoropenapi.post
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Identifier first: `POST identify {"email": ...}` (or `{"username": ...}`) finds the account and answers the ways it
 * can log in as `next_method` - the flows the account set up, e.g. a password then TOTP, or an email code
 * ([IdentifyConfiguration] says where they come from). The login then continues with one of them, for that account
 * only: a later step that authenticates another account is refused.
 *
 * The identifier is kept in the session ([IdentifiedSessionData]): password steps need only the password, `email-code`
 * sends to the identified address, and OIDC passes it to the identity provider as `login_hint`.
 *
 * A flow starting with `identify` has neither `continue` nor `success`: what follows depends on the account.
 */
object Identify : AuthenticationMethod("identify") {

    override val relatedAuthMethodConfiguration = IdentifyConfiguration::class
    override val relatedAuthMethodStoredData = IdentifyStoredData::class

    /** The identifier of a request body: `email` or `username`. */
    private fun identifierOf(body: JsonObject): Pair<AccountIdentifier, String> {
        fun field(name: String) = (body[name] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
        field("email")?.let { email ->
            require("@" in email) { "Invalid email" }
            return EmailIdentifier(email) to email
        }
        field("username")?.let { return UsernameIdentifier(it) to it }
        throw IllegalArgumentException("Identify with {\"email\": ...} or {\"username\": ...}")
    }

    /** [flows] with each step's settings filled in from [IdentifyConfiguration.methods] where it carries none. */
    internal fun withSettings(flows: Set<AuthFlow>, config: IdentifyConfiguration): Set<AuthFlow> = flows.mapTo(LinkedHashSet()) { flow ->
        flow.copy(
            config = flow.config ?: config.methods[flow.method],
            continueWith = flow.continueWith?.let { withSettings(it, config) },
        )
    }

    /** The flows to offer for [identifier], and the account they are for (null: not bound to an account here). */
    internal suspend fun offeredFlows(identifier: AccountIdentifier, config: IdentifyConfiguration): Pair<Set<AuthFlow>, String?> {
        (identifier as? EmailIdentifier)?.email?.substringAfterLast('@')?.lowercase()?.let { domain ->
            config.domains[domain]?.let { return it to null }
        }
        val accountId = KtorAuthnzManager.accountStore.lookupAccountUuid(identifier)
            ?: return (config.unknown ?: throw AccountNotFoundException(identifier.accountIdentifierName)) to null
        val own = (KtorAuthnzManager.accountStore.lookupStoredDataForAccount(accountId, this) as? IdentifyStoredData)?.flows
        val flows = own ?: config.default ?: throw AccountNotFoundException(identifier.accountIdentifierName)
        return flows to accountId
    }

    override fun Route.registerAuthenticationRoutes(
        authContext: ApplicationCall.() -> AuthContext,
        functionAmendments: Map<AuthMethodFunctionAmendments, suspend (Any) -> Unit>?
    ) {
        post("identify", {
            tags("Authentication")
            summary = "Identify, to learn the ways to log in"
            request { body<JsonObject> { description = "{\"email\": ...} or {\"username\": ...}" } }
            response { HttpStatusCode.OK to { body<AuthSessionInformation>() } }
        }) {
            val session = call.getAuthSession(authContext)
            val config = session.lookupFlowMethodConfiguration<IdentifyConfiguration>(this@Identify)
            val (identifier, entered) = identifierOf(call.receive<JsonObject>())
            call.attemptOnIdentifier(id, entered)

            val (flows, accountId) = offeredFlows(identifier, config)
            identify(session, identifier, entered, withSettings(flows, config), accountId)
            call.respond(session.toInformation(revealTokenToClient = authContext(call).revealTokenToClient))
        }
    }

    private suspend fun identify(session: AuthSession, identifier: AccountIdentifier, entered: String, flows: Set<AuthFlow>, accountId: String?) {
        require(flows.isNotEmpty()) { "No way to log in is set up for this account" }
        session.setStepInformation(this, null)
        session.accountId = accountId
        session.flows = flows
        session.status = AuthSessionStatus.CONTINUE_NEXT_FLOW
        session.sessionData = (session.sessionData ?: HashMap()).apply {
            put(id, IdentifiedSessionData(identifier.identifierName(), entered))
        }
        SessionManager.updateSession(session)
    }
}
