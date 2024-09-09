package id.walt.authkit.accounts

import id.walt.authkit.accounts.identifiers.AccountIdentifier
import id.walt.authkit.methods.AuthenticationMethod
import id.walt.authkit.methods.data.AuthMethodStoredData

interface EditableAccountStore : AccountStore {

    fun registerAccount(newAccount: Account)

    fun addAccountIdentifierToAccount(accountId: String, newAccountIdentifier: AccountIdentifier)

    fun addAccountIdentifierToAccount(account: Account, newAccountIdentifier: AccountIdentifier) =
        addAccountIdentifierToAccount(account.id, newAccountIdentifier)


    fun addAccountStoredData(accountId: String, data: Pair<AuthenticationMethod, AuthMethodStoredData>)

    fun deleteAccountStoredData(accountId: String, dataFor: AuthenticationMethod)
}
