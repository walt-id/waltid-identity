package id.walt.ktorauthnz.accounts

import id.walt.ktorauthnz.accounts.identifiers.methods.AccountIdentifier
import id.walt.ktorauthnz.methods.AuthenticationMethod
import id.walt.ktorauthnz.methods.storeddata.AuthMethodStoredData

interface AccountStore {

    suspend fun lookupStoredDataForAccount(accountId: String, method: AuthenticationMethod): AuthMethodStoredData?

    suspend fun lookupStoredDataForAccountIdentifier(identifier: AccountIdentifier, method: AuthenticationMethod): AuthMethodStoredData?

    /**
     * Resolve account uuid for account identifier
     */
    suspend fun lookupAccountUuid(identifier: AccountIdentifier): String?

    suspend fun hasStoredDataFor(identifier: AccountIdentifier, method: AuthenticationMethod): Boolean
}
