package id.walt.ktorauthnz.methods

import id.walt.ktorauthnz.exceptions.AuthSessionStateException
import id.walt.ktorauthnz.methods.sessiondata.IdentifiedSessionData
import id.walt.ktorauthnz.methods.storeddata.AuthMethodStoredData
import id.walt.ktorauthnz.attempts.AttemptLimiter.attemptOnIdentifier
import id.walt.ktorauthnz.accounts.identifiers.methods.AccountIdentifier
import id.walt.ktorauthnz.sessions.AuthSession
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Authentication method based on an id (username, email) and password
 *
 * Handles passing the auth credential as basic auth header, as JSON document body, or as form post.
 */
abstract class UserPassBasedAuthMethod(
    override val id: String,
    val usernameName: String = DEFAULT_USER_NAME,
    val passwordName: String = DEFAULT_PASSWORD_NAME,
) : AuthenticationMethod(id) {

    companion object {
        const val DEFAULT_USER_NAME = "username"
        const val DEFAULT_PASSWORD_NAME = "password"
    }

    val EXPLANATION_MESSAGE by lazy {
        """Pass authentication credential either as 1) Basic Auth header (`Authorization: Basic <credentials>`) as per RFC 7617; OR 2) JSON document (`{"$usernameName": "<$usernameName>", "$passwordName": "<$passwordName>"}`); OR 3) Form post ($usernameName=<$usernameName>&$passwordName=<$passwordName>)!"""
    }

    /**
     * Handle username and password as:
     * 1. Basic auth header
     * 2. JSON document body
     * 3. Form post
     */
    suspend fun ApplicationCall.getUsernamePasswordFromRequest(): UserPasswordCredential =
        readUsernamePassword(identified = null).also { attemptOnIdentifier(id, it.name) }

    /**
     * Like [getUsernamePasswordFromRequest], for a step of [session]: after an `identify` step the login name is the
     * identified one, so only the password is needed (a different name is refused).
     */
    suspend fun ApplicationCall.getUsernamePasswordFromRequest(session: AuthSession): UserPasswordCredential {
        val identified = session.getSessionData<IdentifiedSessionData>(Identify)?.value
        return readUsernamePassword(identified).also { attemptOnIdentifier(id, it.name) }
    }

    private suspend fun ApplicationCall.readUsernamePassword(identified: String?): UserPasswordCredential {
        val credential = readCredentials(identified != null)
        if (identified == null) return UserPasswordCredential(requireNotNull(credential.first) { "Missing $usernameName. $EXPLANATION_MESSAGE" }, credential.second)
        if (credential.first != null && !credential.first.equals(identified, ignoreCase = true)) {
            throw AuthSessionStateException("This login is for the identified $usernameName")
        }
        return UserPasswordCredential(identified, credential.second)
    }

    private suspend fun ApplicationCall.readCredentials(nameOptional: Boolean): Pair<String?, String> {
        val contentType = request.contentType()
        when {
            // As JSON document
            contentType.match(ContentType.Application.Json) -> {
                val body = receive<JsonObject>()

                val username = body[usernameName] as? JsonPrimitive ?: body[DEFAULT_USER_NAME] as? JsonPrimitive
                val password = body[passwordName] as? JsonPrimitive ?: body[DEFAULT_PASSWORD_NAME] as? JsonPrimitive

                require(username?.isString == true || (nameOptional && username == null)) { "Invalid or missing $usernameName in JSON request. $EXPLANATION_MESSAGE" }
                require(password?.isString == true) { "Invalid or missing $passwordName in JSON request. $EXPLANATION_MESSAGE" }

                return username?.content to password.content
            }
            // As form post
            contentType.match(ContentType.Application.FormUrlEncoded) -> {
                val form = receiveParameters()

                val username = (form[usernameName] ?: form[DEFAULT_USER_NAME]).also {
                    require(it != null || nameOptional) { "Invalid or missing $usernameName in form post request. $EXPLANATION_MESSAGE" }
                }

                val password = requireNotNull(form[passwordName] ?: form[DEFAULT_PASSWORD_NAME]) {
                    "Invalid or missing $passwordName in form post request. $EXPLANATION_MESSAGE"
                }

                return username to password
            }
            // Basic auth (fallback)
            contentType.match(ContentType.Any) -> {
                val basic = requireNotNull(request.basicAuthenticationCredentials()) {
                    "No basic auth credential header found. $EXPLANATION_MESSAGE"
                }
                return basic.name to basic.password
            }

            else -> throw IllegalArgumentException("Invalid content type: $contentType. $EXPLANATION_MESSAGE")
        }
    }

    abstract suspend fun auth(session: AuthSession, credential: UserPasswordCredential, context: ApplicationCall): AccountIdentifier

    /** Whether passwords of this method are kept by ktor-authnz (and so can be changed and reset here). */
    open val managesPasswords: Boolean = false

    /** The account identifier of a login name, for methods that [managesPasswords]. */
    open fun identifierFor(name: String): AccountIdentifier =
        throw UnsupportedOperationException("${this::class.simpleName} does not manage passwords")

    /** Stored data holding [password] (hashed when saved), for methods that [managesPasswords]. */
    open fun storedDataFor(password: String): AuthMethodStoredData =
        throw UnsupportedOperationException("${this::class.simpleName} does not manage passwords")

    /** Checks [password] of the account behind [name]; returns its identifier, or throws an authentication failure. */
    open suspend fun verifyPassword(name: String, password: String): AccountIdentifier =
        throw UnsupportedOperationException("${this::class.simpleName} does not manage passwords")
    open suspend fun register(session: AuthSession, credential: UserPasswordCredential, context: ApplicationCall): AccountIdentifier =
        throw NotImplementedError("Register method is not implemented for this ${this::class.simpleName}")
}
