package id.walt.ktorauthnz.accounts.identifiers

import id.walt.ktorauthnz.accounts.identifiers.methods.*

object AccountIdentifierManager {

    private val defaultIdentifiers =
        listOf(
            EmailIdentifier, JWTIdentifier, LDAPIdentifier, OIDCIdentifier, RADIUSIdentifier, UsernameIdentifier,
            Web3Identifier, VerifiableCredentialIdentifier, PasskeyIdentifier
        )

    private val factories: MutableMap<String, AccountIdentifier.AccountIdentifierFactory<out AccountIdentifier>> =
        defaultIdentifiers.associateBy { it.identifierName }.toMutableMap()

    fun registerAccountIdentifier(identifierFactory: AccountIdentifier.AccountIdentifierFactory<out AccountIdentifier>) =
        factories.set(identifierFactory.identifierName, identifierFactory)

    fun getAccountIdentifier(type: String, accountIdentifierDataString: String): AccountIdentifier {
        val factory = requireNotNull(factories[type]) { "Unknown account identifier type: $type" }

        return factory.fromAccountIdentifierDataString(accountIdentifierDataString)
    }
}
