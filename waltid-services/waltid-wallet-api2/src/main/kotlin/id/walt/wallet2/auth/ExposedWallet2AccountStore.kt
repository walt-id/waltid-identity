package id.walt.wallet2.auth

import id.walt.ktorauthnz.accounts.identifiers.methods.AccountIdentifier
import id.walt.ktorauthnz.accounts.identifiers.methods.EmailIdentifier
import id.walt.ktorauthnz.methods.AuthenticationMethod
import id.walt.ktorauthnz.methods.storeddata.AuthMethodStoredData
import id.walt.ktorauthnz.methods.storeddata.EmailPassStoredData
import id.walt.ktorauthnz.methods.storeddata.PasswordStoredData
import id.walt.ktorauthnz.security.PasswordHash
import id.walt.wallet2.persistence.Wallet2Tables.AccountAuthData
import id.walt.wallet2.persistence.Wallet2Tables.AuthIdentifiers
import id.walt.wallet2.persistence.Wallet2Tables.IdentifierAuthData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.upsert
import kotlin.uuid.Uuid

internal class ExposedWallet2AccountStore(private val db: Database) : Wallet2AccountStore {
    override suspend fun register(email: EmailIdentifier, password: String): String? {
        if (lookupAccountUuid(email) != null) return null
        val accountId = Uuid.random().toString()
        val identifier = encodeIdentifier(email)
        val verifier = EmailPassStoredData(password = password).transformSavable()
        val data = encodeData(verifier)

        return query {
            if (!insertIdentity(identifier, accountId)) return@query null
            IdentifierAuthData.insert {
                it[IdentifierAuthData.identifier] = identifier
                it[IdentifierAuthData.method] = verifier.authMethod().id
                it[IdentifierAuthData.data] = data
            }
            accountId
        }
    }

    override suspend fun lookupAccountUuid(identifier: AccountIdentifier): String? =
        query { findAccount(encodeIdentifier(identifier)) }

    override suspend fun lookupAccountIdentifiers(accountId: String): List<AccountIdentifier> = query {
        AuthIdentifiers.select(AuthIdentifiers.identifier)
            .where { AuthIdentifiers.accountId eq accountId }
            .map { Json.decodeFromString(it[AuthIdentifiers.identifier]) }
    }

    override suspend fun addAccountIdentifierToAccount(accountId: String, newAccountIdentifier: AccountIdentifier) {
        val identifier = encodeIdentifier(newAccountIdentifier)
        query {
            require(insertIdentity(identifier, accountId) || findAccount(identifier) == accountId) {
                "Identifier belongs to another account"
            }
        }
    }

    override suspend fun removeAccountIdentifierFromAccount(accountIdentifier: AccountIdentifier) {
        val identifier = encodeIdentifier(accountIdentifier)
        query {
            IdentifierAuthData.deleteWhere { IdentifierAuthData.identifier eq identifier }
            AuthIdentifiers.deleteWhere { AuthIdentifiers.identifier eq identifier }
        }
    }

    override suspend fun updateAccountIdentifierStoredData(
        accountIdentifier: AccountIdentifier, method: String, data: AuthMethodStoredData,
    ) {
        val identifier = encodeIdentifier(accountIdentifier)
        val storedData = encodeData(data.transformSavable())
        query {
            requireNotNull(findAccount(identifier)) { "Unknown account identifier" }
            IdentifierAuthData.upsert {
                it[IdentifierAuthData.identifier] = identifier
                it[IdentifierAuthData.method] = method
                it[IdentifierAuthData.data] = storedData
            }
        }
    }

    override suspend fun updateAccountStoredData(accountId: String, method: String, data: AuthMethodStoredData) {
        val storedData = encodeData(data.transformSavable())
        query {
            AccountAuthData.upsert {
                it[AccountAuthData.accountId] = accountId
                it[AccountAuthData.method] = method
                it[AccountAuthData.data] = storedData
            }
        }
    }

    override suspend fun upgradePasswordHash(
        identifier: AccountIdentifier, verified: PasswordStoredData, replacement: PasswordHash,
    ) {
        val identifierData = encodeIdentifier(identifier)
        val method = verified.authMethod().id
        val verifiedData = encodeData(verified)
        val replacementData = encodeData(verified.withPasswordHash(replacement))
        query {
            IdentifierAuthData.update({
                (IdentifierAuthData.identifier eq identifierData) and
                        (IdentifierAuthData.method eq method) and (IdentifierAuthData.data eq verifiedData)
            }) { it[IdentifierAuthData.data] = replacementData }
        }
    }

    override suspend fun deleteAccountIdentifierStoredData(accountIdentifier: AccountIdentifier, method: String) {
        val identifier = encodeIdentifier(accountIdentifier)
        query {
            IdentifierAuthData.deleteWhere {
                (IdentifierAuthData.identifier eq identifier) and (IdentifierAuthData.method eq method)
            }
        }
    }

    override suspend fun deleteAccountStoredData(accountId: String, method: String) {
        query {
            AccountAuthData.deleteWhere {
                (AccountAuthData.accountId eq accountId) and (AccountAuthData.method eq method)
            }
        }
    }

    override suspend fun lookupStoredDataForAccount(accountId: String, method: AuthenticationMethod): AuthMethodStoredData? =
        query {
            AccountAuthData.select(AccountAuthData.data)
                .where { (AccountAuthData.accountId eq accountId) and (AccountAuthData.method eq method.id) }
                .singleOrNull()?.get(AccountAuthData.data)?.let { Json.decodeFromString(it) }
        }

    override suspend fun lookupStoredDataForAccountIdentifier(
        identifier: AccountIdentifier, method: AuthenticationMethod,
    ): AuthMethodStoredData? = query {
        IdentifierAuthData.select(IdentifierAuthData.data)
            .where {
                (IdentifierAuthData.identifier eq encodeIdentifier(identifier)) and (IdentifierAuthData.method eq method.id)
            }
            .singleOrNull()?.get(IdentifierAuthData.data)?.let { Json.decodeFromString(it) }
    }

    private fun insertIdentity(identifier: String, accountId: String): Boolean =
        AuthIdentifiers.insertIgnore {
            it[AuthIdentifiers.identifier] = identifier
            it[AuthIdentifiers.accountId] = accountId
        }.insertedCount == 1

    private fun findAccount(identifier: String): String? =
        AuthIdentifiers.select(AuthIdentifiers.accountId)
            .where { AuthIdentifiers.identifier eq identifier }
            .singleOrNull()?.get(AuthIdentifiers.accountId)

    private fun encodeIdentifier(identifier: AccountIdentifier): String = Json.encodeToString(identifier)

    private fun encodeData(data: AuthMethodStoredData): String = Json.encodeToString(data)

    private suspend fun <T> query(block: suspend JdbcTransaction.() -> T): T =
        withContext(Dispatchers.IO) { suspendTransaction(db, statement = block) }
}
