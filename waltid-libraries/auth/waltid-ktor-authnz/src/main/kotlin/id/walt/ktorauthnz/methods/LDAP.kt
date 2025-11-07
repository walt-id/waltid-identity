package id.walt.ktorauthnz.methods

import id.walt.ktorauthnz.AuthContext
import id.walt.ktorauthnz.accounts.identifiers.methods.AccountIdentifier
import id.walt.ktorauthnz.accounts.identifiers.methods.LDAPIdentifier
import id.walt.ktorauthnz.amendmends.AuthMethodFunctionAmendments
import id.walt.ktorauthnz.exceptions.authFailure
import id.walt.ktorauthnz.methods.config.LDAPConfiguration
import id.walt.ktorauthnz.methods.requests.UserPassCredentials
import id.walt.ktorauthnz.sessions.AuthSession
import id.walt.ktorauthnz.sessions.AuthSessionInformation
import io.github.smiley4.ktoropenapi.post
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import org.apache.directory.api.ldap.model.exception.LdapException
import org.apache.directory.ldap.client.api.LdapConnection
import org.apache.directory.ldap.client.api.LdapNetworkConnection

object LDAP : UserPassBasedAuthMethod("ldap") {

    override suspend fun auth(session: AuthSession, credential: UserPasswordCredential, context: ApplicationCall): AccountIdentifier {
        val config = session.lookupFlowMethodConfiguration<LDAPConfiguration>(this)

        val (hostname, port) = config.ldapServerUrl.removePrefix("ldap://").split(":")
        val userDN = config.userDNFormat.format(credential.name)

        var connection: LdapConnection? = null

        val identifier = LDAPIdentifier(config.ldapServerUrl, credential.name)

        try {
            connection = LdapNetworkConnection(hostname, port.toInt())
            connection.bind(userDN, credential.password)

            return identifier
        } catch (e: LdapException) {
            e.printStackTrace()
            authFailure(e.message ?: "LDAP auth failed")
        } finally {
            connection?.unBind()
            connection?.close()
        }
    }

    override fun Route.registerAuthenticationRoutes(
        authContext: ApplicationCall.() -> AuthContext,
        functionAmendments: Map<AuthMethodFunctionAmendments, suspend (Any) -> Unit>?
    ) {
        post("ldap", {
            request { body<UserPassCredentials> { required = true } }
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
