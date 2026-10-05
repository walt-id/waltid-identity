package id.walt.ktorauthnz.methods

object AuthMethodManager {

    val registeredAuthMethods = HashMap<String, AuthenticationMethod>()

    fun registerAuthenticationMethod(authenticationMethod: AuthenticationMethod) =
        registeredAuthMethods.set(authenticationMethod.id, authenticationMethod)

    fun registerAuthenticationMethods(vararg authenticationMethods: AuthenticationMethod) =
        authenticationMethods.forEach { registerAuthenticationMethod(it) }

    fun getAuthenticationMethodById(id: String): AuthenticationMethod =
        registeredAuthMethods[id] ?: throw IllegalArgumentException("Unknown authentication method: $id")

    init {
        registerAuthenticationMethods(
            EmailCode,
            EmailPass,
            Identify,
            JWT,
            // Kerberos,
            LDAP,
            OIDC,
            Passkey,
            RADIUS,
            RecoveryCode,
            TOTP,
            TotpSetup,
            UserPass,
            VerifiableCredential,
            Web3
        )
    }

}
