package id.walt.ktorauthnz.methods

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
    internal suspend fun ApplicationCall.getUsernamePasswordFromRequest(): UserPasswordCredential {
        val contentType = request.contentType()
        when {
            // As JSON document
            contentType.match(ContentType.Application.Json) -> {
                val body = receive<JsonObject>()

                val username = body[usernameName] as? JsonPrimitive ?: body[DEFAULT_USER_NAME] as? JsonPrimitive
                val password = body[passwordName] as? JsonPrimitive ?: body[DEFAULT_PASSWORD_NAME] as? JsonPrimitive

                check(username?.isString == true) { "Invalid or missing $usernameName in JSON request. $EXPLANATION_MESSAGE" }
                check(password?.isString == true) { "Invalid or missing $passwordName in JSON request. $EXPLANATION_MESSAGE" }

                return UserPasswordCredential(username.content, password.content)
            }
            // As form post
            contentType.match(ContentType.Application.FormUrlEncoded) -> {
                val form = receiveParameters()

                val username = form[usernameName] ?: form[DEFAULT_USER_NAME]
                ?: error("Invalid or missing $usernameName in form post request. $EXPLANATION_MESSAGE")

                val password = form[passwordName] ?: form[DEFAULT_PASSWORD_NAME]
                ?: error("Invalid or missing $passwordName in form post request. $EXPLANATION_MESSAGE")

                return UserPasswordCredential(username, password)
            }
            // Basic auth (fallback)
            contentType.match(ContentType.Any) -> {
                val basicAuth = request.basicAuthenticationCredentials()
                if (basicAuth != null)
                    return basicAuth
                else error("No basic auth credential header found. $EXPLANATION_MESSAGE")
            }

            else -> error("Invalid content type: $contentType. $EXPLANATION_MESSAGE")
        }
    }

    abstract suspend fun auth(session: AuthSession, credential: UserPasswordCredential, context: ApplicationCall): AccountIdentifier
    open suspend fun register(session: AuthSession, credential: UserPasswordCredential, context: ApplicationCall): AccountIdentifier =
        throw NotImplementedError("Register method is not implemented for this ${this::class.simpleName}")
}
