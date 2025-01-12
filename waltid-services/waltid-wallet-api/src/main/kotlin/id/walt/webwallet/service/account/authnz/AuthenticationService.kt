import AccountIdentifiers.userId
import id.walt.ktorauthnz.accounts.EditableAccountStore
import id.walt.ktorauthnz.accounts.identifiers.methods.AccountIdentifier
import id.walt.ktorauthnz.methods.AuthenticationMethod
import id.walt.ktorauthnz.methods.data.AuthMethodStoredData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*
import kotlin.uuid.ExperimentalUuidApi

object Users : Table() {
    val id = uuid("id").autoGenerate()
    val publicKey = varchar("public_key", 42).uniqueIndex() // Ethereum addresses are 42 chars including '0x'

    override val primaryKey = PrimaryKey(id)
}

object AccountIdentifiers : Table() {
    val id = uuid("id").autoGenerate()
    val userId = uuid("user_id").references(Users.id)
    val identifier = varchar("identifier", 255)
    val method = varchar("method", 50)

    override val primaryKey = PrimaryKey(id)
}


object StoredData : Table() {
    val id = uuid("id").autoGenerate()
    val accountId = uuid("account_id").references(Users.id)
    val method = varchar("method", 50)
    val data = text("data")

    override val primaryKey = PrimaryKey(id)
}


@OptIn(ExperimentalUuidApi::class)
class AuthenticationService(private val database: Database) {

    init {
        transaction(database) {
            SchemaUtils.create(Users, AccountIdentifiers, StoredData)
        }
    }


    val editableAccountStore = object : EditableAccountStore {
        override suspend fun addAccountIdentifierToAccount(
            accountId: String,
            newAccountIdentifier: AccountIdentifier
        ): Unit = withContext(Dispatchers.IO) {
            transaction(database) {
                AccountIdentifiers.insert {
                    it[userId] = UUID.fromString(accountId)
                    it[identifier] = newAccountIdentifier.accountIdentifierName
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
            transaction(database) {
                val userId = AccountIdentifiers
                    .select(userId)
                    .where { AccountIdentifiers.identifier eq accountIdentifier.accountIdentifierName }
                    .singleOrNull()?.get(AccountIdentifiers.userId)
                    ?: throw IllegalStateException("Account not found")

                StoredData.insert {
                    it[accountId] = userId
                    it[StoredData.method] = method
                    it[StoredData.data] = data.toString()
                }
                Unit
            }
        }


        override suspend fun addAccountStoredData(
            accountId: String,
            method: String,
            data: AuthMethodStoredData
        ): Unit = withContext(Dispatchers.IO) {
            transaction(database) {
                StoredData.insert {
                    it[StoredData.accountId] = UUID.fromString(accountId)
                    it[StoredData.method] = method
                    it[StoredData.data] = data.toString()
                }
                Unit
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

        override suspend fun lookupAccountUuid(identifier: AccountIdentifier): String =
            withContext(Dispatchers.IO) {
                transaction(database) {
                    AccountIdentifiers
                        .selectAll().where { AccountIdentifiers.identifier eq identifier.accountIdentifierName }
                        .map { it[userId].toString() }
                        .firstOrNull() ?: throw IllegalStateException("Account not found")
                }
            }


        override suspend fun hasStoredDataFor(
            identifier: AccountIdentifier,
            method: AuthenticationMethod
        ): Boolean = withContext(Dispatchers.IO) {
            transaction(database) {
                val userId = AccountIdentifiers
                    .select(userId)
                    .map { it[userId] }
                    .firstOrNull() ?: return@transaction false

                StoredData.selectAll().where {
                    (StoredData.accountId eq userId) and (StoredData.method eq method.id)
                }.count() > 0
            }
        }

        suspend fun createOrGetUserByPublicKey(publicKey: String): String = withContext(Dispatchers.IO) {
            transaction(database) {
                val existingUser = Users
                    .selectAll().where { Users.publicKey eq publicKey }
                    .map { it[Users.id].toString() }
                    .firstOrNull()

                existingUser ?: Users.insert {
                    it[Users.publicKey] = publicKey
                }[Users.id].toString()
            }
        }
    }

}

