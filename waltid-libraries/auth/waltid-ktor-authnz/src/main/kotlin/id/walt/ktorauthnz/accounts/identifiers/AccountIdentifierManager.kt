package id.walt.ktorauthnz.accounts.identifiers

import id.walt.ktorauthnz.accounts.identifiers.methods.*

object AccountIdentifierManager {

    private val defaultIdentifiers =
        listOf(EmailIdentifier, JWTIdentifier, LDAPIdentifier, OIDCIdentifier, RADIUSIdentifier, UsernameIdentifier)

    private val factories: MutableMap<String, AccountIdentifier.AccountIdentifierFactory<out AccountIdentifier>> =
        defaultIdentifiers.associateBy { it.identifierName }.toMutableMap()

    fun registerAccountIdentifier(identifierFactory: AccountIdentifier.AccountIdentifierFactory<out AccountIdentifier>) =
        factories.set(identifierFactory.identifierName, identifierFactory)

    fun getAccountIdentifier(type: String, accountIdentifierDataString: String): AccountIdentifier {
        val factory = factories[type] ?: error("No such account identifier known")

        return factory.fromAccountIdentifierDataString(accountIdentifierDataString)
    }
}
