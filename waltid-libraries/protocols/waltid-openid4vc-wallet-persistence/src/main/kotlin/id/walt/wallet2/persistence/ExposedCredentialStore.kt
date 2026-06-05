package id.walt.wallet2.persistence

import id.walt.credentials.formats.DigitalCredential
import id.walt.wallet2.data.StoredCredential
import id.walt.wallet2.data.WalletCredentialStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.upsert
import java.time.Instant
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant

/**
 * Exposed-backed [WalletCredentialStore].
 *
 * Credentials are serialized with `Json.encodeToString(DigitalCredential.serializer(), credential)`.
 * No re-parsing on load — the kotlinx.serialization JSON preserves the exact [DigitalCredential]
 * subtype including format-specific fields, disclosures, and parsed data.
 */
class ExposedCredentialStore(
    val storeId: String,
    private val db: Database,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : WalletCredentialStore {

    override suspend fun getCredential(id: String): StoredCredential? =
        suspendTransaction(db) {
            Wallet2Tables.Credentials.selectAll()
                .where { (Wallet2Tables.Credentials.storeId eq storeId) and (Wallet2Tables.Credentials.id eq id) }
                .firstOrNull()
                ?.let { rowToStoredCredential(it) }
        }

    override suspend fun listCredentials(): Flow<StoredCredential> =
        suspendTransaction(db) {
            Wallet2Tables.Credentials.selectAll()
                .where { Wallet2Tables.Credentials.storeId eq storeId }
                .mapNotNull { rowToStoredCredential(it) }
        }.asFlow()

    override suspend fun addCredential(entry: StoredCredential) {
        suspendTransaction(db) {
            Wallet2Tables.Credentials.upsert {
                it[Wallet2Tables.Credentials.storeId] = this@ExposedCredentialStore.storeId
                it[Wallet2Tables.Credentials.id] = entry.id
                it[Wallet2Tables.Credentials.serializedCredential] =
                    json.encodeToString(DigitalCredential.serializer(), entry.credential)
                it[Wallet2Tables.Credentials.label] = entry.label
                it[Wallet2Tables.Credentials.addedAt] =
                    entry.addedAt?.toJavaInstant() ?: Instant.now()
            }
        }
    }

    override suspend fun removeCredential(id: String): Boolean =
        suspendTransaction(db) {
            Wallet2Tables.Credentials.deleteWhere {
                (Wallet2Tables.Credentials.storeId eq storeId) and (Wallet2Tables.Credentials.id eq id)
            } > 0
        }

    private fun rowToStoredCredential(row: ResultRow): StoredCredential? =
        runCatching {
            StoredCredential(
                id = row[Wallet2Tables.Credentials.id],
                credential = json.decodeFromString(
                    DigitalCredential.serializer(),
                    row[Wallet2Tables.Credentials.serializedCredential]
                ),
                label = row[Wallet2Tables.Credentials.label],
                addedAt = row[Wallet2Tables.Credentials.addedAt].toKotlinInstant()
            )
        }.getOrNull()
}
