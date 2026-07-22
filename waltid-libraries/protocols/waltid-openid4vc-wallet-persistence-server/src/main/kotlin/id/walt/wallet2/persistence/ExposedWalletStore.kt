package id.walt.wallet2.persistence

import id.walt.crypto.keys.KeyManager
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.jose.Jwk
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.StoredKey
import id.walt.crypto2.keys.Key as Crypto2Key
import id.walt.crypto2.keys.toPublicJwk
import id.walt.crypto2.migration.v1.V1KeyMigration
import id.walt.crypto2.providers.cryptography.CryptographySoftwareKeyProvider
import id.walt.crypto2.serialization.StoredKeyCodec
import id.walt.wallet2.data.WalletDescriptor
import id.walt.wallet2.stores.WalletStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.upsert

/**
 * Exposed-backed [WalletStore].
 *
 * Persists [WalletDescriptor] across the wallet, junction, and store-registration tables.
 * Also implements [id.walt.wallet2.stores.WalletAccountMapping] for account-ownership tracking.
 */
class ExposedWalletStore(private val db: Database) : WalletStore {
    private val crypto2Runtime = CryptoRuntime(listOf(CryptographySoftwareKeyProvider()))
    private val migration = V1KeyMigration()

    override suspend fun loadDescriptor(walletId: String): WalletDescriptor? =
        suspendTransaction(db) {
            val walletRow = Wallet2Tables.Wallets.selectAll()
                .where { Wallet2Tables.Wallets.id eq walletId }
                .firstOrNull() ?: return@suspendTransaction null

            val keyStoreIds = Wallet2Tables.WalletKeyStores.selectAll()
                .where { Wallet2Tables.WalletKeyStores.walletId eq walletId }
                .orderBy(Wallet2Tables.WalletKeyStores.position)
                .map { it[Wallet2Tables.WalletKeyStores.storeId] }

            val credentialStoreIds = Wallet2Tables.WalletCredentialStores.selectAll()
                .where { Wallet2Tables.WalletCredentialStores.walletId eq walletId }
                .orderBy(Wallet2Tables.WalletCredentialStores.position)
                .map { it[Wallet2Tables.WalletCredentialStores.storeId] }

            val didStoreId = Wallet2Tables.WalletDidStores.selectAll()
                .where { Wallet2Tables.WalletDidStores.walletId eq walletId }
                .firstOrNull()
                ?.get(Wallet2Tables.WalletDidStores.storeId)

            WalletDescriptor(
                id = walletId,
                keyStoreIds = keyStoreIds,
                credentialStoreIds = credentialStoreIds,
                didStoreId = didStoreId,
                serializedStaticKey = walletRow[Wallet2Tables.Wallets.serializedStaticKey],
                staticDid = walletRow[Wallet2Tables.Wallets.staticDid],
                defaultKeyId = walletRow[Wallet2Tables.Wallets.defaultKeyId],
                defaultDidId = walletRow[Wallet2Tables.Wallets.defaultDidId],
                crypto2StaticKey = resolveCrypto2StaticKey(walletRow),
            )
        }

    override suspend fun saveDescriptor(descriptor: WalletDescriptor) {
        val crypto2StaticKey = staticKeyForWrite(descriptor)
        suspendTransaction(db) {
            // Upsert wallet row
            Wallet2Tables.Wallets.upsert {
                it[Wallet2Tables.Wallets.id] = descriptor.id
                it[Wallet2Tables.Wallets.serializedStaticKey] = descriptor.serializedStaticKey
                it[Wallet2Tables.Wallets.crypto2StaticKey] = crypto2StaticKey?.let(StoredKeyCodec::encodeToString)
                it[Wallet2Tables.Wallets.staticDid] = descriptor.staticDid
                it[Wallet2Tables.Wallets.defaultKeyId] = descriptor.defaultKeyId
                it[Wallet2Tables.Wallets.defaultDidId] = descriptor.defaultDidId
            }

            // Ensure named store rows exist
            descriptor.keyStoreIds.forEach { storeId ->
                Wallet2Tables.KeyStores.upsert { it[Wallet2Tables.KeyStores.id] = storeId }
            }
            descriptor.credentialStoreIds.forEach { storeId ->
                Wallet2Tables.CredentialStores.upsert { it[Wallet2Tables.CredentialStores.id] = storeId }
            }
            descriptor.didStoreId?.let { storeId ->
                Wallet2Tables.DidStores.upsert { it[Wallet2Tables.DidStores.id] = storeId }
            }

            // Replace junction rows (delete + insert to maintain order)
            Wallet2Tables.WalletKeyStores.deleteWhere { Wallet2Tables.WalletKeyStores.walletId eq descriptor.id }
            descriptor.keyStoreIds.forEachIndexed { pos, storeId ->
                Wallet2Tables.WalletKeyStores.insert {
                    it[Wallet2Tables.WalletKeyStores.walletId] = descriptor.id
                    it[Wallet2Tables.WalletKeyStores.storeId] = storeId
                    it[Wallet2Tables.WalletKeyStores.position] = pos
                }
            }

            Wallet2Tables.WalletCredentialStores.deleteWhere { Wallet2Tables.WalletCredentialStores.walletId eq descriptor.id }
            descriptor.credentialStoreIds.forEachIndexed { pos, storeId ->
                Wallet2Tables.WalletCredentialStores.insert {
                    it[Wallet2Tables.WalletCredentialStores.walletId] = descriptor.id
                    it[Wallet2Tables.WalletCredentialStores.storeId] = storeId
                    it[Wallet2Tables.WalletCredentialStores.position] = pos
                }
            }

            Wallet2Tables.WalletDidStores.deleteWhere { Wallet2Tables.WalletDidStores.walletId eq descriptor.id }
            descriptor.didStoreId?.let { storeId ->
                Wallet2Tables.WalletDidStores.insert {
                    it[Wallet2Tables.WalletDidStores.walletId] = descriptor.id
                    it[Wallet2Tables.WalletDidStores.storeId] = storeId
                }
            }
        }
    }

    override suspend fun deleteWallet(walletId: String) {
        suspendTransaction(db) {
            val keyStoreIds = Wallet2Tables.WalletKeyStores.selectAll()
                .where { Wallet2Tables.WalletKeyStores.walletId eq walletId }
                .map { it[Wallet2Tables.WalletKeyStores.storeId] }
            val credentialStoreIds = Wallet2Tables.WalletCredentialStores.selectAll()
                .where { Wallet2Tables.WalletCredentialStores.walletId eq walletId }
                .map { it[Wallet2Tables.WalletCredentialStores.storeId] }
            val didStoreIds = Wallet2Tables.WalletDidStores.selectAll()
                .where { Wallet2Tables.WalletDidStores.walletId eq walletId }
                .map { it[Wallet2Tables.WalletDidStores.storeId] }
            Wallet2Tables.WalletKeyStores.deleteWhere { Wallet2Tables.WalletKeyStores.walletId eq walletId }
            Wallet2Tables.WalletCredentialStores.deleteWhere { Wallet2Tables.WalletCredentialStores.walletId eq walletId }
            Wallet2Tables.WalletDidStores.deleteWhere { Wallet2Tables.WalletDidStores.walletId eq walletId }
            Wallet2Tables.AccountWallets.deleteWhere { Wallet2Tables.AccountWallets.walletId eq walletId }
            Wallet2Tables.Wallets.deleteWhere { Wallet2Tables.Wallets.id eq walletId }

            keyStoreIds.forEach { storeId ->
                if (Wallet2Tables.WalletKeyStores.selectAll().where { Wallet2Tables.WalletKeyStores.storeId eq storeId }.none()) {
                    Wallet2Tables.Keys.deleteWhere { Wallet2Tables.Keys.storeId eq storeId }
                    Wallet2Tables.KeyStores.deleteWhere { Wallet2Tables.KeyStores.id eq storeId }
                }
            }
            credentialStoreIds.forEach { storeId ->
                if (Wallet2Tables.WalletCredentialStores.selectAll()
                        .where { Wallet2Tables.WalletCredentialStores.storeId eq storeId }.none()
                ) {
                    Wallet2Tables.Credentials.deleteWhere { Wallet2Tables.Credentials.storeId eq storeId }
                    Wallet2Tables.CredentialStores.deleteWhere { Wallet2Tables.CredentialStores.id eq storeId }
                }
            }
            didStoreIds.forEach { storeId ->
                if (Wallet2Tables.WalletDidStores.selectAll().where { Wallet2Tables.WalletDidStores.storeId eq storeId }.none()) {
                    Wallet2Tables.Dids.deleteWhere { Wallet2Tables.Dids.storeId eq storeId }
                    Wallet2Tables.DidStores.deleteWhere { Wallet2Tables.DidStores.id eq storeId }
                }
            }
        }
    }

    override suspend fun listWalletIds(): Flow<String> =
        suspendTransaction(db) {
            Wallet2Tables.Wallets.selectAll().map { it[Wallet2Tables.Wallets.id] }
        }.asFlow()

    override suspend fun linkWalletToAccount(accountId: String, walletId: String) {
        suspendTransaction(db) {
            Wallet2Tables.AccountWallets.upsert {
                it[Wallet2Tables.AccountWallets.accountId] = accountId
                it[Wallet2Tables.AccountWallets.walletId] = walletId
            }
        }
    }

    override suspend fun getWalletIdsForAccount(accountId: String): List<String>? =
        suspendTransaction(db) {
            Wallet2Tables.AccountWallets.selectAll()
                .where { Wallet2Tables.AccountWallets.accountId eq accountId }
                .map { it[Wallet2Tables.AccountWallets.walletId] }
        }

    private suspend fun staticKeyForWrite(descriptor: WalletDescriptor): StoredKey? {
        val serializedStaticKey = descriptor.serializedStaticKey
        if (serializedStaticKey == null) {
            require(descriptor.crypto2StaticKey == null) { "A crypto2 static key requires serialized legacy key material" }
            return null
        }
        val expected = migrateStaticKey(serializedStaticKey)
        val supplied = descriptor.crypto2StaticKey ?: return expected?.stored
        val restored = restoreAndValidate(supplied)
        requireNotNull(expected) { "The supplied crypto2 static key has no supported legacy counterpart" }
        require(sidecarMatchesLegacy(supplied, restored, expected)) {
            "The supplied crypto2 static key does not match the serialized legacy key"
        }
        return supplied
    }

    private suspend fun resolveCrypto2StaticKey(row: ResultRow): StoredKey? {
        val walletId = row[Wallet2Tables.Wallets.id]
        val serializedStaticKey = row[Wallet2Tables.Wallets.serializedStaticKey]
        val persistedEncoded = row[Wallet2Tables.Wallets.crypto2StaticKey]
        if (persistedEncoded == null) {
            val expected = serializedStaticKey?.let { migrateStaticKey(it) } ?: return null
            val expectedEncoded = StoredKeyCodec.encodeToString(expected.stored)
            check(updateCrypto2StaticKey(walletId, serializedStaticKey, null, expectedEncoded) == 1) {
                "Wallet changed while its crypto2 static key was being backfilled"
            }
            return expected.stored
        }

        // Decode and restore before consulting legacy material: malformed sidecars never downgrade.
        val persisted = StoredKeyCodec.decodeFromString(persistedEncoded)
        val restored = restoreAndValidate(persisted)
        val expected = requireNotNull(serializedStaticKey?.let { migrateStaticKey(it) }) {
            "Persisted crypto2 static key has no supported legacy counterpart"
        }
        if (!sidecarMatchesLegacy(persisted, restored, expected)) {
            val expectedEncoded = StoredKeyCodec.encodeToString(expected.stored)
            check(updateCrypto2StaticKey(walletId, serializedStaticKey, persistedEncoded, expectedEncoded) == 1) {
                "Wallet changed while its stale crypto2 static key was being replaced"
            }
            return expected.stored
        }
        return persisted
    }

    private suspend fun migrateStaticKey(serializedStaticKey: String): MigratedStaticKey? {
        val serialized = try {
            Json.parseToJsonElement(serializedStaticKey).jsonObject
        } catch (cause: CancellationException) {
            throw cause
        } catch (_: Exception) {
            return null
        }
        if (serialized["type"]?.jsonPrimitive?.content != "jwk") return null
        return try {
            val legacyKey = KeyManager.resolveSerializedKey(serializedStaticKey)
            val usages = if (legacyKey.hasPrivateKey) {
                setOf(KeyUsage.SIGN, KeyUsage.VERIFY)
            } else {
                setOf(KeyUsage.VERIFY)
            }
            val stored = migration.migrate(KeyId(legacyKey.getKeyId()), serialized, usages)
            MigratedStaticKey(stored, restoreAndValidate(stored))
        } catch (cause: CancellationException) {
            throw cause
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun restoreAndValidate(stored: StoredKey): Crypto2Key = crypto2Runtime.restore(stored).also { key ->
        require(key.id == stored.id) { "Restored crypto2 static key ID changed" }
        require(key.spec == stored.spec) { "Restored crypto2 static key specification changed" }
        require(key.usages == stored.usages) { "Restored crypto2 static key usages changed" }
    }

    private suspend fun sidecarMatchesLegacy(
        persisted: StoredKey,
        restored: Crypto2Key,
        expected: MigratedStaticKey,
    ): Boolean = persisted.id == expected.stored.id &&
        persisted.spec == expected.stored.spec &&
        persisted.usages == expected.stored.usages &&
        publicThumbprint(restored) == publicThumbprint(expected.key)

    private suspend fun publicThumbprint(key: Crypto2Key): String {
        val publicKey = requireNotNull(key.capabilities.publicKeyExporter) {
            "Wallet crypto2 static key does not export public material"
        }.exportPublicKey().toPublicJwk(key.spec)
        return Jwk.sha256Thumbprint(publicKey)
    }

    private fun updateCrypto2StaticKey(
        walletId: String,
        serializedStaticKey: String,
        currentCrypto2StaticKey: String?,
        replacement: String,
    ): Int {
        val crypto2Condition = currentCrypto2StaticKey?.let { Wallet2Tables.Wallets.crypto2StaticKey eq it }
            ?: Wallet2Tables.Wallets.crypto2StaticKey.isNull()
        return Wallet2Tables.Wallets.update({
            (Wallet2Tables.Wallets.id eq walletId) and
                (Wallet2Tables.Wallets.serializedStaticKey eq serializedStaticKey) and
                crypto2Condition
        }) {
            it[Wallet2Tables.Wallets.crypto2StaticKey] = replacement
        }
    }

    private data class MigratedStaticKey(
        val stored: StoredKey,
        val key: Crypto2Key,
    )
}
