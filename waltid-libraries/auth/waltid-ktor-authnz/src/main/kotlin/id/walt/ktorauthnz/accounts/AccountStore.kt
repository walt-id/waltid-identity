package id.walt.ktorauthnz.accounts

import id.walt.ktorauthnz.accounts.identifiers.methods.AccountIdentifier
import id.walt.ktorauthnz.methods.AuthenticationMethod
import id.walt.ktorauthnz.methods.data.AuthMethodStoredData
import id.walt.ktorauthnz.sessions.AuthSession

interface AccountStore {

    fun lookupStoredMultiDataForAccount(session: AuthSession, method: AuthenticationMethod): AuthMethodStoredData?

    fun lookupStoredDataFor(identifier: AccountIdentifier, method: AuthenticationMethod): AuthMethodStoredData?

    /**
     * Resolve account uuid for account identifier
     */
    fun lookupAccountUuid(identifier: AccountIdentifier): String

    fun hasStoredDataFor(identifier: AccountIdentifier, method: AuthenticationMethod): Boolean
}
