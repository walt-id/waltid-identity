package id.walt.ktorauthnz.enrollment

import id.walt.ktorauthnz.KtorAuthnzManager
import id.walt.ktorauthnz.accounts.identifiers.methods.PasskeyIdentifier
import id.walt.ktorauthnz.attempts.AttemptLimiter.attemptOnIdentifier
import id.walt.ktorauthnz.auth.getAuthenticatedAccount
import id.walt.ktorauthnz.events.AuthnzEvent
import id.walt.ktorauthnz.events.AuthnzEvents
import id.walt.ktorauthnz.exceptions.AccountNotFoundException
import id.walt.ktorauthnz.exceptions.AuthSessionStateException
import id.walt.ktorauthnz.methods.Passkey
import id.walt.ktorauthnz.methods.authenticationMethodRoutes
import io.github.smiley4.ktoropenapi.delete
import io.github.smiley4.ktoropenapi.post
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.JsonObject

/**
 * Passkey registration for the authenticated account - place inside `authenticate { }`:
 *
 * - `POST passkey/register/options`: `PublicKeyCredentialCreationOptions` (WebAuthn JSON) for `navigator.credentials.create()`
 * - `POST passkey/register?name=...`: the created credential (WebAuthn JSON); adds the passkey to the account
 * - `DELETE passkey/{credentialId}`: removes one of the account's passkeys
 *
 * [userName] is what the authenticator shows for the account, e.g. its email address.
 */
fun Route.passkeyEnrollment(
    userName: suspend ApplicationCall.(accountId: String) -> String = { it },
) = authenticationMethodRoutes {
    val accounts = { KtorAuthnzManager.accountStore }

    post("passkey/register/options", {
        tags("Authentication")
        summary = "Passkey registration options"
        response { HttpStatusCode.OK to { body<JsonObject>() } }
    }) {
        val accountId = call.getAuthenticatedAccount()
        val challenge = Passkey.newChallenge("register:$accountId")
        call.respond(Passkey.registrationOptions(challenge, accountId, call.userName(accountId), excludeCredentials = emptyList()))
    }

    post("passkey/register", {
        tags("Authentication")
        summary = "Register a passkey"
        request {
            queryParameter<String>("name") { required = false; description = "A name for the passkey, e.g. \"work laptop\"" }
            body<JsonObject> { description = "The PublicKeyCredential from navigator.credentials.create(), as WebAuthn JSON" }
        }
        response { HttpStatusCode.Created to { } }
    }) {
        val accountId = call.getAuthenticatedAccount()
        call.attemptOnIdentifier("passkey-registration", accountId)
        val (identifier, stored) = Passkey.verifyRegistration(call.receiveText(), accountId, call.request.queryParameters["name"])
        if (identifier.resolveIfExists() != null) throw AuthSessionStateException("This passkey is already registered")

        accounts().addAccountIdentifierToAccount(accountId, identifier)
        accounts().addAccountIdentifierStoredData(identifier, Passkey.id, stored)
        AuthnzEvents.emit(AuthnzEvent.MethodEnrolled(accountId, Passkey.id))
        call.respond(HttpStatusCode.Created)
    }

    delete("passkey/{credentialId}", {
        tags("Authentication")
        summary = "Remove a passkey"
        request { pathParameter<String>("credentialId") }
        response { HttpStatusCode.NoContent to { } }
    }) {
        val accountId = call.getAuthenticatedAccount()
        val identifier = PasskeyIdentifier(call.parameters["credentialId"]!!)
        if (identifier.resolveIfExists() != accountId) throw AccountNotFoundException(identifier.accountIdentifierName)

        accounts().deleteAccountIdentifierStoredData(identifier, Passkey.id)
        accounts().removeAccountIdentifierFromAccount(identifier)
        AuthnzEvents.emit(AuthnzEvent.MethodRemoved(accountId, Passkey.id))
        call.respond(HttpStatusCode.NoContent)
    }
}
