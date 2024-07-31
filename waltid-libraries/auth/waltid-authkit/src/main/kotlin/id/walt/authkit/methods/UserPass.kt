package id.walt.authkit.methods

import id.walt.authkit.AuthContext
import id.walt.authkit.accounts.identifiers.UsernameIdentifier
import id.walt.authkit.exceptions.authCheck
import id.walt.authkit.methods.data.AuthMethodStoredData
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import io.ktor.util.pipeline.*
import kotlinx.serialization.Serializable

object UserPass : UserPassBasedAuthMethod() {

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

    override fun Route.register(context: PipelineContext<Unit, ApplicationCall>.() -> AuthContext) {
        post("userpass") {
            val credential = call.getUsernamePasswordFromRequest()

            auth(credential)
        }
    }

}
