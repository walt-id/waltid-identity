package id.walt.authkit.methods

import id.walt.authkit.AuthContext
import id.walt.authkit.accounts.identifiers.AccountIdentifier
import id.walt.authkit.accounts.identifiers.EmailIdentifier
import id.walt.authkit.exceptions.authCheck
import id.walt.authkit.methods.data.AuthMethodStoredData
import id.walt.authkit.sessions.AuthSession
import io.github.smiley4.ktorswaggerui.dsl.routing.post
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import io.ktor.util.pipeline.*
import kotlinx.serialization.Serializable

@Serializable
data class EmailPassCredentials(val email: String, val password: String)

object EmailPass : UserPassBasedAuthMethod("email", usernameName = "email") {

    @Serializable
    data class EmailPassStoredData(
        val password: String,
    ) : AuthMethodStoredData

    override suspend fun auth(session: AuthSession, credential: UserPasswordCredential, context: ApplicationCall): AccountIdentifier {
        val identifier = EmailIdentifier(credential.name)

        val storedData: EmailPassStoredData = lookupStoredData(identifier /*context()*/)

        authCheck(credential.password == storedData.password) { "Invalid password" }

       return identifier
    }

    override fun Route.register(authContext: PipelineContext<Unit, ApplicationCall>.() -> AuthContext) {
        post("emailpass", {
            request {
                body<EmailPassCredentials>()
            }
        }) {
            val session = getSession(authContext)

            val credential = call.getUsernamePasswordFromRequest()

            val identifier = auth(session, credential, context)

            context.handleAuthSuccess(session, identifier.resolveToAccountId())
        }
    }

}
