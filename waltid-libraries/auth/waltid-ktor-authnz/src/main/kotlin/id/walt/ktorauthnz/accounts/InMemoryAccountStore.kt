package id.walt.ktorauthnz.accounts

import id.walt.ktorauthnz.accounts.identifiers.methods.AccountIdentifier
import id.walt.ktorauthnz.methods.AuthenticationMethod
import id.walt.ktorauthnz.methods.storeddata.AuthMethodStoredData
import id.walt.ktorauthnz.tenants.currentAuthnzTenant
import java.util.concurrent.ConcurrentHashMap
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Accounts in memory, for development, tests and prototypes - a complete [EditableAccountStore] to start with before
 * writing one for your database.
 *
 * Multi-tenant: under an `authnzTenant` scope every call works on that tenant's accounts only (see
 * [currentAuthnzTenant]), so `alice` of one tenant is not `alice` of another. Outside a scope it works on the accounts
 * of no tenant. Set accounts up for a tenant with `inAuthnzTenant("org1") { store.addAccount(...) }`.
 */
class InMemoryAccountStore : EditableAccountStore {

    private data class Scoped<T>(val tenant: String?, val key: T)

    private val accountIds = ConcurrentHashMap<Scoped<AccountIdentifier>, String>()
    private val identifierData = ConcurrentHashMap<Scoped<Pair<AccountIdentifier, String>>, AuthMethodStoredData>()
    private val accountData = ConcurrentHashMap<Scoped<Pair<String, String>>, AuthMethodStoredData>()

    private suspend fun <T> scoped(key: T) = Scoped(currentAuthnzTenant(), key)

    /**
     * Adds an account with [identifiers], identifier-bound data (e.g. a password: `UserPass.id to
     * UserPassStoredData("...")` - hashed when stored) and account-bound data (e.g. a TOTP secret). Returns its id.
     */
    @OptIn(ExperimentalUuidApi::class)
    suspend fun addAccount(
        vararg identifiers: AccountIdentifier,
        identifierData: Map<String, AuthMethodStoredData> = emptyMap(),
        accountData: Map<String, AuthMethodStoredData> = emptyMap(),
        accountId: String = Uuid.random().toString(),
    ): String {
        identifiers.forEach { identifier ->
            addAccountIdentifierToAccount(accountId, identifier)
            identifierData.forEach { (method, data) -> addAccountIdentifierStoredData(identifier, method, data) }
        }
        accountData.forEach { (method, data) -> addAccountStoredData(accountId, method, data) }
        return accountId
    }

    override suspend fun lookupAccountUuid(identifier: AccountIdentifier) = accountIds[scoped(identifier)]

    override suspend fun addAccountIdentifierToAccount(accountId: String, newAccountIdentifier: AccountIdentifier) {
        accountIds[scoped(newAccountIdentifier)] = accountId
    }

    override suspend fun removeAccountIdentifierFromAccount(accountIdentifier: AccountIdentifier) {
        accountIds.remove(scoped(accountIdentifier))
    }

    override suspend fun lookupStoredDataForAccount(accountId: String, method: AuthenticationMethod) =
        accountData[scoped(accountId to method.id)]

    override suspend fun lookupStoredDataForAccountIdentifier(identifier: AccountIdentifier, method: AuthenticationMethod) =
        identifierData[scoped(identifier to method.id)]

    override suspend fun hasStoredDataFor(identifier: AccountIdentifier, method: AuthenticationMethod) =
        identifierData.containsKey(scoped(identifier to method.id))

    override suspend fun addAccountIdentifierStoredData(accountIdentifier: AccountIdentifier, method: String, data: AuthMethodStoredData) =
        updateAccountIdentifierStoredData(accountIdentifier, method, data)

    override suspend fun updateAccountIdentifierStoredData(accountIdentifier: AccountIdentifier, method: String, data: AuthMethodStoredData) {
        identifierData[scoped(accountIdentifier to method)] = data.transformSavable()
    }

    override suspend fun deleteAccountIdentifierStoredData(accountIdentifier: AccountIdentifier, method: String) {
        identifierData.remove(scoped(accountIdentifier to method))
    }

    override suspend fun addAccountStoredData(accountId: String, method: String, data: AuthMethodStoredData) =
        updateAccountStoredData(accountId, method, data)

    override suspend fun updateAccountStoredData(accountId: String, method: String, data: AuthMethodStoredData) {
        accountData[scoped(accountId to method)] = data.transformSavable()
    }

    override suspend fun deleteAccountStoredData(accountId: String, method: String) {
        accountData.remove(scoped(accountId to method))
    }
}
