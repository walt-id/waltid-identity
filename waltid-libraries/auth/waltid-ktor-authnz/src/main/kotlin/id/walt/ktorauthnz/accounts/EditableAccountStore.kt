package id.walt.ktorauthnz.accounts

import id.walt.ktorauthnz.accounts.identifiers.methods.AccountIdentifier
import id.walt.ktorauthnz.methods.storeddata.AuthMethodStoredData
import id.walt.ktorauthnz.methods.storeddata.PasswordStoredData
import id.walt.ktorauthnz.security.PasswordHash

interface EditableAccountStore : AccountStore {

    //fun registerAccount(newAccount: Account)

    suspend fun addAccountIdentifierToAccount(accountId: String, newAccountIdentifier: AccountIdentifier)

    suspend fun addAccountIdentifierToAccount(account: Account, newAccountIdentifier: AccountIdentifier) =
        addAccountIdentifierToAccount(account.id, newAccountIdentifier)

    suspend fun removeAccountIdentifierFromAccount(accountIdentifier: AccountIdentifier)

    //suspend fun addAccountStoredData(accountIdentifier: AccountIdentifier, data: Pair<AuthenticationMethod, AuthMethodStoredData>)
    suspend fun addAccountIdentifierStoredData(accountIdentifier: AccountIdentifier, method: String, data: AuthMethodStoredData)
    suspend fun addAccountStoredData(accountId: String, method: String, data: AuthMethodStoredData)
    suspend fun updateAccountIdentifierStoredData(accountIdentifier: AccountIdentifier, method: String, data: AuthMethodStoredData)
    /** Persist a login-time upgrade. Concurrent stores should replace only the credential that was verified. */
    suspend fun upgradePasswordHash(identifier: AccountIdentifier, verified: PasswordStoredData, replacement: PasswordHash) {
        updateAccountIdentifierStoredData(identifier, verified.authMethod().id, verified.withPasswordHash(replacement))
    }

    suspend fun updateAccountStoredData(accountId: String, method: String, data: AuthMethodStoredData)
    suspend fun deleteAccountIdentifierStoredData(accountIdentifier: AccountIdentifier, method: String)
    suspend fun deleteAccountStoredData(accountId: String, method: String)
}
