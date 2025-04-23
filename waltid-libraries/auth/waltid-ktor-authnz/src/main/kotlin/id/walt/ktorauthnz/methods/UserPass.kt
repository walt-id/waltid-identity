package id.walt.ktorauthnz.methods

import id.walt.ktorauthnz.AuthContext
import id.walt.ktorauthnz.KtorAuthnzManager
import id.walt.ktorauthnz.accounts.identifiers.methods.AccountIdentifier
import id.walt.ktorauthnz.accounts.identifiers.methods.UsernameIdentifier
import id.walt.ktorauthnz.amendmends.AuthMethodFunctionAmendments
import id.walt.ktorauthnz.exceptions.authCheck
import id.walt.ktorauthnz.methods.data.UserPassStoredData
import id.walt.ktorauthnz.security.PasswordHash
import id.walt.ktorauthnz.security.PasswordHashing
import id.walt.ktorauthnz.sessions.AuthSession
import id.walt.ktorauthnz.sessions.AuthSessionInformation
import io.github.smiley4.ktoropenapi.post
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class UserPassCredentials(val username: String, val password: String)

object UserPass : UserPassBasedAuthMethod("userpass") {

    override val relatedAuthMethodStoredData = UserPassStoredData::class

    override suspend fun auth(session: AuthSession, credential: UserPasswordCredential, context: ApplicationCall): AccountIdentifier {
        val identifier = UsernameIdentifier(credential.name)

        val storedData: UserPassStoredData = lookupAccountIdentifierStoredData(identifier /*context()*/)

        val passwordHash = PasswordHash.fromString(storedData.passwordHash ?: error("Missing password hash"))
        val check = PasswordHashing.check(credential.password, passwordHash)

        authCheck(check.valid) { "Invalid password" }
        if (check.updated) {
            val newData = storedData.copy(passwordHash = check.updatedHash!!.toString())
            KtorAuthnzManager.accountStore.updateAccountIdentifierStoredData(identifier, id, newData)
        }

        return identifier
    }

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

            call.handleAuthSuccess(session, identifier.resolveToAccountId())
        }
    }

}
