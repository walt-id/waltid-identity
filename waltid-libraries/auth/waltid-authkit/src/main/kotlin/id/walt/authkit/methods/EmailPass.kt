package id.walt.authkit.methods

import id.walt.authkit.AuthContext
import id.walt.authkit.accounts.identifiers.EmailIdentifier
import id.walt.authkit.exceptions.authCheck
import id.walt.authkit.methods.data.AuthMethodStoredData
import id.walt.authkit.sessions.AuthSession
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.pipeline.*
import kotlinx.serialization.Serializable

object EmailPass : UserPassBasedAuthMethod("email", usernameName = "email") {

    @Serializable
    data class EmailPassStoredData(
        val password: String,
    ) : AuthMethodStoredData

    override suspend fun auth(session: AuthSession, credential: UserPasswordCredential) {
        val identifier = EmailIdentifier(credential.name)

        val storedData: EmailPassStoredData = lookupStoredData(identifier /*context()*/)

        authCheck(credential.password == storedData.password) { "Invalid password" }

        session.accountId = identifier.resolveToAccountId()
        session.progressFlow(this@EmailPass)
        // TODO: Open session
    }

    override fun Route.register(authContext: PipelineContext<Unit, ApplicationCall>.() -> AuthContext) {
        post("emailpass") {
            val session = getSession(authContext)

            val credential = call.getUsernamePasswordFromRequest()

            auth(session, credential)

            context.respond(session.toInformation())
        }
    }

}
