package id.walt.ktorauthnz.accounts

import id.walt.ktorauthnz.accounts.identifiers.methods.AccountIdentifier
import id.walt.ktorauthnz.accounts.identifiers.methods.UsernameIdentifier
import id.walt.ktorauthnz.methods.AuthenticationMethod
import id.walt.ktorauthnz.methods.TOTP
import id.walt.ktorauthnz.methods.UserPass
import id.walt.ktorauthnz.methods.storeddata.AuthMethodStoredData
import id.walt.ktorauthnz.methods.storeddata.TOTPStoredData
import id.walt.ktorauthnz.methods.storeddata.UserPassStoredData
import kotlinx.coroutines.runBlocking

object ExampleAccountStore : EditableAccountStore {

    /** Account uuid -> account */
    private val wip_accounts = HashMap<String, Account>()

    // TODO: Missing context, for multi tenancy

    /** AccountIdentifier -> Account uuid */
    private val wip_account_ids = HashMap<AccountIdentifier, String>()

    /** Account identifier -> auth method -> data */
    private val wip_accountIdentifierStoredData = HashMap<AccountIdentifier, HashMap<String, AuthMethodStoredData>>()
    /** Account uuid (does not need account identifier) -> auth method -> data */
    private val wip_accountStoredData = HashMap<String, HashMap<String, AuthMethodStoredData>>()


    init {
        val newAccount = Account("11111111-1111-1111-1111-000000000000", "Alice")
        registerAccount(newAccount)

        val accountIdentifier = UsernameIdentifier("alice1")
        runBlocking {
            addAccountIdentifierToAccount(newAccount, accountIdentifier)

            addAccountIdentifierStoredData(accountIdentifier, UserPass.id, UserPassStoredData("123456"))
            addAccountStoredData(newAccount.id, TOTP.id, TOTPStoredData("JBSWY3DPEHPK3PXP")) // https://totp.danhersam.com/
        }
    }

    fun registerAccount(newAccount: Account) {
        wip_accounts[newAccount.id] = newAccount
        println("Registered account: $newAccount")
        println("Now accounts are: $wip_accounts")
    }

    override suspend fun addAccountIdentifierToAccount(accountId: String, newAccountIdentifier: AccountIdentifier) {
        println("Added account identifier $newAccountIdentifier to account $accountId")
        wip_account_ids[newAccountIdentifier] = accountId
        println("Now account ids is: $wip_account_ids")
    }

    override suspend fun removeAccountIdentifierFromAccount(accountIdentifier: AccountIdentifier) {
        wip_account_ids.remove(accountIdentifier)
    }

    override suspend fun addAccountIdentifierStoredData(accountIdentifier: AccountIdentifier, method: String, data: AuthMethodStoredData) {
        updateAccountIdentifierStoredData(accountIdentifier, method, data)
    }
    override suspend fun addAccountStoredData(accountId: String, method: String, data: AuthMethodStoredData) {
        updateAccountStoredData(accountId, method, data)
    }
    override suspend fun updateAccountIdentifierStoredData(accountIdentifier: AccountIdentifier, method: String, data: AuthMethodStoredData) {
        wip_accountIdentifierStoredData.getOrPut(accountIdentifier) { HashMap() }[method] = data.transformSavable()
    }
    override suspend fun updateAccountStoredData(accountId: String, method: String, data: AuthMethodStoredData) {
        wip_accountStoredData.getOrPut(accountId) { HashMap() }[method] = data.transformSavable()
    }
    override suspend fun deleteAccountIdentifierStoredData(accountIdentifier: AccountIdentifier, method: String) {
        wip_accountIdentifierStoredData.remove(accountIdentifier)
    }
    override suspend fun deleteAccountStoredData(accountId: String, method: String) {
        wip_accountStoredData.remove(accountId)
    }

    // TODO
    override suspend fun lookupStoredDataForAccount(accountId: String, method: AuthenticationMethod): AuthMethodStoredData? {
        println("Lookup stored multi data for account $accountId, method $method TODO")
        val storedData: AuthMethodStoredData? = wip_accountStoredData[accountId]?.get(method.id)

        return storedData
    }

    // TODO
    override suspend fun lookupStoredDataForAccountIdentifier(identifier: AccountIdentifier, method: AuthenticationMethod): AuthMethodStoredData? {
        //val uuid = wip_account_ids[identifier] ?: error("No account for identifier: $identifier")
        val storedData = wip_accountIdentifierStoredData[identifier]?.get(method.id)
        println("Lookup stored data for method $method for identifier $identifier: $storedData")
        println("Account auth mechanisms is: $wip_accountIdentifierStoredData")

        return storedData
    }

    override suspend fun hasStoredDataFor(identifier: AccountIdentifier, method: AuthenticationMethod): Boolean {
        //val uuid = wip_account_ids[identifier] ?: error("No account for identifier: $identifier")
        println("Checking if stored data for $identifier, method $method")
        println("Account auth mechanisms is: $wip_accountIdentifierStoredData")
        return wip_accountIdentifierStoredData.containsKey(identifier)
    }

    override suspend fun lookupAccountUuid(identifier: AccountIdentifier): String? {
        println("Lookup account uuid by identifier: $identifier")
        println("Account ids is: $wip_accounts")
        return wip_account_ids[identifier]
    }

}
