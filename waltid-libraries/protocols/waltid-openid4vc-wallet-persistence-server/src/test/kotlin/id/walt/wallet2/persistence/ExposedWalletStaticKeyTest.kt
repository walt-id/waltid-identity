package id.walt.wallet2.persistence

import id.walt.crypto.keys.KeyManager
import id.walt.crypto.keys.KeySerialization
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.StoredKey
import id.walt.crypto2.migration.v1.V1KeyMigration
import id.walt.crypto2.serialization.BinaryData
import id.walt.crypto2.serialization.StoredKeyCodec
import id.walt.wallet2.data.WalletDescriptor
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExposedWalletStaticKeyTest {
    @Test
    fun `new static key writes both formats and remains readable by an old reader`() = runTest {
        val db = database()
        val key = JWKKey.generate(KeyType.secp256r1)
        val serialized = KeySerialization.serializeKey(key)
        ExposedWalletStore(db).saveDescriptor(WalletDescriptor(id = "new-writer", serializedStaticKey = serialized))

        val row = staticKeyRow(db, "new-writer")
        assertEquals(serialized, row.serialized)
        val stored = StoredKeyCodec.decodeFromString(assertNotNull(row.crypto2))
        assertEquals(KeyId(key.getKeyId()), stored.id)
        assertEquals(setOf(KeyUsage.SIGN, KeyUsage.VERIFY), stored.usages)

        // A rolling old instance still consumes only static_key.
        assertEquals(key.getKeyId(), KeyManager.resolveSerializedKey(row.serialized).getKeyId())
        assertEquals(stored, ExposedWalletStore(db).loadDescriptor("new-writer")?.crypto2StaticKey)
    }

    @Test
    fun `old static key row is lazily backfilled and survives store recreation`() = runTest {
        val db = database()
        val key = JWKKey.generate(KeyType.Ed25519)
        val serialized = KeySerialization.serializeKey(key)
        suspendTransaction(db) {
            Wallet2Tables.Wallets.insert {
                it[id] = "old-writer"
                it[serializedStaticKey] = serialized
                it[crypto2StaticKey] = null
            }
        }
        assertNull(staticKeyRow(db, "old-writer").crypto2)

        val first = assertNotNull(ExposedWalletStore(db).loadDescriptor("old-writer")?.crypto2StaticKey)
        assertNotNull(staticKeyRow(db, "old-writer").crypto2)
        assertEquals(first, ExposedWalletStore(db).loadDescriptor("old-writer")?.crypto2StaticKey)
    }

    @Test
    fun `malformed sidecar fails without legacy downgrade`() = runTest {
        val db = database()
        val key = JWKKey.generate(KeyType.Ed25519)
        val store = ExposedWalletStore(db)
        store.saveDescriptor(
            WalletDescriptor(id = "malformed", serializedStaticKey = KeySerialization.serializeKey(key))
        )
        setCrypto2StaticKey(db, "malformed", "{malformed")

        assertFails { store.loadDescriptor("malformed") }
        assertEquals("{malformed", staticKeyRow(db, "malformed").crypto2)
    }

    @Test
    fun `old writer replacement leaves compatibility key authoritative and repairs sidecar`() = runTest {
        val db = database()
        val original = JWKKey.generate(KeyType.secp256r1)
        val replacement = JWKKey.generate(KeyType.secp256r1)
        val store = ExposedWalletStore(db)
        store.saveDescriptor(
            WalletDescriptor(id = "rolling-update", serializedStaticKey = KeySerialization.serializeKey(original))
        )
        val originalSidecar = assertNotNull(staticKeyRow(db, "rolling-update").crypto2)
        val replacementSerialized = KeySerialization.serializeKey(replacement)
        suspendTransaction(db) {
            Wallet2Tables.Wallets.update({ Wallet2Tables.Wallets.id eq "rolling-update" }) {
                it[serializedStaticKey] = replacementSerialized
            }
        }

        val repaired = assertNotNull(store.loadDescriptor("rolling-update")?.crypto2StaticKey)

        assertEquals(KeyId(replacement.getKeyId()), repaired.id)
        assertEquals(replacementSerialized, staticKeyRow(db, "rolling-update").serialized)
        assertNotEquals(originalSidecar, assertNotNull(staticKeyRow(db, "rolling-update").crypto2))
    }

    @Test
    fun `sidecar with mismatched private d fails without downgrade or repair`() = runTest {
        val db = database()
        val key = JWKKey.generate(KeyType.secp256r1)
        val replacement = JWKKey.generate(KeyType.secp256r1)
        val store = ExposedWalletStore(db)
        store.saveDescriptor(
            WalletDescriptor(id = "mismatched-private", serializedStaticKey = KeySerialization.serializeKey(key))
        )
        val row = staticKeyRow(db, "mismatched-private")
        val stored = assertIs<StoredKey.Software>(StoredKeyCodec.decodeFromString(assertNotNull(row.crypto2)))
        val material = assertIs<EncodedKey.Jwk>(stored.material)
        val jwk = Json.parseToJsonElement(material.data.toByteArray().decodeToString()).jsonObject
        val replacementJwk = KeySerialization.serializeKeyToJson(replacement).jsonObject["jwk"]!!.jsonObject
        val inconsistent = stored.copy(
            material = material.copy(
                data = BinaryData(JsonObject(jwk + ("d" to replacementJwk.getValue("d"))).toString().encodeToByteArray())
            )
        )
        val inconsistentEncoded = StoredKeyCodec.encodeToString(inconsistent)
        setCrypto2StaticKey(db, "mismatched-private", inconsistentEncoded)

        val failure = assertFailsWith<IllegalArgumentException> { store.loadDescriptor("mismatched-private") }
        val message = failure.message.orEmpty().lowercase()
        assertTrue("private" in message && "public" in message)
        assertEquals(inconsistentEncoded, staticKeyRow(db, "mismatched-private").crypto2)
        assertEquals(key.getKeyId(), KeyManager.resolveSerializedKey(row.serialized).getKeyId())
    }

    @Test
    fun `old writer stale sidecars are repaired after every identity check`() = runTest {
        val db = database()
        val key = JWKKey.generate(KeyType.secp256r1)
        val store = ExposedWalletStore(db)
        store.saveDescriptor(WalletDescriptor(id = "stale", serializedStaticKey = KeySerialization.serializeKey(key)))
        val expectedEncoded = assertNotNull(staticKeyRow(db, "stale").crypto2)
        val expected = assertIs<StoredKey.Software>(StoredKeyCodec.decodeFromString(expectedEncoded))
        val replacement = JWKKey.generate(KeyType.secp256r1)
        val otherSpec = JWKKey.generate(KeyType.Ed25519)
        val migration = V1KeyMigration()
        val staleSidecars = mapOf(
            "id" to expected.copy(id = KeyId("stale-id")),
            "spec" to migration.migrate(
                KeyId(key.getKeyId()),
                KeySerialization.serializeKeyToJson(otherSpec).jsonObject,
                expected.usages,
            ),
            "public thumbprint" to migration.migrate(
                KeyId(key.getKeyId()),
                KeySerialization.serializeKeyToJson(replacement).jsonObject,
                expected.usages,
            ),
            "usages" to migration.migrate(
                KeyId(key.getKeyId()),
                KeySerialization.serializeKeyToJson(key.getPublicKey()).jsonObject,
                setOf(KeyUsage.VERIFY),
            ),
            "usage superset" to migration.migrate(
                KeyId(key.getKeyId()),
                KeySerialization.serializeKeyToJson(key).jsonObject,
                expected.usages + KeyUsage.KEY_AGREEMENT,
            ),
        )

        staleSidecars.forEach { (field, stale) ->
            setCrypto2StaticKey(db, "stale", StoredKeyCodec.encodeToString(stale))

            assertEquals(expected, store.loadDescriptor("stale")?.crypto2StaticKey, "Failed to repair $field")
            assertEquals(expectedEncoded, staticKeyRow(db, "stale").crypto2, "Failed to persist $field repair")
        }
    }

    private fun database() = initWallet2Database(
        Wallet2PersistenceConfig(
            jdbcUrl = "jdbc:sqlite::memory:",
            maximumPoolSize = 1,
            minimumIdle = 1,
        )
    )

    private suspend fun staticKeyRow(db: Database, walletId: String): StaticKeyRow = suspendTransaction(db) {
        Wallet2Tables.Wallets.selectAll()
            .where { Wallet2Tables.Wallets.id eq walletId }
            .single()
            .let { row ->
                StaticKeyRow(
                    serialized = assertNotNull(row[Wallet2Tables.Wallets.serializedStaticKey]),
                    crypto2 = row[Wallet2Tables.Wallets.crypto2StaticKey],
                )
            }
    }

    private suspend fun setCrypto2StaticKey(db: Database, walletId: String, encoded: String) {
        suspendTransaction(db) {
            Wallet2Tables.Wallets.update({ Wallet2Tables.Wallets.id eq walletId }) {
                it[crypto2StaticKey] = encoded
            }
        }
    }

    private data class StaticKeyRow(val serialized: String, val crypto2: String?)
}
