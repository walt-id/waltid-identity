package id.walt.authkit.methods

import id.walt.authkit.AuthContext
import id.walt.authkit.accounts.identifiers.UsernameIdentifier
import id.walt.authkit.exceptions.authCheck
import id.walt.authkit.methods.data.AuthMethodStoredData
import id.walt.authkit.sessions.AuthSession
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.pipeline.*
import kotlinx.serialization.Serializable

object UserPass : UserPassBasedAuthMethod("userpass") {

    @Serializable
    data class UserPassStoredData(
        val password: String,
    ) : AuthMethodStoredData

    override suspend fun auth(credential: UserPasswordCredential) {
        val identifier = UsernameIdentifier(credential.name)

        val storedData: UserPassStoredData = lookupStoredData(identifier /*context()*/)

        authCheck(credential.password == storedData.password) { "Invalid password" }

        // TODO: Open session
    }

    override fun Route.register(authContext: PipelineContext<Unit, ApplicationCall>.() -> AuthContext) {
        post("userpass") {
            val credential = call.getUsernamePasswordFromRequest()

            auth(credential)
        }
    }

}
