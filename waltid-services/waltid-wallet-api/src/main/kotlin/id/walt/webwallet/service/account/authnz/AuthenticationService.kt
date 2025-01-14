package id.walt.webwallet.service.account.authnz

import id.walt.ktorauthnz.accounts.EditableAccountStore
import id.walt.ktorauthnz.accounts.identifiers.methods.AccountIdentifier
import id.walt.ktorauthnz.methods.AuthenticationMethod
import id.walt.ktorauthnz.methods.data.AuthMethodStoredData
import id.walt.webwallet.db.models.authnz.AuthnzAccountIdentifiers
import id.walt.webwallet.db.models.authnz.AuthnzAccountIdentifiers.userId
import id.walt.webwallet.db.models.authnz.AuthnzStoredData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
class AuthenticationService {

    val editableAccountStore = object : EditableAccountStore {
        override suspend fun addAccountIdentifierToAccount(
            accountId: String,
            newAccountIdentifier: AccountIdentifier
        ): Unit = withContext(Dispatchers.IO) {
            transaction {
                AuthnzAccountIdentifiers.insert {
                    it[AuthnzAccountIdentifiers.userId] = UUID.fromString(accountId)
                    it[AuthnzAccountIdentifiers.identifier] = newAccountIdentifier.accountIdentifierName
                    //it[AuthnzAccountIdentifiers.method] =
                }
                Unit // Explicitly return Unit
            }
        }

        override suspend fun removeAccountIdentifierFromAccount(accountIdentifier: AccountIdentifier) {
            TODO("Not yet implemented")
        }

        override suspend fun addAccountIdentifierStoredData(
            accountIdentifier: AccountIdentifier,
            method: String,
            data: AuthMethodStoredData
        ): Unit = withContext(Dispatchers.IO) {
            val savableStoredData = data.transformSavable()
            transaction {
                val userId = AuthnzAccountIdentifiers
                    .select(AuthnzAccountIdentifiers.userId)
                    .where { AuthnzAccountIdentifiers.identifier eq accountIdentifier.accountIdentifierName }
                    .singleOrNull()?.get(AuthnzAccountIdentifiers.userId)
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
        ): Unit = withContext(Dispatchers.IO) {
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
            TODO("Not yet implemented")
        }

        override suspend fun updateAccountStoredData(
            accountId: String,
            method: String,
            data: AuthMethodStoredData
        ) {
            TODO("Not yet implemented")
        }

        override suspend fun deleteAccountIdentifierStoredData(
            accountIdentifier: AccountIdentifier,
            method: String
        ) {
            TODO("Not yet implemented")
        }

        override suspend fun deleteAccountStoredData(accountId: String, method: String) {
            TODO("Not yet implemented")
        }

        override suspend fun lookupStoredDataForAccount(
            accountId: String,
            method: AuthenticationMethod
        ): AuthMethodStoredData? {
            TODO()
        }


        override suspend fun lookupStoredDataForAccountIdentifier(
            identifier: AccountIdentifier,
            method: AuthenticationMethod
        ): AuthMethodStoredData? {
            TODO("Not yet implemented")
        }

        override suspend fun lookupAccountUuid(identifier: AccountIdentifier): String? =
            withContext(Dispatchers.IO) {
                transaction {
                    AuthnzAccountIdentifiers
                        .selectAll().where { AuthnzAccountIdentifiers.identifier eq identifier.accountIdentifierName }
                        .map { it[userId].toString() }
                        .firstOrNull()
                }
            }

        override suspend fun hasStoredDataFor(
            identifier: AccountIdentifier,
            method: AuthenticationMethod
        ): Boolean = withContext(Dispatchers.IO) {
            transaction {
                AuthnzStoredData
                    .selectAll().where { AuthnzStoredData.method eq method.toString() }
                    .count() > 0
            }
        }
    }
}

