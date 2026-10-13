package id.walt.ktorauthnz.enrollment

import id.walt.ktorauthnz.KtorAuthnzManager
import id.walt.ktorauthnz.accounts.registerAccount
import id.walt.ktorauthnz.exceptions.AccountExistsException
import id.walt.ktorauthnz.exceptions.AuthenticationFailureException
import id.walt.ktorauthnz.exceptions.TooManyAttemptsException
import id.walt.ktorauthnz.methods.EmailPass
import id.walt.ktorauthnz.methods.UserPassBasedAuthMethod
import id.walt.ktorauthnz.methods.authenticationMethodRoutes
import id.walt.ktorauthnz.methods.config.EmailCodeDelivery
import id.walt.ktorauthnz.tenants.authnzTenant
import io.github.smiley4.ktoropenapi.post
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.kotlincrypto.hash.sha2.SHA256
import java.security.MessageDigest
import java.security.SecureRandom
import kotlin.io.encoding.Base64
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

@Serializable
data class SignUpCreated(@SerialName("account_id") val accountId: String)

@Serializable
data class SignUpPending(
    /** Names the sign-up when confirming it with the code sent by email. */
    @SerialName("signup_id") val signUpId: String,
)

@Serializable
data class SignUpConfirmation(@SerialName("signup_id") val signUpId: String, val code: String)

@Serializable
private data class PendingSignUp(val name: String, val passwordData: String, val details: JsonObject, val codeDigest: String)

/**
 * Sign-up with a password, public routes:
 *
 * - `POST signup {<login name>, password, ...}` - the login name is `email` for [EmailPass], `username` for `UserPass`;
 *   other fields are passed on as the account's details (to the `onAccountRegistered` hook, e.g. for a profile)
 *   - without [verifyEmail]: creates the account, 201 `{account_id}`
 *   - with [verifyEmail] (needs `emailCodes` set when installing KtorAuthnz): sends a code to the address and
 *     answers 202 `{signup_id}`; `POST signup/confirm {signup_id, code}` then creates the account, 201 `{account_id}`
 * - a login name that has an account already answers 409; [maxPerHour] sign-ups per login name, then 429
 *
 * The new account logs in with [method] - include it in your flows. Rate limiting sign-ups per client (e.g. per IP)
 * is up to the application.
 */
fun Route.signUp(
    method: UserPassBasedAuthMethod = EmailPass,
    verifyEmail: Boolean = method == EmailPass,
    minimumLength: Int = 8,
    codeLifetime: Duration = 15.minutes,
    maxPerHour: Int = 5,
) = authenticationMethodRoutes {
    require(method.managesPasswords) { "${method.id} passwords are not kept by ktor-authnz" }
    val store = { KtorAuthnzManager.expiringStore }
    val random = SecureRandom()
    fun ApplicationCall.key(vararg parts: String) = listOfNotNull("signup", authnzTenant, *parts).joinToString(":")
    fun digest(value: String) = SHA256().digest(value.encodeToByteArray()).toHexString()

    post("signup", {
        tags("Authentication")
        summary = "Sign up with a password"
        request { body<JsonObject> { description = "{\"${method.usernameName}\": ..., \"password\": ..., and any details of your own}" } }
        response {
            HttpStatusCode.Created to { body<SignUpCreated>() }
            HttpStatusCode.Accepted to { body<SignUpPending>() }
        }
    }) {
        val body = call.receive<JsonObject>()
        fun field(name: String) = (body[name] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull?.trim()
        val name = requireNotNull(field(method.usernameName)?.takeIf { it.isNotEmpty() }) { "Missing ${method.usernameName}" }
        if (method == EmailPass) require("@" in name) { "Invalid email" }
        val password = (body[method.passwordName] as? JsonPrimitive)?.contentOrNull
        require(password != null && password.length >= minimumLength) { "The password needs at least $minimumLength characters" }
        val details = JsonObject(body - method.usernameName - method.passwordName)

        if (store().increment(call.key("requests", name.lowercase()), 1.hours) > maxPerHour) {
            throw TooManyAttemptsException("Too many sign-ups for this ${method.usernameName}; try again later")
        }
        val identifier = method.identifierFor(name)
        if (identifier.resolveIfExists() != null) throw AccountExistsException(identifier.accountIdentifierName)

        if (!verifyEmail) {
            val accountId = registerAccount(details = details) { data(identifier, method.id, method.storedDataFor(password)) }
            call.respond(HttpStatusCode.Created, SignUpCreated(accountId))
            return@post
        }

        val settings = requireNotNull(KtorAuthnzManager.emailCodes) { "Verifying sign-ups needs `emailCodes` set when installing KtorAuthnz" }
        val signUpId = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(ByteArray(24).also { random.nextBytes(it) })
        val code = buildString { repeat(settings.codeLength) { append(random.nextInt(10)) } }
        // The password is kept hashed while the sign-up waits.
        val passwordData = Json.encodeToString(method.storedDataFor(password).transformSavable())
        store().put(call.key(signUpId), Json.encodeToString(PendingSignUp(name, passwordData, details, digest("$signUpId:$code"))), codeLifetime)
        settings.send(EmailCodeDelivery(email = name, accountId = null, code = code, tenant = call.authnzTenant))
        call.respond(HttpStatusCode.Accepted, SignUpPending(signUpId))
    }

    post("signup/confirm", {
        tags("Authentication")
        summary = "Confirm a sign-up with the code sent by email"
        request { body<SignUpConfirmation>() }
        response { HttpStatusCode.Created to { body<SignUpCreated>() } }
    }) {
        val confirmation = call.receive<SignUpConfirmation>()
        val key = call.key(confirmation.signUpId)
        // Wrong codes count against the sign-up: it ends after five.
        call.attemptOnSignUp(key)
        val pending = store().get(key)?.let { Json.decodeFromString<PendingSignUp>(it) }
            ?: throw AuthenticationFailureException("Invalid or expired sign-up")
        val matches = MessageDigest.isEqual(pending.codeDigest.encodeToByteArray(), digest("${confirmation.signUpId}:${confirmation.code.trim()}").encodeToByteArray())
        if (!matches) {
            if (store().increment("$key:failures", codeLifetime) >= 5) store().remove(key)
            throw AuthenticationFailureException("Invalid code")
        }
        if (!store().putIfAbsent("$key:used", "used", codeLifetime)) throw AuthenticationFailureException("Invalid or expired sign-up")
        store().remove(key)

        val identifier = method.identifierFor(pending.name)
        if (identifier.resolveIfExists() != null) throw AccountExistsException(identifier.accountIdentifierName)
        val accountId = registerAccount(details = pending.details) {
            data(identifier, method.id, Json.decodeFromString(pending.passwordData))
        }
        call.respond(HttpStatusCode.Created, SignUpCreated(accountId))
    }
}

private suspend fun ApplicationCall.attemptOnSignUp(key: String) {
    if ((KtorAuthnzManager.expiringStore.get("$key:failures")?.toLongOrNull() ?: 0) >= 5) {
        throw TooManyAttemptsException("Too many wrong codes; sign up again")
    }
}
