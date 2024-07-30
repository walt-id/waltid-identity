package id.walt.methods

import org.apache.directory.api.ldap.model.exception.LdapException
import org.apache.directory.ldap.client.api.LdapConnection
import org.apache.directory.ldap.client.api.LdapNetworkConnection

fun auth(ldapServerUrl: String, userDNFormat: String, username: String, password: String): Boolean {
    val (hostname, port) = ldapServerUrl.removePrefix("ldap://").split(":")
    val userDN = userDNFormat.format(username)

    var connection: LdapConnection? = null

    return try {
        connection = LdapNetworkConnection(hostname, port.toInt())
        connection.bind(userDN, password)
        true
    } catch (e: LdapException) {
        e.printStackTrace()
        false
    } finally {
        connection?.unBind()
        connection?.close()
    }
}

// Example usage
fun main() {
    val result = auth("ldap://localhost:3893", "cn=%s,ou=superheros,dc=glauth,dc=com", "hackers", "dogood")
    println("Authentication result: $result")
}
