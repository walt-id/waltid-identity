package id.walt.methods

/*
import org.ietf.jgss.*

fun auth(kerberosRealm: String, kerberosKdc: String, servicePrincipal: String, username: String, password: String): Boolean {
    System.setProperty("java.security.krb5.realm", kerberosRealm)
    System.setProperty("java.security.krb5.kdc", kerberosKdc)
    System.setProperty("javax.security.auth.useSubjectCredsOnly", "false")

    val gssManager = GSSManager.getInstance()
    val clientPrincipalName = "user@$kerberosRealm"
    val clientPrincipal = GSSName.NT_USER_NAME.apply {
        gssManager.createName(clientPrincipalName, this)
    }

    val credentials = gssManager.createCredential(
        clientPrincipal,
        GSSCredential.DEFAULT_LIFETIME,
        null,
        GSSCredential.INITIATE_ONLY
    )

    val serverPrincipal = gssManager.createName(servicePrincipal, GSSName.NT_HOSTBASED_SERVICE)

    val context = gssManager.createContext(
        serverPrincipal,
        GSSUtil.GSS_KRB5_MECH_OID,
        credentials,
        GSSContext.DEFAULT_LIFETIME
    )

    return try {
        context.requestMutualAuth(true)
        context.requestConf(true)
        context.requestInteg(true)

        val token = ByteArray(0)
        val outToken = context.initSecContext(token, 0, token.size)

        // This is where you would send the outToken to the server and get a response token back
        // For the purposes of this example, we'll assume the server accepted the token
        context.isEstablished
    } catch (e: GSSException) {
        e.printStackTrace()
        false
    } finally {
        context.dispose()
    }
}

// Example usage
fun main() {
    val result = auth("EXAMPLE.COM", "kerberos.example.com", "HTTP/kerberos.example.com", "user1", "pass1")
    println("Authentication result: $result")
}*/
