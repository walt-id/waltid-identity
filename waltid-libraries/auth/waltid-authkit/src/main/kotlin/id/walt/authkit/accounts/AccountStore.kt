package id.walt.authkit.accounts

import id.walt.authkit.accounts.identifiers.AccountIdentifier
import id.walt.authkit.methods.AuthenticationMethod
import id.walt.authkit.methods.data.AuthMethodStoredData
import id.walt.authkit.sessions.AuthSession

interface AccountStore {

    fun lookupStoredMultiDataForAccount(session: AuthSession, method: AuthenticationMethod): AuthMethodStoredData?

    fun lookupStoredDataFor(identifier: AccountIdentifier, method: AuthenticationMethod): AuthMethodStoredData?

    /**
     * Resolve account uuid for account identifier
     */
    fun lookupAccountUuid(identifier: AccountIdentifier): String

    fun hasStoredDataFor(identifier: AccountIdentifier, method: AuthenticationMethod): Boolean
}
