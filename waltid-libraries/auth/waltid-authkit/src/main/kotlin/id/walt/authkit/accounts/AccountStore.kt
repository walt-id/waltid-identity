package id.walt.authkit.accounts

import id.walt.authkit.AuthContext
import id.walt.authkit.accounts.identifiers.AccountIdentifier
import id.walt.authkit.accounts.identifiers.UsernameIdentifier
import id.walt.authkit.methods.AuthenticationMethod
import id.walt.authkit.methods.UserPass
import id.walt.authkit.methods.data.AuthMethodStoredData
import kotlin.uuid.Uuid

@OptIn(ExperimentalStdlibApi::class)
object AccountStore {

    // Account uuid -> account
    val wip_accounts = HashMap<Uuid, Account>()

    // AccountIdentifier -> Account uuid
    val wip_account_ids = HashMap<AccountIdentifier, Uuid>()

    // Account uuid -> auth mechanisms
    val wip_accountAuthMechanisms = HashMap<Uuid, HashMap<AuthenticationMethod, AuthMethodStoredData>>()


    init {
        val newAccount = Account(Uuid.random(), "Alice")
        wip_accounts[newAccount.id] = newAccount

        val accountIdentifier = UsernameIdentifier("alice1")
        wip_account_ids[accountIdentifier] = newAccount.id

        wip_accountAuthMechanisms[newAccount.id] = hashMapOf(UserPass to UserPass.UserPassStoredData("123456"))
    }

    fun lookupStoredDataFor(identifier: AccountIdentifier, method: AuthenticationMethod): AuthMethodStoredData {
        val uuid = wip_account_ids[identifier] ?: error("No account for identifier: $identifier")
        val storedData = wip_accountAuthMechanisms[uuid]!![method] ?: error("No stored data for method: $method")

        return storedData
    }

}
