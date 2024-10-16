package id.walt.ktorauthnz.accounts

import id.walt.ktorauthnz.accounts.identifiers.methods.AccountIdentifier
import id.walt.ktorauthnz.accounts.identifiers.methods.UsernameIdentifier
import id.walt.ktorauthnz.methods.AuthenticationMethod
import id.walt.ktorauthnz.methods.TOTP
import id.walt.ktorauthnz.methods.UserPass
import id.walt.ktorauthnz.methods.data.AuthMethodStoredData
import id.walt.ktorauthnz.methods.data.TOTPStoredData
import id.walt.ktorauthnz.methods.data.UserPassStoredData
import id.walt.ktorauthnz.sessions.AuthSession

object ExampleAccountStore : EditableAccountStore {

    // Account uuid -> account
    private val wip_accounts = HashMap<String, Account>()

    // TODO: Missing context, for multi tenancy

    // AccountIdentifier -> Account uuid
    private val wip_account_ids = HashMap<AccountIdentifier, String>()

    // Account uuid -> auth mechanisms
    private val wip_accountAuthMechanisms = HashMap<String, MutableMap<AuthenticationMethod, AuthMethodStoredData>>()


    init {
        val newAccount = Account("11111111-1111-1111-1111-000000000000", "Alice")
        registerAccount(newAccount)

        val accountIdentifier = UsernameIdentifier("alice1")
        addAccountIdentifierToAccount(newAccount, accountIdentifier)

        addAccountStoredData(newAccount.id, UserPass to UserPassStoredData("123456"))
        addAccountStoredData(newAccount.id, TOTP to TOTPStoredData("JBSWY3DPEHPK3PXP")) // https://totp.danhersam.com/
    }

    override fun registerAccount(newAccount: Account) {
        wip_accounts[newAccount.id] = newAccount
    }

    override fun addAccountIdentifierToAccount(accountId: String, newAccountIdentifier: AccountIdentifier) {
        wip_account_ids[newAccountIdentifier] = accountId
    }

    override fun addAccountStoredData(accountId: String, data: Pair<AuthenticationMethod, AuthMethodStoredData>) {
        if (!wip_accountAuthMechanisms.containsKey(accountId)) {
            wip_accountAuthMechanisms[accountId] = HashMap()
        }

        wip_accountAuthMechanisms[accountId]!![data.first] = data.second
    }

    override fun deleteAccountStoredData(accountId: String, dataFor: AuthenticationMethod) {
        wip_accountAuthMechanisms[accountId]!!.remove(dataFor)
    }

    // TODO
    override suspend fun lookupStoredMultiDataForAccount(session: AuthSession, method: AuthenticationMethod): AuthMethodStoredData? {
        check(session.accountId != null) { "No account id available for session yet (no AccountIdentifier used yet)." }
        val storedData = wip_accountAuthMechanisms[session.accountId]!![method]

        return storedData
    }

    // TODO
    override suspend fun lookupStoredDataFor(identifier: AccountIdentifier, method: AuthenticationMethod): AuthMethodStoredData? {
        val uuid = wip_account_ids[identifier] ?: error("No account for identifier: $identifier")
        val storedData = wip_accountAuthMechanisms[uuid]!![method]

        return storedData
    }

    override suspend fun hasStoredDataFor(identifier: AccountIdentifier, method: AuthenticationMethod): Boolean {
        val uuid = wip_account_ids[identifier] ?: error("No account for identifier: $identifier")
        return wip_accountAuthMechanisms[uuid]!!.containsKey(method)
    }

    override suspend fun lookupAccountUuid(identifier: AccountIdentifier): String {
        return wip_account_ids[identifier] ?: error("No account for account id: $this")
    }

}
