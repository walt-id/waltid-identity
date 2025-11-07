package id.walt.ktorauthnz.methods

import id.walt.commons.web.InvalidCredentialsException
import id.walt.ktorauthnz.AuthContext
import id.walt.ktorauthnz.KtorAuthnzManager
import id.walt.ktorauthnz.accounts.identifiers.methods.AccountIdentifier
import id.walt.ktorauthnz.accounts.identifiers.methods.EmailIdentifier
import id.walt.ktorauthnz.amendmends.AuthMethodFunctionAmendments
import id.walt.ktorauthnz.exceptions.authCheck
import id.walt.ktorauthnz.methods.storeddata.EmailPassStoredData
import id.walt.ktorauthnz.security.PasswordHash
import id.walt.ktorauthnz.security.PasswordHashing
import id.walt.ktorauthnz.sessions.AuthSession
import id.walt.ktorauthnz.sessions.AuthSessionInformation
import io.github.smiley4.ktoropenapi.post
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

object EmailPass : UserPassBasedAuthMethod("email", usernameName = "email") {

    @Serializable
    @SerialName("email")
    data class EmailPassMethodInstance(
        override val data: EmailPassStoredData,
    ) : MethodInstance {
        override val config = null

        override fun authMethod() = EmailPass
    }

    override val relatedAuthMethodStoredData = EmailPassStoredData::class

    override suspend fun auth(session: AuthSession, credential: UserPasswordCredential, context: ApplicationCall): AccountIdentifier {
        val identifier = EmailIdentifier(credential.name)

        val storedData: EmailPassStoredData = lookupAccountIdentifierStoredData(identifier /*context()*/)

        val passwordHash = PasswordHash.fromString(storedData.passwordHash ?: error("Missing password hash"))
        val check = PasswordHashing.check(credential.password, passwordHash)

        authCheck(check.valid, InvalidCredentialsException())

        if (check.updated) {
            val newData = storedData.copy(passwordHash = check.updatedHash!!.toString())
            KtorAuthnzManager.accountStore.updateAccountIdentifierStoredData(identifier, id, newData)
        }

        return identifier
    }

    /*override val supportsRegistration = true
    override suspend fun register(session: AuthSession, credential: UserPasswordCredential, context: ApplicationCall): AccountIdentifier {
        val identifier = EmailIdentifier(credential.name)

    }*/


    @Serializable
    data class EmailPassCredentials(val email: String, val password: String)

    override fun Route.registerAuthenticationRoutes(
        authContext: ApplicationCall.() -> AuthContext,
        functionAmendments: Map<AuthMethodFunctionAmendments, suspend (Any) -> Unit>?
    ) {
        post("emailpass", {
            request { body<EmailPassCredentials> { required = true } }
            response {
                HttpStatusCode.OK to {
                    description = "Successful authentication"
                    body<AuthSessionInformation>()
                }
            }
        }) {
            val session = call.getAuthSession(authContext)

            val credential = call.getUsernamePasswordFromRequest()

            val identifier = auth(session, credential, call)

            val authContext = authContext(call)
            call.handleAuthSuccess(session, authContext, identifier.resolveToAccountId())
        }
    }

    /*override fun Route.registerRegistrationRoutes(authContext: ApplicationCall.() -> AuthContext) {
        post("emailpass", {
            request { body<EmailPassCredentials>() }
            response { HttpStatusCode.OK to { body<AuthSessionInformation>() } }
        }) {
            val session = getSession(authContext)

            val credential = call.getUsernamePasswordFromRequest()

            val identifier = register(session, credential, context)

            context.handleAuthSuccess(session, identifier.resolveToAccountId())
        }
    }*/

//    override val authenticationHandlesRegistration: Boolean = false
}
