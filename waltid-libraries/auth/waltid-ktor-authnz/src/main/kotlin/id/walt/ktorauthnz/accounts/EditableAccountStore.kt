package id.walt.ktorauthnz.accounts

import id.walt.ktorauthnz.accounts.identifiers.methods.AccountIdentifier
import id.walt.ktorauthnz.methods.AuthenticationMethod
import id.walt.ktorauthnz.methods.data.AuthMethodStoredData

interface EditableAccountStore : AccountStore {

    fun registerAccount(newAccount: Account)

    fun addAccountIdentifierToAccount(accountId: String, newAccountIdentifier: AccountIdentifier)

    fun addAccountIdentifierToAccount(account: Account, newAccountIdentifier: AccountIdentifier) =
        addAccountIdentifierToAccount(account.id, newAccountIdentifier)


    fun addAccountStoredData(accountId: String, data: Pair<AuthenticationMethod, AuthMethodStoredData>)

    fun deleteAccountStoredData(accountId: String, dataFor: AuthenticationMethod)
}
