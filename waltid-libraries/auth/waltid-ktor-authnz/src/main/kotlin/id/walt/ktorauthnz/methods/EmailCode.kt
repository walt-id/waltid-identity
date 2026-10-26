package id.walt.ktorauthnz.methods

import id.walt.ktorauthnz.methods.sessiondata.IdentifiedSessionData
import id.walt.ktorauthnz.AuthContext
import id.walt.ktorauthnz.KtorAuthnzManager
import id.walt.ktorauthnz.accounts.identifiers.methods.EmailIdentifier
import id.walt.ktorauthnz.amendments.AuthMethodFunctionAmendments
import id.walt.ktorauthnz.attempts.AttemptLimiter.attemptOnIdentifier
import id.walt.ktorauthnz.exceptions.AuthSessionStateException
import id.walt.ktorauthnz.exceptions.AuthenticationFailureException
import id.walt.ktorauthnz.exceptions.TooManyAttemptsException
import id.walt.ktorauthnz.methods.config.EmailCodeDelivery
import id.walt.ktorauthnz.methods.config.EmailCodeSettings
import id.walt.ktorauthnz.methods.sessiondata.EmailCodeSessionData
import id.walt.ktorauthnz.sessions.AuthSession
import id.walt.ktorauthnz.sessions.AuthSessionInformation
import id.walt.ktorauthnz.sessions.AuthSessionNextStepCustomData
import id.walt.ktorauthnz.tenants.authnzTenant
import io.github.smiley4.ktoropenapi.post
import io.github.smiley4.ktoropenapi.route
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import org.kotlincrypto.hash.sha2.SHA256
import java.security.MessageDigest
import java.security.SecureRandom
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

/**
 * A one-time code sent by email ([EmailCodeSettings], set with `install(KtorAuthnz) { emailCodes = ... }`):
 *
 * - as a later step, it verifies that the user can read the account's mail: `POST {sessionId}/email-code/send` (no
 *   body) has the application send a code for the account, `POST {sessionId}/email-code {code}` checks it
 * - as the first step, it is a passwordless login: `POST email-code/send {email}` sends a code if an account has that
 *   address (the answer is the same either way), `POST {sessionId}/email-code {code}` logs in. With the `Registration`
 *   function amendment, an unknown address is registered (it gets the [EmailIdentifier]) once its code is entered.
 *
 * A code is valid for one use within its lifetime; wrong codes count as failed attempts of the session.
 */
object EmailCode : AuthenticationMethod("email-code") {

    @Serializable
    data class EmailCodeRequest(val code: String)

    private val random = SecureRandom()

    private val settings: EmailCodeSettings
        get() = KtorAuthnzManager.emailCodes
            ?: throw IllegalStateException("The email-code method needs `emailCodes` set when installing KtorAuthnz")

    private fun newCode(length: Int) = buildString { repeat(length) { append(random.nextInt(10)) } }

    private fun digest(session: AuthSession, code: String) =
        SHA256().digest("${session.id}:$code".encodeToByteArray()).toHexString()

    private suspend fun ApplicationCall.limitSends(key: String) {
        val sent = KtorAuthnzManager.expiringStore.increment(
            listOfNotNull("email-code-sends", authnzTenant, key).joinToString(":"), 1.hours
        )
        if (sent > settings.maxSends) throw TooManyAttemptsException("Too many codes sent; try again later")
    }

    override fun Route.registerAuthenticationRoutes(
        authContext: ApplicationCall.() -> AuthContext,
        functionAmendments: Map<AuthMethodFunctionAmendments, suspend (Any) -> Unit>?
    ) {
        route("email-code", { tags("Authentication") }) {
            post("send", {
                summary = "Send a login code by email"
                description = "As the first step, the body names the address: {\"email\": ...}. As a later step, no body is needed."
                response { HttpStatusCode.OK to { body<AuthSessionInformation>() } }
            }) {
                val settings = settings
                val session = call.getAuthSession(authContext)
                val identifiedEmail = session.getSessionData<IdentifiedSessionData>(Identify)?.takeIf { it.identifierType == "email" }?.value
                val email: String?
                val accountId: String?
                if (session.accountId != null) {
                    email = identifiedEmail
                    accountId = session.accountId
                    call.limitSends("session:${session.id}")
                } else {
                    email = identifiedEmail ?: runCatching { call.receive<JsonObject>() }.getOrNull()
                        ?.let { (it["email"] as? JsonPrimitive)?.contentOrNull?.trim() }
                    require(!email.isNullOrBlank() && "@" in email) { "Missing or invalid email" }
                    call.attemptOnIdentifier(id, email)
                    call.limitSends("address:${email.lowercase()}")
                    accountId = EmailIdentifier(email).resolveIfExists()
                }

                val code = newCode(settings.codeLength)
                session.setSessionData(
                    this@EmailCode,
                    EmailCodeSessionData(digest(session, code), Clock.System.now() + settings.codeLifetime, accountId, email),
                )
                val registers = functionAmendments?.containsKey(AuthMethodFunctionAmendments.Registration) == true
                if (accountId != null || registers) {
                    settings.send(EmailCodeDelivery(email, accountId, code, call.authnzTenant))
                }

                call.handleAuthNextStep(
                    session = session,
                    nextStepInfo = AuthSessionNextStepCustomData(buildJsonObject { put("expires_in", settings.codeLifetime.inWholeSeconds) }),
                    nextStepDescription = "Enter the code sent by email: POST ${session.id}/email-code {\"code\": ...}",
                )
            }

            post({
                summary = "Enter a login code sent by email"
                request { body<EmailCodeRequest>() }
                response { HttpStatusCode.OK to { body<AuthSessionInformation>() } }
            }) {
                val session = call.getAuthSession(authContext)
                val code = call.receive<EmailCodeRequest>().code.trim()
                val pending = session.getSessionData<EmailCodeSessionData>(this@EmailCode)
                    ?: throw AuthSessionStateException("No code was sent for this session; use email-code/send")

                val matches = MessageDigest.isEqual(pending.codeDigest.encodeToByteArray(), digest(session, code).encodeToByteArray())
                if (!matches || Clock.System.now() > pending.expiresAt) throw AuthenticationFailureException("Invalid or expired code")
                // One use, also against two requests racing with the same code.
                if (!KtorAuthnzManager.expiringStore.putIfAbsent("email-code-used:${pending.codeDigest}", "used", settings.codeLifetime)) {
                    throw AuthenticationFailureException("Invalid or expired code")
                }
                session.sessionData?.remove(id)

                val accountId = when {
                    session.accountId != null -> null
                    pending.accountId != null -> pending.accountId
                    else -> {
                        val register = functionAmendments?.get(AuthMethodFunctionAmendments.Registration)
                            ?: throw AuthenticationFailureException("Invalid or expired code")
                        val identifier = EmailIdentifier(requireNotNull(pending.email))
                        register(identifier)
                        identifier.resolveToAccountId()
                    }
                }
                call.handleAuthSuccess(session, authContext(call), accountId)
            }
        }
    }
}
