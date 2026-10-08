package id.walt.ktorauthnz.methods

import id.walt.ktorauthnz.AuthContext
import id.walt.ktorauthnz.accounts.identifiers.methods.AccountIdentifier
import id.walt.ktorauthnz.accounts.identifiers.methods.LDAPIdentifier
import id.walt.ktorauthnz.amendments.AuthMethodFunctionAmendments
import id.walt.ktorauthnz.exceptions.AccountNotFoundException
import id.walt.ktorauthnz.exceptions.authFailure
import id.walt.ktorauthnz.methods.config.LDAPConfiguration
import id.walt.ktorauthnz.methods.requests.UserPassCredentials
import id.walt.ktorauthnz.sessions.AuthSession
import id.walt.ktorauthnz.sessions.AuthSessionInformation
import io.klogging.logger
import io.github.smiley4.ktoropenapi.post
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import id.walt.errors.StatusException
import org.apache.directory.api.ldap.model.exception.LdapAuthenticationException
import org.apache.directory.api.ldap.model.exception.LdapException
import org.apache.directory.api.ldap.model.exception.LdapNoSuchObjectException
import org.apache.directory.api.ldap.model.name.Rdn
import org.apache.directory.ldap.client.api.LdapConnectionConfig
import org.apache.directory.ldap.client.api.LdapNetworkConnection
import java.net.URI

/**
 * Login with the user's LDAP password: binds as the user's DN ([LDAPConfiguration.userDNFormat]). Users the account
 * store does not know yet log in only with the `Registration` function amendment, which gets their [LDAPIdentifier].
 */
object LDAP : UserPassBasedAuthMethod("ldap") {

    private val log = logger("LDAP")

    /** Where to connect: host, port, and whether TLS starts with the connection. */
    internal fun connectionConfig(config: LDAPConfiguration) = LdapConnectionConfig().apply {
        val url = URI(config.ldapServerUrl)
        val ssl = when (url.scheme?.lowercase()) {
            "ldap" -> false
            "ldaps" -> true
            else -> throw IllegalArgumentException("LDAP server URL must start with ldap:// or ldaps://: ${config.ldapServerUrl}")
        }
        ldapHost = requireNotNull(url.host) { "LDAP server URL has no host: ${config.ldapServerUrl}" }
        ldapPort = if (url.port != -1) url.port else if (ssl) 636 else 389
        isUseSsl = ssl
        isUseTls = config.startTls
    }

    /** The DN [name] binds as: special characters are escaped, so a name cannot add or change DN components. */
    internal fun userDn(config: LDAPConfiguration, name: String): String = config.userDNFormat.format(Rdn.escapeValue(name))

    private suspend fun rejected(name: String, e: LdapException): Nothing {
        // The server's message can tell whether the user exists; it is logged, not answered.
        log.debug { "LDAP bind failed for $name: ${e.message}" }
        authFailure("LDAP authentication failed")
    }

    override suspend fun auth(session: AuthSession, credential: UserPasswordCredential, context: ApplicationCall): AccountIdentifier {
        val config = session.lookupFlowMethodConfiguration<LDAPConfiguration>(this)
        // A simple bind with an empty password is an unauthenticated bind (RFC 4513), which servers may accept.
        if (credential.name.isBlank() || credential.password.isEmpty()) authFailure("LDAP authentication failed")

        withContext(Dispatchers.IO) {
            LdapNetworkConnection(connectionConfig(config)).use { connection ->
                try {
                    connection.bind(userDn(config, credential.name), credential.password)
                    connection.unBind()
                } catch (e: LdapAuthenticationException) {
                    rejected(credential.name, e)
                } catch (e: LdapNoSuchObjectException) {
                    rejected(credential.name, e)
                } catch (e: LdapException) {
                    log.warn { "LDAP server ${config.ldapServerUrl} failed: ${e.message}" }
                    throw StatusException(503, "The LDAP server is unavailable")
                }
            }
        }
        return LDAPIdentifier(config.ldapServerUrl, credential.name)
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
            val credential = call.getUsernamePasswordFromRequest(session)
            val identifier = auth(session, credential, call)

            val accountId = accountFor(session, identifier, functionAmendments?.get(AuthMethodFunctionAmendments.Registration))
            call.handleAuthSuccess(session, authContext(call), accountId)
        }
    }
}
