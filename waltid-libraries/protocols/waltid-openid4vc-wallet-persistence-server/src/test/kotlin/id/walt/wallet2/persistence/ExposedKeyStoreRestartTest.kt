package id.walt.wallet2.persistence

import id.walt.crypto.keys.KeySerialization
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.keys.KeyUsage
import id.walt.wallet2.data.WalletKeyStore
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ExposedKeyStoreRestartTest {
    @Test
    fun `local key dual-write survives restart and signs through crypto2`() = runTest {
        val db = database()
        val store = ExposedStoreRegistry(db).createKeyStore("keys")
        val keyId = store.addKey(JWKKey.generate(KeyType.secp256r1))
        assertNotNull(storedCrypto2Key(db, "keys", keyId))

        val restartedStore = ExposedKeyStore("keys", db)
        val crypto2Key = assertNotNull(restartedStore.getCrypto2Key(keyId))
        assertNotNull((restartedStore as WalletKeyStore).getCrypto2Key(keyId, setOf(KeyUsage.SIGN)))
        val signed = CompactJws.sign("restart".encodeToByteArray(), crypto2Key, JwsAlgorithm.ES256)

        assertEquals("restart", CompactJws.verify(signed, crypto2Key, JwsAlgorithm.ES256).payload.decodeToString())
        assertNotNull(restartedStore.getKey(keyId))
    }

    @Test
    fun `legacy row is backfilled once and malformed crypto2 does not downgrade`() = runTest {
        val db = database()
        ExposedStoreRegistry(db).createKeyStore("legacy")
        val legacyKey = JWKKey.generate(KeyType.secp256r1)
        val keyId = legacyKey.getKeyId()
        suspendTransaction(db) {
            Wallet2Tables.Keys.insert {
                it[Wallet2Tables.Keys.storeId] = "legacy"
                it[Wallet2Tables.Keys.keyId] = keyId
                it[Wallet2Tables.Keys.keyType] = legacyKey.keyType.name
                it[Wallet2Tables.Keys.serializedKey] = KeySerialization.serializeKey(legacyKey)
                it[Wallet2Tables.Keys.crypto2StoredKey] = null
            }
        }
        assertNull(storedCrypto2Key(db, "legacy", keyId))

        val store = ExposedKeyStore("legacy", db)
        assertNotNull(store.getCrypto2Key(keyId))
        assertNotNull(storedCrypto2Key(db, "legacy", keyId))

        val oldWriterReplacement = JWKKey.generate(KeyType.secp256r1)
        suspendTransaction(db) {
            Wallet2Tables.Keys.update({
                (Wallet2Tables.Keys.storeId eq "legacy") and (Wallet2Tables.Keys.keyId eq keyId)
            }) {
                it[Wallet2Tables.Keys.keyType] = oldWriterReplacement.keyType.name
                it[Wallet2Tables.Keys.serializedKey] = KeySerialization.serializeKey(oldWriterReplacement)
            }
        }
        val repairedKey = assertNotNull(store.getCrypto2Key(keyId))
        val replacementSignature = CompactJws.sign("{}".encodeToByteArray(), repairedKey, JwsAlgorithm.ES256)
        assertEquals(true, oldWriterReplacement.getPublicKey().verifyJws(replacementSignature).isSuccess)

        val publicReplacement = oldWriterReplacement.getPublicKey()
        suspendTransaction(db) {
            Wallet2Tables.Keys.update({
                (Wallet2Tables.Keys.storeId eq "legacy") and (Wallet2Tables.Keys.keyId eq keyId)
            }) {
                it[Wallet2Tables.Keys.serializedKey] = KeySerialization.serializeKey(publicReplacement)
            }
        }
        val repairedPublicKey = assertNotNull(store.getCrypto2Key(keyId))
        assertEquals(setOf(KeyUsage.VERIFY), repairedPublicKey.usages)
        assertFails { CompactJws.sign("{}".encodeToByteArray(), repairedPublicKey, JwsAlgorithm.ES256) }

        suspendTransaction(db) {
            Wallet2Tables.Keys.update({
                (Wallet2Tables.Keys.storeId eq "legacy") and (Wallet2Tables.Keys.keyId eq keyId)
            }) {
                it[Wallet2Tables.Keys.crypto2StoredKey] = "not-a-stored-key"
            }
        }
        assertFails { store.getCrypto2Key(keyId) }
        assertFails { store.getKey(keyId) }
    }

    private fun database() = initWallet2Database(
        Wallet2PersistenceConfig(
            jdbcUrl = "jdbc:sqlite::memory:",
            maximumPoolSize = 1,
            minimumIdle = 1,
        )
    )

    private suspend fun storedCrypto2Key(db: Database, storeId: String, keyId: String) =
        suspendTransaction(db) {
            Wallet2Tables.Keys.selectAll()
                .where { (Wallet2Tables.Keys.storeId eq storeId) and (Wallet2Tables.Keys.keyId eq keyId) }
                .single()[Wallet2Tables.Keys.crypto2StoredKey]
        }
}
