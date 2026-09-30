package id.walt.ktorauthnz.enrollment

import id.walt.ktorauthnz.KtorAuthnzManager
import id.walt.ktorauthnz.attempts.AttemptLimiter.attemptOnIdentifier
import id.walt.ktorauthnz.auth.getAuthenticatedAccount
import id.walt.ktorauthnz.events.AuthnzEvent
import id.walt.ktorauthnz.events.AuthnzEvents
import id.walt.ktorauthnz.exceptions.AuthSessionStateException
import id.walt.ktorauthnz.exceptions.TooManyAttemptsException
import id.walt.ktorauthnz.methods.EmailPass
import id.walt.ktorauthnz.methods.UserPassBasedAuthMethod
import id.walt.ktorauthnz.methods.authenticationMethodRoutes
import id.walt.ktorauthnz.sessions.SessionManager
import io.github.smiley4.ktoropenapi.post
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.kotlincrypto.hash.sha2.SHA256
import java.security.SecureRandom
import kotlin.io.encoding.Base64
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

@Serializable
data class PasswordResetConfirmation(
    val token: String,
    @SerialName("new_password") val newPassword: String,
)

private fun JsonObject.string(name: String) = (this[name] as? JsonPrimitive)?.contentOrNull

private fun requireAcceptable(password: String?, minimumLength: Int): String {
    require(password != null && password.length >= minimumLength) { "The new password needs at least $minimumLength characters" }
    return password
}

/** Sets [password] for [name] and ends every session of the account, so other logins have to use it. */
private suspend fun UserPassBasedAuthMethod.setPassword(name: String, password: String): String {
    val identifier = identifierFor(name)
    val accountId = identifier.resolveToAccountId()
    KtorAuthnzManager.accountStore.updateAccountIdentifierStoredData(identifier, id, storedDataFor(password).transformSavable())
    SessionManager.invalidateAllSessionsForAccount(accountId)
    AuthnzEvents.emit(AuthnzEvent.PasswordChanged(accountId, id))
    return accountId
}

/**
 * `POST password/change` for the authenticated account - place inside `authenticate { }`. The body names the login
 * (e.g. `email`, as for [method]'s login), the `current_password` and the `new_password`. Ends every session of the
 * account, including this one.
 */
fun Route.passwordChange(method: UserPassBasedAuthMethod = EmailPass, minimumLength: Int = 8) = authenticationMethodRoutes {
    require(method.managesPasswords) { "${method.id} passwords are not kept by ktor-authnz" }
    post("password/change", {
        tags("Authentication")
        summary = "Change the password"
        request { body<JsonObject> { description = "{\"${method.usernameName}\": ..., \"current_password\": ..., \"new_password\": ...}" } }
        response { HttpStatusCode.NoContent to { } }
    }) {
        val accountId = call.getAuthenticatedAccount()
        val body = call.receive<JsonObject>()
        val name = requireNotNull(body.string(method.usernameName)) { "Missing ${method.usernameName}" }
        val current = requireNotNull(body.string("current_password")) { "Missing current_password" }
        val new = requireAcceptable(body.string("new_password"), minimumLength)

        call.attemptOnIdentifier("password-change", accountId)
        val identifier = method.verifyPassword(name, current)
        if (identifier.resolveToAccountId() != accountId) throw AuthSessionStateException("${method.usernameName} is not this account's")

        method.setPassword(name, new)
        call.respond(HttpStatusCode.NoContent)
    }
}

/**
 * Password reset for [method], public routes:
 *
 * - `POST password/reset/request {<login name>}`: sends a single-use token through [sendResetToken] (e.g. an email with a
 *   link) if the account exists; always answers 202, and 429 after [maxRequestsPerHour] for one login
 * - `POST password/reset/confirm {token, new_password}`: sets the password and ends every session of the account
 */
fun Route.passwordReset(
    method: UserPassBasedAuthMethod = EmailPass,
    tokenLifetime: Duration = 30.minutes,
    maxRequestsPerHour: Int = 5,
    minimumLength: Int = 8,
    sendResetToken: suspend (loginName: String, token: String) -> Unit,
) = authenticationMethodRoutes {
    require(method.managesPasswords) { "${method.id} passwords are not kept by ktor-authnz" }
    val store = { KtorAuthnzManager.expiringStore }
    fun tokenKey(token: String) = "password-reset:${SHA256().digest(token.toByteArray()).toHexString()}"

    post("password/reset/request", {
        tags("Authentication")
        summary = "Request a password reset"
        request { body<JsonObject> { description = "{\"${method.usernameName}\": ...}" } }
        response { HttpStatusCode.Accepted to { } }
    }) {
        val name = requireNotNull(call.receive<JsonObject>().string(method.usernameName)) { "Missing ${method.usernameName}" }
        val requests = store().increment("password-reset-requests:${method.id}:${name.lowercase()}", 1.hours)
        if (requests > maxRequestsPerHour) throw TooManyAttemptsException("Too many password reset requests; try again later")

        val identifier = method.identifierFor(name)
        if (identifier.resolveIfExists() != null) {
            val token = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(ByteArray(32).also { SecureRandom().nextBytes(it) })
            store().put(tokenKey(token), name, tokenLifetime)
            AuthnzEvents.emit(AuthnzEvent.PasswordResetRequested(method.id, name))
            sendResetToken(name, token)
        }
        call.respond(HttpStatusCode.Accepted)
    }

    post("password/reset/confirm", {
        tags("Authentication")
        summary = "Set a new password with a reset token"
        request { body<PasswordResetConfirmation>() }
        response { HttpStatusCode.NoContent to { } }
    }) {
        val request = call.receive<PasswordResetConfirmation>()
        val new = requireAcceptable(request.newPassword, minimumLength)
        val key = tokenKey(request.token)
        val name = store().get(key)?.takeIf { store().putIfAbsent("$key:used", "used", tokenLifetime) }
            ?: throw AuthSessionStateException("Invalid or expired password reset token")
        store().remove(key)

        method.setPassword(name, new)
        call.respond(HttpStatusCode.NoContent)
    }
}
