package id.walt.authkit.methods

import id.walt.authkit.AuthContext
import id.walt.authkit.exceptions.authFailure
import id.walt.authkit.methods.data.AuthMethodStoredData
import id.walt.authkit.sessions.AuthSession
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
        val userDNFormat: String
    ): AuthMethodStoredData

    // Todo: Move to configuration (is not stored data)

    override suspend fun auth(session: AuthSession, credential: UserPasswordCredential) {
        //val identifier = UsernameIdentifier(credential.name)
        //val storedData: UserPassStoredData = lookupStoredData(identifier /*context()*/)

        val ldapServerUrl = "ldap://localhost:3893"
        val userDNFormat = "cn=%s,ou=superheros,dc=glauth,dc=com"
        val username = "hackers"
        val password = "dogood"

        val (hostname, port) = ldapServerUrl.removePrefix("ldap://").split(":")
        val userDN = userDNFormat.format(username)

        var connection: LdapConnection? = null

        try {
            connection = LdapNetworkConnection(hostname, port.toInt())
            connection.bind(userDN, password)
        } catch (e: LdapException) {
            e.printStackTrace()
            authFailure(e.message ?: "LDAP auth failed")
        } finally {
            connection?.unBind()
            connection?.close()
        }
    }

    override fun Route.register(authContext: PipelineContext<Unit, ApplicationCall>.() -> AuthContext) {
        post("ldap") {
            val session = getSession(authContext)

            val credential = call.getUsernamePasswordFromRequest()

            auth(session, credential)
        }
    }

}
