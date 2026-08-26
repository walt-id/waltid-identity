package id.walt.wallet2.persistence

import id.walt.crypto.keys.KeySerialization
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.credentials.formats.MdocsCredential
import id.walt.wallet2.data.HolderKeyBinding
import id.walt.wallet2.data.HolderKeyBindingOrigin
import id.walt.wallet2.data.PublicKeyThumbprint
import id.walt.wallet2.data.StoredCredential
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import java.sql.Connection
import java.sql.DriverManager
import kotlinx.serialization.json.buildJsonObject
import kotlin.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class Wallet2DatabaseMigrationTest {
    @Test
    fun `legacy credential table gains nullable holder key binding column`() = runTest {
        val jdbcUrl = sharedMemoryUrl("credential-binding-upgrade")
        DriverManager.getConnection(jdbcUrl).use { anchor ->
            anchor.createStatement().use { statement ->
                statement.execute("CREATE TABLE wallet2_credential_stores (id VARCHAR(128) PRIMARY KEY)")
                statement.execute(
                    """CREATE TABLE wallet2_credentials (
                        store_id VARCHAR(128) NOT NULL,
                        id VARCHAR(256) NOT NULL,
                        serialized_credential TEXT NOT NULL,
                        label VARCHAR(512),
                        added_at TIMESTAMP NOT NULL,
                        metadata TEXT,
                        PRIMARY KEY (store_id, id),
                        FOREIGN KEY (store_id) REFERENCES wallet2_credential_stores(id)
                    )""".trimIndent()
                )
                statement.execute("INSERT INTO wallet2_credential_stores(id) VALUES ('legacy')")
                statement.execute(
                    """INSERT INTO wallet2_credentials(
                        store_id, id, serialized_credential, label, added_at, metadata
                    ) VALUES ('legacy', 'mdoc-1', '{}', NULL, '2026-01-01T00:00:00Z', NULL)""".trimIndent()
                )
            }

            initWallet2Database(config(jdbcUrl))

            anchor.createStatement().use { statement ->
                statement.executeQuery("PRAGMA table_info(wallet2_credentials)").use { columns ->
                    val names = buildList {
                        while (columns.next()) add(columns.getString("name"))
                    }
                    assertTrue("holder_key_binding" in names)
                }
                statement.executeQuery(
                    "SELECT holder_key_binding FROM wallet2_credentials WHERE id = 'mdoc-1'"
                ).use { result ->
                    assertTrue(result.next())
                    assertEquals(null, result.getString(1))
                }
            }
        }
    }

    @Test
    fun `server credential store round trips holder key binding`() = runTest {
        val jdbcUrl = sharedMemoryUrl("credential-binding-roundtrip")
        DriverManager.getConnection(jdbcUrl).use { anchor ->
            val db = initWallet2Database(config(jdbcUrl))
            anchor.createStatement().use {
                it.execute("INSERT INTO wallet2_credential_stores(id) VALUES ('credentials')")
            }
            val binding = HolderKeyBinding(
                keyReference = "urn:waltid:wallet-key:v1:store:0:aG9sZGVy",
                publicKeyThumbprint = PublicKeyThumbprint(value = "thumbprint"),
                origin = HolderKeyBindingOrigin.ISSUANCE,
                createdAt = Instant.fromEpochMilliseconds(1_725_000_000_000),
            )
            val store = ExposedCredentialStore("credentials", db)

            store.addCredential(
                StoredCredential(
                    id = "mdoc-1",
                    credential = MdocsCredential(
                        credentialData = buildJsonObject { },
                        signed = null,
                        docType = "org.iso.18013.5.1.mDL",
                    ),
                    holderKeyBinding = binding,
                )
            )

            assertEquals(binding, store.getCredential("mdoc-1")?.holderKeyBinding)
        }
    }

    @Test
    fun `legacy key table gains crypto2 column and backfills`() = runTest {
        val jdbcUrl = sharedMemoryUrl("upgrade")
        val legacyKey = JWKKey.generate(KeyType.secp256r1)
        val keyId = legacyKey.getKeyId()
        DriverManager.getConnection(jdbcUrl).use { anchor ->
            anchor.createStatement().use { statement ->
                statement.execute("CREATE TABLE wallet2_key_stores (id VARCHAR(128) PRIMARY KEY)")
                statement.execute(
                    """CREATE TABLE wallet2_keys (
                        store_id VARCHAR(128) NOT NULL,
                        key_id VARCHAR(512) NOT NULL,
                        key_type VARCHAR(64) NOT NULL,
                        serialized_key TEXT NOT NULL,
                        PRIMARY KEY (store_id, key_id),
                        FOREIGN KEY (store_id) REFERENCES wallet2_key_stores(id)
                    )""".trimIndent()
                )
                statement.execute("INSERT INTO wallet2_key_stores(id) VALUES ('legacy')")
            }
            anchor.prepareStatement(
                "INSERT INTO wallet2_keys(store_id, key_id, key_type, serialized_key) VALUES (?, ?, ?, ?)"
            ).use { insert ->
                insert.setString(1, "legacy")
                insert.setString(2, keyId)
                insert.setString(3, legacyKey.keyType.name)
                insert.setString(4, KeySerialization.serializeKey(legacyKey))
                assertEquals(1, insert.executeUpdate())
            }

            val db = initWallet2Database(config(jdbcUrl))
            val store = ExposedKeyStore("legacy", db)
            assertNotNull(store.getCrypto2Key(keyId))

            // An upgraded pre-crypto2 table must also accept a crypto2-only key, which has no legacy
            // representation at all - i.e. serialized_key must no longer be NOT NULL.
            val crypto2Key = CryptoRuntime(defaultSoftwareKeyProviders()).generateSoftwareKey(
                GenerateSoftwareKeyRequest(
                    id = KeyId("crypto2-after-upgrade"),
                    spec = KeySpec.Ec(EcCurve.P256),
                    usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
                )
            )
            assertEquals("crypto2-after-upgrade", store.addCrypto2Key(crypto2Key))
            assertNotNull(store.getCrypto2Key("crypto2-after-upgrade", setOf(KeyUsage.SIGN)))
        }
    }

    @Test
    fun `legacy SQLite wallet table gains crypto2 static key column and backfills`() = runTest {
        val jdbcUrl = sharedMemoryUrl("static-upgrade")
        DriverManager.getConnection(jdbcUrl).use { anchor ->
            createLegacyWallet(anchor, "sqlite-legacy")

            val db = initWallet2Database(config(jdbcUrl))
            assertNotNull(ExposedWalletStore(db).loadDescriptor("sqlite-legacy")?.crypto2StaticKey)
            anchor.createStatement().use { statement ->
                statement.executeQuery(
                    "SELECT crypto2_static_key FROM wallet2_wallets WHERE id = 'sqlite-legacy'"
                ).use { result ->
                    assertTrue(result.next())
                    assertNotNull(result.getString(1))
                }
            }
        }
    }

    @Test
    fun `legacy PostgreSQL wallet table gains crypto2 static key column and backfills`() = runTest {
        val baseUrl = System.getenv("WALLET2_TEST_POSTGRES_JDBC_URL") ?: return@runTest
        val username = System.getenv("WALLET2_TEST_POSTGRES_USERNAME").orEmpty()
        val password = System.getenv("WALLET2_TEST_POSTGRES_PASSWORD").orEmpty()
        val schema = "wallet2_static_${Uuid.random().toString().replace("-", "")}"
        DriverManager.getConnection(baseUrl, username, password).use { connection ->
            connection.createStatement().use { it.execute("CREATE SCHEMA $schema") }
        }
        val separator = if ('?' in baseUrl) '&' else '?'
        val jdbcUrl = "$baseUrl${separator}currentSchema=$schema"
        try {
            DriverManager.getConnection(jdbcUrl, username, password).use { connection ->
                createLegacyWallet(connection, "postgres-legacy")
            }
            val db = initWallet2Database(
                config(jdbcUrl).copy(
                    driverClassName = "org.postgresql.Driver",
                    username = username,
                    password = password,
                )
            )
            assertNotNull(ExposedWalletStore(db).loadDescriptor("postgres-legacy")?.crypto2StaticKey)
            DriverManager.getConnection(jdbcUrl, username, password).use { connection ->
                connection.createStatement().use { statement ->
                    statement.executeQuery(
                        "SELECT crypto2_static_key FROM wallet2_wallets WHERE id = 'postgres-legacy'"
                    ).use { result ->
                        assertTrue(result.next())
                        assertNotNull(result.getString(1))
                    }
                }
            }
        } finally {
            DriverManager.getConnection(baseUrl, username, password).use { connection ->
                connection.createStatement().use { it.execute("DROP SCHEMA $schema CASCADE") }
            }
        }
    }

    @Test
    fun `concurrent startup serializes schema migration`() = runTest {
        val jdbcUrl = sharedMemoryUrl("concurrent")
        DriverManager.getConnection(jdbcUrl).use {
            coroutineScope {
                listOf(
                    async(Dispatchers.IO) { initWallet2Database(config(jdbcUrl)) },
                    async(Dispatchers.IO) { initWallet2Database(config(jdbcUrl)) },
                ).awaitAll()
            }
        }
    }

    private fun config(jdbcUrl: String) = Wallet2PersistenceConfig(
        jdbcUrl = jdbcUrl,
        maximumPoolSize = 1,
        minimumIdle = 1,
    )

    private fun sharedMemoryUrl(label: String) =
        "jdbc:sqlite:file:wallet2-$label-${Uuid.random()}?mode=memory&cache=shared"

    private suspend fun createLegacyWallet(connection: Connection, walletId: String) {
        val key = JWKKey.generate(KeyType.secp256r1)
        connection.createStatement().use { statement ->
            statement.execute(
                """CREATE TABLE wallet2_wallets (
                    id VARCHAR(128) PRIMARY KEY,
                    static_key TEXT,
                    static_did VARCHAR(1024),
                    default_key_id VARCHAR(512),
                    default_did_id VARCHAR(1024)
                )""".trimIndent()
            )
        }
        connection.prepareStatement("INSERT INTO wallet2_wallets(id, static_key) VALUES (?, ?)").use { insert ->
            insert.setString(1, walletId)
            insert.setString(2, KeySerialization.serializeKey(key))
            assertEquals(1, insert.executeUpdate())
        }
    }
}
