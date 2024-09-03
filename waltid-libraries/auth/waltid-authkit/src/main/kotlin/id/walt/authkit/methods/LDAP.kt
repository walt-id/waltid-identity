package id.walt.authkit.methods

import id.walt.authkit.AuthContext
import id.walt.authkit.accounts.identifiers.AccountIdentifier
import id.walt.authkit.accounts.identifiers.RADIUSIdentifier
import id.walt.authkit.exceptions.authFailure
import id.walt.authkit.methods.config.AuthMethodConfiguration
import id.walt.authkit.sessions.AuthSession
import io.github.smiley4.ktorswaggerui.dsl.routing.post
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import io.ktor.util.pipeline.*
import kotlinx.serialization.Serializable
import org.apache.directory.api.ldap.model.exception.LdapException
import org.apache.directory.ldap.client.api.LdapConnection
import org.apache.directory.ldap.client.api.LdapNetworkConnection

object LDAP : UserPassBasedAuthMethod("ldap") {

    @Serializable
    data class LDAPConfiguration(
        val ldapServerUrl: String,
        val userDNFormat: String,
    ) : AuthMethodConfiguration

    override suspend fun auth(session: AuthSession, credential: UserPasswordCredential, context: ApplicationCall): AccountIdentifier {
        val config = session.lookupConfiguration<LDAPConfiguration>(this)

        val (hostname, port) = config.ldapServerUrl.removePrefix("ldap://").split(":")
        val userDN = config.userDNFormat.format(credential.name)

        var connection: LdapConnection? = null

        val identifier = RADIUSIdentifier(config.ldapServerUrl, credential.name)

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

    override fun Route.register(authContext: PipelineContext<Unit, ApplicationCall>.() -> AuthContext) {
        post("ldap", {
            request {
                body<UserPassCredentials>()
            }
        }) {
            val session = getSession(authContext)

            val credential = call.getUsernamePasswordFromRequest()

            val identifier = auth(session, credential, context)

            context.handleAuthSuccess(session, identifier.resolveToAccountId())
        }
    }

}
