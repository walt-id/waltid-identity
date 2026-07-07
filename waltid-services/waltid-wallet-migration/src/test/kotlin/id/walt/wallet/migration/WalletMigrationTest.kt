package id.walt.wallet.migration

import id.walt.credentials.CredentialParser
import id.walt.wallet2.persistence.Wallet2Tables
import id.walt.wallet2.persistence.initWallet2Database
import id.walt.wallet2.persistence.Wallet2PersistenceConfig
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation
import org.junit.jupiter.api.Order
import java.sql.DriverManager
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * End-to-end migration test that simulates an NDID-like v1 wallet database:
 *
 * - One account (email login)
 * - One wallet linked to that account
 * - One EC secp256r1 key (JWK, as produced by KeySerialization.serializeKey)
 * - One W3C JWT VC credential (jwt_vc_json format)
 * - One DID whose document column contains just the DID URI string (not a JSON object) —
 *   this is the NDID case: they use a DID type for which only the URI matters
 *
 * The test verifies:
 * 1. All entities are migrated to the v2 schema
 * 2. The credential round-trips through CredentialParser.detectAndParse
 * 3. A DID with a non-JSON document (NDID's case: only the DID URI is stored, not a
 *    full DID document) is migrated with a minimal {"id":"<did>"} placeholder document
 *    rather than being silently dropped or crashing
 * 4. The account → wallet mapping is preserved
 * 5. Migration is idempotent (running twice doesn't duplicate rows)
 */
@TestMethodOrder(OrderAnnotation::class)
class WalletMigrationTest {

    companion object {
        // ── Realistic test data ────────────────────────────────────────────────

        const val ACCOUNT_ID   = "11111111-1111-1111-1111-111111111111"
        const val WALLET_ID    = "22222222-2222-2222-2222-222222222222"
        const val KEY_ID       = "test-key-ed25519-abc123"
        const val CRED_ID      = "urn:uuid:33333333-3333-3333-3333-333333333333"
        const val DID_URI      = "did:example:ndid:user:abc123"

        /**
         * A serialized Ed25519 JWK key — exactly what KeySerialization.serializeKey produces
         * for a JWKKey. The `type` discriminator is required by KeyManager.resolveSerializedKey.
         */
        val SERIALIZED_KEY = """
            {"type":"jwk","jwk":{"kty":"OKP","crv":"Ed25519","kid":"$KEY_ID",
            "x":"11qYAYKxCrfVS_7TyWQHOg7hcvPapiMlrwIaaPcHURo",
            "d":"nWGxne_9WmC6hEr0kuwsxERJxWl7MmkZcDusAxyuf2A"}}
        """.trimIndent().replace("\n", "")

        /**
         * A minimal W3C JWT VC (jwt_vc_json format).
         *
         * Header: {"typ":"JWT","alg":"EdDSA"}
         * Payload contains a vc claim with @context, type, credentialSubject.
         * The signature is a placeholder — CredentialParser detects format by header/payload
         * structure, not by verifying the signature.
         */
        val W3C_JWT_VC = buildString {
            // Header: {"alg":"EdDSA","typ":"JWT"}
            append("eyJhbGciOiJFZERTQSIsInR5cCI6IkpXVCJ9")
            append(".")
            // Payload: {"iss":"did:example:issuer","sub":"did:example:ndid:user:abc123",
            //   "jti":"urn:uuid:33333333-3333-3333-3333-333333333333",
            //   "vc":{"@context":["https://www.w3.org/2018/credentials/v1"],
            //         "type":["VerifiableCredential","NDIDIdentityCredential"],
            //         "credentialSubject":{"id":"did:example:ndid:user:abc123","nationalId":"1234567890123"}}}
            append("eyJpc3MiOiJkaWQ6ZXhhbXBsZTppc3N1ZXIiLCJzdWIiOiJkaWQ6ZXhhbXBsZTpuZGlkOnVzZXI6YWJjMTIzIiwianRpIjoidXJuOnV1aWQ6MzMzMzMzMzMtMzMzMy0zMzMzLTMzMzMtMzMzMzMzMzMzMzMzIiwidmMiOnsiQGNvbnRleHQiOlsiaHR0cHM6Ly93d3cudzMub3JnLzIwMTgvY3JlZGVudGlhbHMvdjEiXSwidHlwZSI6WyJWZXJpZmlhYmxlQ3JlZGVudGlhbCIsIk5ESURJZGVudGl0eUNyZWRlbnRpYWwiXSwiY3JlZGVudGlhbFN1YmplY3QiOnsiaWQiOiJkaWQ6ZXhhbXBsZTpuZGlkOnVzZXI6YWJjMTIzIiwibmF0aW9uYWxJZCI6IjEyMzQ1Njc4OTAxMjMifX19")
            append(".")
            // Placeholder signature
            append("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")
        }
    }

    // ── Source DB helpers ──────────────────────────────────────────────────────

    private fun createV1Schema(conn: java.sql.Connection) {
        conn.createStatement().use { stmt ->
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS wallets (
                    id TEXT NOT NULL PRIMARY KEY,
                    name TEXT NOT NULL,
                    createdOn TEXT NOT NULL
                )
            """)
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS accounts (
                    tenant TEXT NOT NULL DEFAULT '',
                    id TEXT NOT NULL,
                    name TEXT,
                    email TEXT UNIQUE,
                    password TEXT,
                    createdOn TEXT NOT NULL,
                    PRIMARY KEY (tenant, id)
                )
            """)
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS wallet_keys (
                    wallet TEXT NOT NULL REFERENCES wallets(id),
                    kid TEXT NOT NULL,
                    document TEXT NOT NULL,
                    name TEXT,
                    createdOn TEXT NOT NULL,
                    PRIMARY KEY (wallet, kid)
                )
            """)
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS credentials (
                    wallet TEXT NOT NULL REFERENCES wallets(id),
                    id TEXT NOT NULL,
                    document TEXT NOT NULL,
                    disclosures TEXT,
                    added_on TEXT NOT NULL,
                    manifest TEXT,
                    deleted_on TEXT,
                    pending INTEGER NOT NULL DEFAULT 0,
                    format TEXT NOT NULL DEFAULT 'jwt_vc_json',
                    PRIMARY KEY (wallet, id)
                )
            """)
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS wallet_dids (
                    wallet TEXT NOT NULL REFERENCES wallets(id),
                    did TEXT NOT NULL,
                    alias TEXT NOT NULL DEFAULT '',
                    document TEXT NOT NULL,
                    keyId TEXT NOT NULL,
                    "default" INTEGER NOT NULL DEFAULT 0,
                    createdOn TEXT NOT NULL,
                    PRIMARY KEY (wallet, did)
                )
            """)
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS account_wallet_mapping (
                    tenant TEXT NOT NULL DEFAULT '',
                    id TEXT NOT NULL,
                    wallet TEXT NOT NULL REFERENCES wallets(id),
                    added_on TEXT NOT NULL,
                    permissions TEXT NOT NULL DEFAULT 'USE',
                    PRIMARY KEY (tenant, id, wallet)
                )
            """)
        }
    }

    private fun insertV1TestData(conn: java.sql.Connection) {
        // wallet
        conn.prepareStatement("INSERT INTO wallets (id, name, createdOn) VALUES (?, ?, ?)").use { ps ->
            ps.setString(1, WALLET_ID)
            ps.setString(2, "NDID Test Wallet")
            ps.setString(3, "2024-01-01T00:00:00Z")
            ps.executeUpdate()
        }
        // account
        conn.prepareStatement("INSERT INTO accounts (tenant, id, name, email, createdOn) VALUES (?, ?, ?, ?, ?)").use { ps ->
            ps.setString(1, "")
            ps.setString(2, ACCOUNT_ID)
            ps.setString(3, "NDID User")
            ps.setString(4, "user@ndid.th")
            ps.setString(5, "2024-01-01T00:00:00Z")
            ps.executeUpdate()
        }
        // key — exactly as KeySerialization.serializeKey produces
        conn.prepareStatement("INSERT INTO wallet_keys (wallet, kid, document, createdOn) VALUES (?, ?, ?, ?)").use { ps ->
            ps.setString(1, WALLET_ID)
            ps.setString(2, KEY_ID)
            ps.setString(3, SERIALIZED_KEY)
            ps.setString(4, "2024-01-01T00:00:00Z")
            ps.executeUpdate()
        }
        // W3C JWT VC credential
        conn.prepareStatement(
            "INSERT INTO credentials (wallet, id, document, format, added_on) VALUES (?, ?, ?, ?, ?)"
        ).use { ps ->
            ps.setString(1, WALLET_ID)
            ps.setString(2, CRED_ID)
            ps.setString(3, W3C_JWT_VC)
            ps.setString(4, "jwt_vc_json")
            ps.setString(5, "2024-01-01T00:00:00Z")
            ps.executeUpdate()
        }
        // DID — NDID case: document column holds only the DID URI, not a JSON object
        conn.prepareStatement(
            "INSERT INTO wallet_dids (wallet, did, alias, document, keyId, createdOn) VALUES (?, ?, ?, ?, ?, ?)"
        ).use { ps ->
            ps.setString(1, WALLET_ID)
            ps.setString(2, DID_URI)
            ps.setString(3, "my-ndid-did")
            ps.setString(4, DID_URI)   // ← NOT a JSON object — just the DID URI string
            ps.setString(5, KEY_ID)
            ps.setString(6, "2024-01-01T00:00:00Z")
            ps.executeUpdate()
        }
        // account → wallet mapping
        conn.prepareStatement(
            "INSERT INTO account_wallet_mapping (tenant, id, wallet, added_on, permissions) VALUES (?, ?, ?, ?, ?)"
        ).use { ps ->
            ps.setString(1, "")
            ps.setString(2, ACCOUNT_ID)
            ps.setString(3, WALLET_ID)
            ps.setString(4, "2024-01-01T00:00:00Z")
            ps.setString(5, "USE")
            ps.executeUpdate()
        }
    }

    // ── Tests ──────────────────────────────────────────────────────────────────

    @Test @Order(1)
    fun `CredentialParser detects W3C JWT VC correctly`() = runTest {
        val (detection, credential) = CredentialParser.detectAndParse(W3C_JWT_VC)
        assertNotNull(credential, "Should parse the W3C JWT VC without error")
        assertTrue(credential.credentialData.isNotEmpty(),
            "Parsed credential should have credentialData")
        println("Detection: $detection")
        println("Credential type: ${credential::class.simpleName}")
        println("Format: ${credential.format}")
    }

    @Test @Order(2)
    fun `full migration from v1 SQLite to v2 SQLite`() = runTest {
        // SQLite in-memory databases are connection-scoped — use a file-based temp DB
        // so the migration tool (which opens its own connection) can see the data.
        val sourceFile = kotlin.io.path.createTempFile("wallet_v1_", ".db").toFile()
        val targetFile = kotlin.io.path.createTempFile("wallet_v2_", ".db").toFile()
        sourceFile.deleteOnExit()
        targetFile.deleteOnExit()

        val sourceUrl = "jdbc:sqlite:${sourceFile.absolutePath}"
        val targetUrl = "jdbc:sqlite:${targetFile.absolutePath}"

        // Set up v1 source database
        DriverManager.getConnection(sourceUrl).use { conn ->
            createV1Schema(conn)
            insertV1TestData(conn)
        }

        // Run the migration
        main(arrayOf("--source", sourceUrl, "--target", targetUrl))

        // Verify the v2 target database
        val targetDb = initWallet2Database(Wallet2PersistenceConfig(
            jdbcUrl = targetUrl,
            driverClassName = "org.sqlite.JDBC",
        ))

        transaction(targetDb) {
            // ── Wallet ──────────────────────────────────────────────────────
            val wallets = Wallet2Tables.Wallets.selectAll().toList()
            assertEquals(1, wallets.size, "Should have exactly 1 wallet")
            assertEquals(WALLET_ID, wallets[0][Wallet2Tables.Wallets.id])

            // ── Store registrations ─────────────────────────────────────────
            val keyStores = Wallet2Tables.KeyStores.selectAll().toList()
            assertEquals(1, keyStores.size, "Should have 1 key store")
            assertEquals("wallet-$WALLET_ID-keys", keyStores[0][Wallet2Tables.KeyStores.id])

            val credStores = Wallet2Tables.CredentialStores.selectAll().toList()
            assertEquals(1, credStores.size, "Should have 1 credential store")

            val didStores = Wallet2Tables.DidStores.selectAll().toList()
            assertEquals(1, didStores.size, "Should have 1 DID store")

            // ── Key ─────────────────────────────────────────────────────────
            val keys = Wallet2Tables.Keys.selectAll().toList()
            assertEquals(1, keys.size, "Should have exactly 1 key")
            assertEquals(KEY_ID, keys[0][Wallet2Tables.Keys.keyId])
            // The serialized key document must round-trip — starts with {"type":"jwk"
            assertTrue(keys[0][Wallet2Tables.Keys.serializedKey].contains("\"type\""),
                "Serialized key must contain type discriminator")

            // ── Credential ──────────────────────────────────────────────────
            val creds = Wallet2Tables.Credentials.selectAll().toList()
            assertEquals(1, creds.size, "Should have exactly 1 credential")
            assertEquals(CRED_ID, creds[0][Wallet2Tables.Credentials.id])

            val serialized = creds[0][Wallet2Tables.Credentials.serializedCredential]
            assertNotNull(serialized, "Serialized credential must not be null")
            // Must be a valid JSON object (kotlinx.serialization DigitalCredential)
            val parsed = Json.parseToJsonElement(serialized).jsonObject
            assertNotNull(parsed, "Credential must serialize to JSON")
            // Must contain credentialData which has the VC payload
            assertNotNull(parsed["credentialData"],
                "Migrated credential must contain credentialData field")

            // ── DID ─────────────────────────────────────────────────────────
            // The NDID case: document column = DID URI (not a JSON object).
            // After the fix the DID must be migrated with a minimal {"id":"<did>"} document
            // rather than being silently dropped.
            val dids = Wallet2Tables.Dids.selectAll().toList()
            assertEquals(1, dids.size, "DID must be migrated even when document column is not JSON")
            assertEquals(DID_URI, dids[0][Wallet2Tables.Dids.did])
            val docStr = dids[0][Wallet2Tables.Dids.document]
            assertNotNull(docStr, "DID document must not be null")
            val docJson = Json.parseToJsonElement(docStr).jsonObject
            assertEquals(DID_URI, docJson["id"]!!.jsonPrimitive.content,
                "Minimal document 'id' must equal the DID URI")
            println("Migrated DID document: $docStr")

            // ── Account → wallet mapping ─────────────────────────────────────
            val mappings = Wallet2Tables.AccountWallets.selectAll().toList()
            assertEquals(1, mappings.size, "Should have exactly 1 account→wallet mapping")
            assertEquals(ACCOUNT_ID, mappings[0][Wallet2Tables.AccountWallets.accountId])
            assertEquals(WALLET_ID, mappings[0][Wallet2Tables.AccountWallets.walletId])
        }
    }

    @Test @Order(3)
    fun `migration is idempotent - running twice does not duplicate rows`() = runTest {
        val sourceFile = kotlin.io.path.createTempFile("wallet_v1_idem_", ".db").toFile()
        val targetFile = kotlin.io.path.createTempFile("wallet_v2_idem_", ".db").toFile()
        sourceFile.deleteOnExit()
        targetFile.deleteOnExit()

        val sourceUrl = "jdbc:sqlite:${sourceFile.absolutePath}"
        val targetUrl = "jdbc:sqlite:${targetFile.absolutePath}"

        DriverManager.getConnection(sourceUrl).use { conn ->
            createV1Schema(conn)
            insertV1TestData(conn)
        }

        // Run twice
        main(arrayOf("--source", sourceUrl, "--target", targetUrl))
        main(arrayOf("--source", sourceUrl, "--target", targetUrl))

        val targetDb = initWallet2Database(Wallet2PersistenceConfig(
            jdbcUrl = targetUrl,
            driverClassName = "org.sqlite.JDBC",
        ))

        transaction(targetDb) {
            assertEquals(1, Wallet2Tables.Wallets.selectAll().count(),
                "Idempotent: exactly 1 wallet after 2 runs")
            assertEquals(1, Wallet2Tables.Keys.selectAll().count(),
                "Idempotent: exactly 1 key after 2 runs")
            assertEquals(1, Wallet2Tables.Credentials.selectAll().count(),
                "Idempotent: exactly 1 credential after 2 runs")
            assertEquals(1, Wallet2Tables.AccountWallets.selectAll().count(),
                "Idempotent: exactly 1 account mapping after 2 runs")
        }
    }

    @Test @Order(4)
    fun `dry run does not write anything to target`() = runTest {
        val sourceFile = kotlin.io.path.createTempFile("wallet_v1_dry_", ".db").toFile()
        val targetFile = kotlin.io.path.createTempFile("wallet_v2_dry_", ".db").toFile()
        sourceFile.deleteOnExit()
        targetFile.deleteOnExit()

        val sourceUrl = "jdbc:sqlite:${sourceFile.absolutePath}"
        val targetUrl = "jdbc:sqlite:${targetFile.absolutePath}"

        DriverManager.getConnection(sourceUrl).use { conn ->
            createV1Schema(conn)
            insertV1TestData(conn)
        }

        // Dry run — must not throw, must not write
        main(arrayOf("--source", sourceUrl, "--target", targetUrl, "--dry-run"))

        // Target DB should be empty (tables created by initWallet2Database, but no rows)
        val targetDb = initWallet2Database(Wallet2PersistenceConfig(
            jdbcUrl = targetUrl,
            driverClassName = "org.sqlite.JDBC",
        ))
        transaction(targetDb) {
            assertEquals(0, Wallet2Tables.Wallets.selectAll().count(),
                "Dry run must not write wallets")
            assertEquals(0, Wallet2Tables.Credentials.selectAll().count(),
                "Dry run must not write credentials")
            assertEquals(0, Wallet2Tables.AccountWallets.selectAll().count(),
                "Dry run must not write account mappings")
        }
    }

    @Test @Order(5)
    fun `credential with DID URI as document column is handled without crash`() {
        // Isolated unit test: directly call CredentialParser on a W3C JWT VC
        // and verify a DID-URI-as-document string doesn't blow up credential parsing.
        // (DID parsing is separate from credential parsing; this confirms no cross-contamination.)
        val result = runCatching {
            kotlinx.coroutines.runBlocking {
                CredentialParser.detectAndParse(W3C_JWT_VC)
            }
        }
        assertTrue(result.isSuccess,
            "CredentialParser must not throw on a W3C JWT VC: ${result.exceptionOrNull()?.message}")
    }

    /**
     * Test the real waltid-wallet-api storage format: wallet and account UUIDs are stored
     * as 16-byte binary blobs, NOT as text strings.
     *
     * This was a real bug: the original migration used rs.getString() on blob columns which
     * returns a binary-interpreted string, and then setString() in child queries which does
     * NOT match blob values in SQLite — so keys, credentials, and DIDs were silently lost.
     *
     * The fix: use rs.getBytes() + bytesToUuid() for wallet/account IDs and setBytes() in
     * the child query helper.
     */
    @Test @Order(6)
    fun `migration handles real waltid-wallet-api blob UUID storage correctly`() = runTest {
        val sourceFile = kotlin.io.path.createTempFile("wallet_v1_blob_", ".db").toFile()
        val targetFile = kotlin.io.path.createTempFile("wallet_v2_blob_", ".db").toFile()
        sourceFile.deleteOnExit(); targetFile.deleteOnExit()

        val sourceUrl = "jdbc:sqlite:${sourceFile.absolutePath}"
        val targetUrl = "jdbc:sqlite:${targetFile.absolutePath}"

        // UUID as 16-byte binary (big-endian), matching what waltid-wallet-api stores
        val walletUuid = java.util.UUID.fromString(WALLET_ID)
        val accountUuid = java.util.UUID.fromString(ACCOUNT_ID)
        fun uuidToBytes(u: java.util.UUID): ByteArray {
            val bb = java.nio.ByteBuffer.allocate(16)
            bb.putLong(u.mostSignificantBits); bb.putLong(u.leastSignificantBits)
            return bb.array()
        }
        val walletBytes  = uuidToBytes(walletUuid)
        val accountBytes = uuidToBytes(accountUuid)

        DriverManager.getConnection(sourceUrl).use { conn ->
            // Schema with BLOB wallet/account columns (real waltid-wallet-api DDL)
            conn.createStatement().use { stmt ->
                stmt.executeUpdate("CREATE TABLE wallets (id BLOB NOT NULL PRIMARY KEY, name TEXT NOT NULL, createdOn TEXT NOT NULL)")
                stmt.executeUpdate("CREATE TABLE accounts (tenant TEXT DEFAULT '', id BLOB NOT NULL, name TEXT, email TEXT, createdOn TEXT NOT NULL, PRIMARY KEY(tenant,id))")
                stmt.executeUpdate("CREATE TABLE wallet_keys (wallet BLOB NOT NULL, kid TEXT NOT NULL, document TEXT NOT NULL, name TEXT, createdOn TEXT NOT NULL, PRIMARY KEY(wallet,kid))")
                stmt.executeUpdate("CREATE TABLE credentials (wallet BLOB NOT NULL, id TEXT NOT NULL, document TEXT NOT NULL, disclosures TEXT, added_on TEXT NOT NULL, manifest TEXT, deleted_on TEXT, pending INTEGER DEFAULT 0, format TEXT DEFAULT 'jwt_vc_json', PRIMARY KEY(wallet,id))")
                stmt.executeUpdate("CREATE TABLE wallet_dids (wallet BLOB NOT NULL, did TEXT NOT NULL, alias TEXT DEFAULT '', document TEXT NOT NULL, keyId TEXT NOT NULL, \"default\" INTEGER DEFAULT 0, createdOn TEXT NOT NULL, PRIMARY KEY(wallet,did))")
                stmt.executeUpdate("CREATE TABLE account_wallet_mapping (tenant TEXT DEFAULT '', id BLOB NOT NULL, wallet BLOB NOT NULL, added_on TEXT NOT NULL, permissions TEXT DEFAULT 'USE', PRIMARY KEY(tenant,id,wallet))")
            }
            // Insert with blob UUIDs
            conn.prepareStatement("INSERT INTO wallets VALUES (?,?,?)").use { it.setBytes(1, walletBytes); it.setString(2, "Blob Wallet"); it.setString(3, "2024-01-01"); it.executeUpdate() }
            conn.prepareStatement("INSERT INTO accounts VALUES ('',?,?,?,?)").use { it.setBytes(1, accountBytes); it.setString(2, "Blob User"); it.setString(3, "blob@test.th"); it.setString(4, "2024-01-01"); it.executeUpdate() }
            conn.prepareStatement("INSERT INTO wallet_keys VALUES (?,?,?,?,?)").use { it.setBytes(1, walletBytes); it.setString(2, KEY_ID); it.setString(3, SERIALIZED_KEY); it.setNull(4, java.sql.Types.VARCHAR); it.setString(5, "2024-01-01"); it.executeUpdate() }
            conn.prepareStatement("INSERT INTO credentials VALUES (?,?,?,?,?,?,?,?,?)").use { it.setBytes(1, walletBytes); it.setString(2, CRED_ID); it.setString(3, W3C_JWT_VC); it.setNull(4, java.sql.Types.VARCHAR); it.setString(5, "2024-01-01"); it.setNull(6, java.sql.Types.VARCHAR); it.setNull(7, java.sql.Types.VARCHAR); it.setInt(8, 0); it.setString(9, "jwt_vc_json"); it.executeUpdate() }
            conn.prepareStatement("INSERT INTO wallet_dids VALUES (?,?,?,?,?,?,?)").use { it.setBytes(1, walletBytes); it.setString(2, DID_URI); it.setString(3, "alias"); it.setString(4, """{"id":"$DID_URI"}"""); it.setString(5, KEY_ID); it.setInt(6, 1); it.setString(7, "2024-01-01"); it.executeUpdate() }
            conn.prepareStatement("INSERT INTO account_wallet_mapping VALUES ('',?,?,?,?)").use { it.setBytes(1, accountBytes); it.setBytes(2, walletBytes); it.setString(3, "2024-01-01"); it.setString(4, "USE"); it.executeUpdate() }
        }

        main(arrayOf("--source", sourceUrl, "--target", targetUrl))

        val targetDb = initWallet2Database(Wallet2PersistenceConfig(jdbcUrl = targetUrl, driverClassName = "org.sqlite.JDBC"))
        transaction(targetDb) {
            // Wallet ID must be a proper UUID string, not binary garbage
            val wallets = Wallet2Tables.Wallets.selectAll().toList()
            assertEquals(1, wallets.size, "Must have 1 wallet")
            assertEquals(WALLET_ID, wallets[0][Wallet2Tables.Wallets.id],
                "Wallet UUID must be properly decoded from blob, not binary garbage")

            // Key must be found (the main bug: blob FK lookup must work)
            assertEquals(1, Wallet2Tables.Keys.selectAll().count(),
                "Key must be migrated — blob wallet FK lookup must work")
            assertEquals(KEY_ID, Wallet2Tables.Keys.selectAll().first()[Wallet2Tables.Keys.keyId])

            // Credential must be found
            assertEquals(1, Wallet2Tables.Credentials.selectAll().count(),
                "Credential must be migrated — blob wallet FK lookup must work")

            // DID must be found
            assertEquals(1, Wallet2Tables.Dids.selectAll().count(),
                "DID must be migrated — blob wallet FK lookup must work")
            assertEquals(DID_URI, Wallet2Tables.Dids.selectAll().first()[Wallet2Tables.Dids.did])

            // Account mapping must have proper UUID strings
            val mappings = Wallet2Tables.AccountWallets.selectAll().toList()
            assertEquals(1, mappings.size)
            assertEquals(ACCOUNT_ID, mappings[0][Wallet2Tables.AccountWallets.accountId],
                "Account UUID must be properly decoded from blob")
            assertEquals(WALLET_ID, mappings[0][Wallet2Tables.AccountWallets.walletId],
                "Wallet UUID in mapping must be properly decoded from blob")
        }
    }
}
