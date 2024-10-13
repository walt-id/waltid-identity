package id.walt.ktorauthnz.methods

import id.walt.ktorauthnz.AuthContext
import id.walt.ktorauthnz.accounts.identifiers.methods.AccountIdentifier
import id.walt.ktorauthnz.accounts.identifiers.methods.UsernameIdentifier
import id.walt.ktorauthnz.exceptions.authCheck
import id.walt.ktorauthnz.methods.data.UserPassStoredData
import id.walt.ktorauthnz.sessions.AuthSession
import id.walt.ktorauthnz.sessions.AuthSessionInformation
import io.github.smiley4.ktorswaggerui.dsl.routing.post
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import io.ktor.util.pipeline.*
import kotlinx.serialization.Serializable

@Serializable
data class UserPassCredentials(val username: String, val password: String)

object UserPass : UserPassBasedAuthMethod("userpass") {

    override val relatedAuthMethodStoredData = UserPassStoredData::class

    override suspend fun auth(session: AuthSession, credential: UserPasswordCredential, context: ApplicationCall): AccountIdentifier {
        val identifier = UsernameIdentifier(credential.name)

        val storedData: UserPassStoredData = lookupStoredData(identifier /*context()*/)

        authCheck(credential.password == storedData.password) { "Invalid password" }

        return identifier
    }

    override fun Route.register(authContext: PipelineContext<Unit, ApplicationCall>.() -> AuthContext) {
        post("userpass", {
            request { body<UserPassCredentials>() }
            response { HttpStatusCode.OK to { body<AuthSessionInformation>() } }
        }) {
            val session = getSession(authContext)

            val credential = call.getUsernamePasswordFromRequest()

            val identifier = auth(session, credential, context)

            context.handleAuthSuccess(session, identifier.resolveToAccountId())
        }
    }

}
