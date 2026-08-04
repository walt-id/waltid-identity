package id.walt.wallet2.persistence

import id.walt.crypto.keys.KeySerialization
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
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
        assertNotNull(restartedStore.getCrypto2Key(keyId, setOf(KeyUsage.SIGN)))
        val signed = CompactJws.sign("restart".encodeToByteArray(), crypto2Key, JwsAlgorithm.ES256)

        assertEquals("restart", CompactJws.verify(signed, crypto2Key, JwsAlgorithm.ES256).payload.decodeToString())
        assertNotNull(restartedStore.getKey(keyId))
    }

    @Test
    fun `crypto2-only key is persisted without a legacy representation and survives restart`() = runTest {
        val db = database()
        val store = ExposedStoreRegistry(db).createKeyStore("crypto2")
        val key = CryptoRuntime(defaultSoftwareKeyProviders()).generateSoftwareKey(
            GenerateSoftwareKeyRequest(
                id = KeyId("crypto2-only"),
                spec = KeySpec.Ec(EcCurve.P256),
                usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
            )
        )

        assertEquals("crypto2-only", store.addCrypto2Key(key))
        assertNull(serializedLegacyKey(db, "crypto2", "crypto2-only"))

        val restartedStore = ExposedKeyStore("crypto2", db)
        val restored = assertNotNull(restartedStore.getCrypto2Key("crypto2-only", setOf(KeyUsage.SIGN)))
        assertNull(restartedStore.getKey("crypto2-only"))
        assertNull(restartedStore.getKeyMaterial("crypto2-only")?.legacyKey)
        assertEquals(listOf("crypto2-only"), restartedStore.listKeysAsList().map { it.keyId })

        val signed = CompactJws.sign("crypto2".encodeToByteArray(), restored, JwsAlgorithm.ES256)
        assertEquals("crypto2", CompactJws.verify(signed, restored, JwsAlgorithm.ES256).payload.decodeToString())
    }

    @Test
    fun `pre-crypto2 row is migrated once and the descriptor stays canonical afterwards`() = runTest {
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
        val migrated = assertNotNull(store.getCrypto2Key(keyId))
        val adoptedDescriptor = assertNotNull(storedCrypto2Key(db, "legacy", keyId))
        assertEquals(setOf(KeyUsage.SIGN, KeyUsage.VERIFY), migrated.usages)

        // A legacy-only writer changing serialized_key must not alter the adopted descriptor: the
        // descriptor is the record of truth, the legacy column only feeds the one-time migration.
        val oldWriterReplacement = JWKKey.generate(KeyType.secp256r1)
        suspendTransaction(db) {
            Wallet2Tables.Keys.update({
                (Wallet2Tables.Keys.storeId eq "legacy") and (Wallet2Tables.Keys.keyId eq keyId)
            }) {
                it[Wallet2Tables.Keys.keyType] = oldWriterReplacement.keyType.name
                it[Wallet2Tables.Keys.serializedKey] = KeySerialization.serializeKey(oldWriterReplacement)
            }
        }
        val stillOriginal = assertNotNull(store.getCrypto2Key(keyId))
        assertEquals(adoptedDescriptor, storedCrypto2Key(db, "legacy", keyId))
        val signature = CompactJws.sign("{}".encodeToByteArray(), stillOriginal, JwsAlgorithm.ES256)
        assertEquals(true, legacyKey.getPublicKey().verifyJws(signature).isSuccess)
        assertEquals(false, oldWriterReplacement.getPublicKey().verifyJws(signature).isSuccess)
    }

    @Test
    fun `malformed descriptor fails instead of downgrading to the legacy key`() = runTest {
        val db = database()
        val store = ExposedStoreRegistry(db).createKeyStore("corrupt")
        val keyId = store.addKey(JWKKey.generate(KeyType.secp256r1))
        suspendTransaction(db) {
            Wallet2Tables.Keys.update({
                (Wallet2Tables.Keys.storeId eq "corrupt") and (Wallet2Tables.Keys.keyId eq keyId)
            }) {
                it[Wallet2Tables.Keys.crypto2StoredKey] = "not-a-stored-key"
            }
        }

        assertFails { store.getCrypto2Key(keyId) }
        assertFails { store.getKey(keyId) }
        assertFails { store.getKeyMaterial(keyId) }
    }

    @Test
    fun `public-only legacy row migrates to a verify-only descriptor`() = runTest {
        val db = database()
        ExposedStoreRegistry(db).createKeyStore("public")
        val publicKey = JWKKey.generate(KeyType.secp256r1).getPublicKey()
        val keyId = publicKey.getKeyId()
        suspendTransaction(db) {
            Wallet2Tables.Keys.insert {
                it[Wallet2Tables.Keys.storeId] = "public"
                it[Wallet2Tables.Keys.keyId] = keyId
                it[Wallet2Tables.Keys.keyType] = publicKey.keyType.name
                it[Wallet2Tables.Keys.serializedKey] = KeySerialization.serializeKey(publicKey)
                it[Wallet2Tables.Keys.crypto2StoredKey] = null
            }
        }

        val migrated = assertNotNull(ExposedKeyStore("public", db).getCrypto2Key(keyId))
        assertEquals(setOf(KeyUsage.VERIFY), migrated.usages)
        assertFails { CompactJws.sign("{}".encodeToByteArray(), migrated, JwsAlgorithm.ES256) }
    }

    private fun database() = initWallet2Database(
        Wallet2PersistenceConfig(
            jdbcUrl = "jdbc:sqlite::memory:",
            maximumPoolSize = 1,
            minimumIdle = 1,
        )
    )

    private suspend fun storedCrypto2Key(db: Database, storeId: String, keyId: String) =
        keyRow(db, storeId, keyId)[Wallet2Tables.Keys.crypto2StoredKey]

    private suspend fun serializedLegacyKey(db: Database, storeId: String, keyId: String) =
        keyRow(db, storeId, keyId)[Wallet2Tables.Keys.serializedKey]

    private suspend fun keyRow(db: Database, storeId: String, keyId: String) =
        suspendTransaction(db) {
            Wallet2Tables.Keys.selectAll()
                .where { (Wallet2Tables.Keys.storeId eq storeId) and (Wallet2Tables.Keys.keyId eq keyId) }
                .single()
        }
}
