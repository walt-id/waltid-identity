package id.walt.authkit.accounts

import id.walt.authkit.accounts.identifiers.AccountIdentifier
import id.walt.authkit.accounts.identifiers.UsernameIdentifier
import id.walt.authkit.methods.AuthenticationMethod
import id.walt.authkit.methods.TOTP
import id.walt.authkit.methods.UserPass
import id.walt.authkit.methods.data.AuthMethodStoredData
import id.walt.authkit.sessions.AuthSession

object ExampleAccountStore : AccountStoreInterface {

    // Account uuid -> account
    private val wip_accounts = HashMap<String, Account>()

    // TODO: Missing context, for multi tenancy

    // AccountIdentifier -> Account uuid
    private val wip_account_ids = HashMap<AccountIdentifier, String>()

    // Account uuid -> auth mechanisms
    private val wip_accountAuthMechanisms = HashMap<String, Map<AuthenticationMethod, AuthMethodStoredData>>()


    init {
        val newAccount = Account("11111111-1111-1111-1111-000000000000", "Alice")
        wip_accounts[newAccount.id] = newAccount

        val accountIdentifier = UsernameIdentifier("alice1")
        wip_account_ids[accountIdentifier] = newAccount.id

        wip_accountAuthMechanisms[newAccount.id] = hashMapOf(
            UserPass to UserPass.UserPassStoredData("123456"),
            TOTP to TOTP.TOTPStoredData("JBSWY3DPEHPK3PXP") // https://totp.danhersam.com/
        )
    }

    // TODO
    override fun lookupStoredMultiDataForAccount(session: AuthSession, method: AuthenticationMethod): AuthMethodStoredData? {
        check(session.accountId != null) { "No account id available for session yet (no AccountIdentifier used yet)." }
        val storedData = wip_accountAuthMechanisms[session.accountId]!![method]

        return storedData
    }

    // TODO
    override fun lookupStoredDataFor(identifier: AccountIdentifier, method: AuthenticationMethod): AuthMethodStoredData? {
        val uuid = wip_account_ids[identifier] ?: error("No account for identifier: $identifier")
        val storedData = wip_accountAuthMechanisms[uuid]!![method]

        return storedData
    }

    override fun hasStoredDataFor(identifier: AccountIdentifier, method: AuthenticationMethod): Boolean {
        val uuid = wip_account_ids[identifier] ?: error("No account for identifier: $identifier")
        return wip_accountAuthMechanisms[uuid]!!.containsKey(method)
    }

    override fun lookupAccountUuid(identifier: AccountIdentifier): String {
        return wip_account_ids[identifier] ?: error("No account for account id: $this")
    }

}
