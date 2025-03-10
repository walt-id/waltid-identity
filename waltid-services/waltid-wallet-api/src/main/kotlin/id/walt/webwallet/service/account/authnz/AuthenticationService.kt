package id.walt.webwallet.service.account.authnz

import id.walt.ktorauthnz.accounts.EditableAccountStore
import id.walt.ktorauthnz.accounts.identifiers.methods.AccountIdentifier
import id.walt.ktorauthnz.methods.AuthenticationMethod
import id.walt.ktorauthnz.methods.data.AuthMethodStoredData
import id.walt.webwallet.db.models.Accounts
import id.walt.webwallet.db.models.authnz.AuthnzAccountIdentifiers
import id.walt.webwallet.db.models.authnz.AuthnzAccountIdentifiers.userId
import id.walt.webwallet.db.models.authnz.AuthnzStoredData
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
class AuthenticationService(private val dispatcher: CoroutineDispatcher = Dispatchers.IO) {

    val editableAccountStore = object : EditableAccountStore {
        override suspend fun addAccountIdentifierToAccount(
            accountId: String,
            newAccountIdentifier: AccountIdentifier
        ): Unit = withContext(dispatcher) {
            transaction {
                AuthnzAccountIdentifiers.insert {
                    it[userId] = UUID.fromString(accountId)
                    it[identifier] = newAccountIdentifier.accountIdentifierName
                    //it[AuthnzAccountIdentifiers.method] =
                }
                Unit // Explicitly return Unit
            }
        }

        override suspend fun removeAccountIdentifierFromAccount(accountIdentifier: AccountIdentifier) {
            throw NotImplementedError("removeAccountIdentifierFromAccount")
        }

        override suspend fun addAccountIdentifierStoredData(
            accountIdentifier: AccountIdentifier,
            method: String,
            data: AuthMethodStoredData
        ): Unit = withContext(dispatcher) {
            val savableStoredData = data.transformSavable()
            transaction {
                val userId = AuthnzAccountIdentifiers
                    .select(userId)
                    .where { AuthnzAccountIdentifiers.identifier eq accountIdentifier.accountIdentifierName }
                    .singleOrNull()?.get(userId)
                    ?: throw IllegalStateException("Account not found")

                AuthnzStoredData.insert {
                    it[accountId] = userId
                    it[AuthnzStoredData.method] = method
                    it[AuthnzStoredData.data] = savableStoredData
                }
            }
        }


        override suspend fun addAccountStoredData(
            accountId: String,
            method: String,
            data: AuthMethodStoredData
        ): Unit = withContext(dispatcher) {
            val savableStoredData = data.transformSavable()
            transaction {
                AuthnzStoredData.insert {
                    it[AuthnzStoredData.accountId] = UUID.fromString(accountId)
                    it[AuthnzStoredData.method] = method
                    it[AuthnzStoredData.data] = savableStoredData
                }
            }
        }

        override suspend fun updateAccountIdentifierStoredData(
            accountIdentifier: AccountIdentifier,
            method: String,
            data: AuthMethodStoredData
        ) {
            throw NotImplementedError("updateAccountIdentifierStoredData")
        }

        override suspend fun updateAccountStoredData(
            accountId: String,
            method: String,
            data: AuthMethodStoredData
        ) {
            throw NotImplementedError("updateAccountStoredData")
        }

        override suspend fun deleteAccountIdentifierStoredData(
            accountIdentifier: AccountIdentifier,
            method: String
        ) {
            throw NotImplementedError("deleteAccountIdentifierStoredData")
        }

        override suspend fun deleteAccountStoredData(accountId: String, method: String) {
            throw NotImplementedError("deleteAccountStoredData")
        }

        override suspend fun lookupStoredDataForAccount(
            accountId: String,
            method: AuthenticationMethod
        ): AuthMethodStoredData? {
            throw NotImplementedError("lookupStoredDataForAccount")
        }


        override suspend fun lookupStoredDataForAccountIdentifier(
            identifier: AccountIdentifier,
            method: AuthenticationMethod
        ): AuthMethodStoredData? {
            throw NotImplementedError("lookupStoredDataForAccountIdentifier")
        }

        override suspend fun lookupAccountUuid(identifier: AccountIdentifier): String? =
            withContext(dispatcher) {
                transaction {
                    val existingAccount = Accounts
                        .select(Accounts.id)
                        .where { Accounts.name eq identifier.toDataString() }
                        .map { it[Accounts.id] }
                        .firstOrNull()

                    if (existingAccount != null) {
                        return@transaction existingAccount.toString()
                    }
                    return@transaction null
                }
            }

        override suspend fun hasStoredDataFor(
            identifier: AccountIdentifier,
            method: AuthenticationMethod
        ): Boolean = withContext(dispatcher) {
            transaction {
                AuthnzStoredData
                    .selectAll().where { AuthnzStoredData.method eq method.toString() }
                    .count() > 0
            }
        }
    }
}

