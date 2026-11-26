package id.walt.wallet2.auth

import id.walt.ktorauthnz.accounts.PasswordHashUpgradeStore
import id.walt.ktorauthnz.accounts.identifiers.methods.AccountIdentifier
import id.walt.ktorauthnz.accounts.identifiers.methods.EmailIdentifier
import id.walt.ktorauthnz.methods.AuthenticationMethod
import id.walt.ktorauthnz.methods.storeddata.AuthMethodStoredData
import id.walt.ktorauthnz.methods.storeddata.EmailPassStoredData
import id.walt.ktorauthnz.methods.storeddata.PasswordStoredData
import id.walt.ktorauthnz.security.PasswordHash
import java.util.concurrent.ConcurrentHashMap
import kotlin.uuid.Uuid

internal interface Wallet2AccountStore : PasswordHashUpgradeStore {
    /** Creates an account with its first verifier. Returns null when the email is already registered. */
    suspend fun register(email: EmailIdentifier, password: String): String?

    override suspend fun lookupAccountIdentifiers(accountId: String): List<AccountIdentifier>

    override suspend fun addAccountIdentifierStoredData(
        accountIdentifier: AccountIdentifier, method: String, data: AuthMethodStoredData,
    ) = updateAccountIdentifierStoredData(accountIdentifier, method, data)

    override suspend fun addAccountStoredData(accountId: String, method: String, data: AuthMethodStoredData) =
        updateAccountStoredData(accountId, method, data)

    override suspend fun hasStoredDataFor(identifier: AccountIdentifier, method: AuthenticationMethod): Boolean =
        lookupStoredDataForAccountIdentifier(identifier, method) != null
}

internal class InMemoryWallet2AccountStore : Wallet2AccountStore {
    private class Identity(
        val accountId: String,
        val data: ConcurrentHashMap<String, AuthMethodStoredData> = ConcurrentHashMap(),
    )

    private val identities = ConcurrentHashMap<AccountIdentifier, Identity>()
    private val accountData = ConcurrentHashMap<Pair<String, String>, AuthMethodStoredData>()

    override suspend fun register(email: EmailIdentifier, password: String): String? {
        if (identities.containsKey(email)) return null
        val accountId = Uuid.random().toString()
        val verifier = EmailPassStoredData(password = password).transformSavable()
        val identity = Identity(accountId, ConcurrentHashMap(mapOf(verifier.authMethod().id to verifier)))
        return accountId.takeIf { identities.putIfAbsent(email, identity) == null }
    }

    override suspend fun lookupAccountUuid(identifier: AccountIdentifier): String? = identities[identifier]?.accountId

    override suspend fun lookupAccountIdentifiers(accountId: String): List<AccountIdentifier> =
        identities.entries.filter { it.value.accountId == accountId }.map { it.key }

    override suspend fun addAccountIdentifierToAccount(accountId: String, newAccountIdentifier: AccountIdentifier) {
        val identity = identities.computeIfAbsent(newAccountIdentifier) { Identity(accountId) }
        require(identity.accountId == accountId) { "Identifier belongs to another account" }
    }

    override suspend fun removeAccountIdentifierFromAccount(accountIdentifier: AccountIdentifier) {
        identities.remove(accountIdentifier)
    }

    override suspend fun updateAccountIdentifierStoredData(
        accountIdentifier: AccountIdentifier, method: String, data: AuthMethodStoredData,
    ) {
        val identity = requireNotNull(identities[accountIdentifier]) { "Unknown account identifier" }
        identity.data[method] = data.transformSavable()
    }

    override suspend fun updateAccountStoredData(accountId: String, method: String, data: AuthMethodStoredData) {
        accountData[accountId to method] = data.transformSavable()
    }

    override suspend fun upgradePasswordHash(
        identifier: AccountIdentifier, verified: PasswordStoredData, replacement: PasswordHash,
    ) {
        identities[identifier]?.data?.replace(verified.authMethod().id, verified, verified.withPasswordHash(replacement))
    }

    override suspend fun deleteAccountIdentifierStoredData(accountIdentifier: AccountIdentifier, method: String) {
        identities[accountIdentifier]?.data?.remove(method)
    }

    override suspend fun deleteAccountStoredData(accountId: String, method: String) {
        accountData.remove(accountId to method)
    }

    override suspend fun lookupStoredDataForAccount(accountId: String, method: AuthenticationMethod): AuthMethodStoredData? =
        accountData[accountId to method.id]

    override suspend fun lookupStoredDataForAccountIdentifier(
        identifier: AccountIdentifier, method: AuthenticationMethod,
    ): AuthMethodStoredData? = identities[identifier]?.data?.get(method.id)
}
