package id.walt.ktorauthnz.methods

import id.walt.ktorauthnz.exceptions.InvalidCredentialsException
import id.walt.ktorauthnz.AuthContext
import id.walt.ktorauthnz.KtorAuthnzManager
import id.walt.ktorauthnz.accounts.identifiers.methods.AccountIdentifier
import id.walt.ktorauthnz.accounts.identifiers.methods.UsernameIdentifier
import id.walt.ktorauthnz.amendmends.AuthMethodFunctionAmendments
import id.walt.ktorauthnz.exceptions.authCheck
import id.walt.ktorauthnz.methods.requests.UserPassCredentials
import id.walt.ktorauthnz.methods.storeddata.UserPassStoredData
import id.walt.ktorauthnz.security.PasswordHash
import id.walt.ktorauthnz.security.PasswordHashing
import id.walt.ktorauthnz.sessions.AuthSession
import id.walt.ktorauthnz.sessions.AuthSessionInformation
import io.github.smiley4.ktoropenapi.post
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*

object UserPass : UserPassBasedAuthMethod("userpass") {

    override val relatedAuthMethodStoredData = UserPassStoredData::class

    override val managesPasswords = true

    override fun identifierFor(name: String) = UsernameIdentifier(name)

    override fun storedDataFor(password: String) = UserPassStoredData(password = password)

    override suspend fun verifyPassword(name: String, password: String): AccountIdentifier {
        val identifier = UsernameIdentifier(name)

        val storedData: UserPassStoredData = lookupAccountIdentifierStoredData(identifier)

        val passwordHash = PasswordHash.fromString(storedData.passwordHash ?: error("Missing password hash"))
        val check = PasswordHashing.check(password, passwordHash)

        authCheck(check.valid, InvalidCredentialsException())

        if (check.updated) {
            val newData = storedData.copy(passwordHash = check.updatedHash!!.toString())
            KtorAuthnzManager.accountStore.updateAccountIdentifierStoredData(identifier, id, newData)
        }

        return identifier
    }

    override suspend fun auth(session: AuthSession, credential: UserPasswordCredential, context: ApplicationCall): AccountIdentifier =
        verifyPassword(credential.name, credential.password)

    override fun Route.registerAuthenticationRoutes(
        authContext: ApplicationCall.() -> AuthContext,
        functionAmendments: Map<AuthMethodFunctionAmendments, suspend (Any) -> Unit>?
    ) {
        post("userpass", {
            request { body<UserPassCredentials>() }
            response { HttpStatusCode.OK to { body<AuthSessionInformation>() } }
        }) {
            val session = call.getAuthSession(authContext)

            val credential = call.getUsernamePasswordFromRequest()

            val identifier = auth(session, credential, call)

            val authContext = authContext(call)
            call.handleAuthSuccess(session, authContext, identifier.resolveToAccountId())
        }
    }

}
