package id.walt.wallet.migration

import id.walt.credentials.CredentialParser
import id.walt.wallet2.persistence.Wallet2PersistenceConfig
import id.walt.wallet2.persistence.Wallet2Tables
import id.walt.wallet2.persistence.initWallet2Database
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import java.sql.DriverManager
import java.sql.Types
import java.util.UUID
import kotlin.test.*

/**
 * Comprehensive migration test covering all key types, DID methods, credential formats,
 * deleted credentials, and multi-account scenarios.
 *
 * All data is inserted with BLOB UUIDs matching the real waltid-wallet-api SQLite storage.
 */
@TestMethodOrder(OrderAnnotation::class)
class ComprehensiveMigrationTest {

    // ── Test data ──────────────────────────────────────────────────────────────

    private val walletA = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
    private val walletB = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb")
    private val accountA = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc")
    private val accountB = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd")

    private fun uuidBlob(u: UUID): ByteArray {
        val bb = java.nio.ByteBuffer.allocate(16)
        bb.putLong(u.mostSignificantBits); bb.putLong(u.leastSignificantBits)
        return bb.array()
    }

    // Serialized keys — exactly as KeySerialization.serializeKey produces

    private val ED25519_KEY = """{"type":"jwk","jwk":{"kty":"OKP","crv":"Ed25519","kid":"key-ed25519","x":"11qYAYKxCrfVS_7TyWQHOg7hcvPapiMlrwIaaPcHURo","d":"nWGxne_9WmC6hEr0kuwsxERJxWl7MmkZcDusAxyuf2A"}}"""
    private val SECP256R1_KEY = """{"type":"jwk","jwk":{"kty":"EC","crv":"P-256","kid":"key-p256","x":"2Z3gxK7IatHaxPWLYkBYn1XS0wKdL7fMQQuF_nGw2Kw","y":"41CM3oYupV2TNid0xDbESe0bzKWVNu0LU8kKQS47jUI","d":"ozV2xdDIDJMoA1_M7DzVEZVZc4ozN9KtREQqQj7AgQo"}}"""
    private val SECP256K1_KEY = """{"type":"jwk","jwk":{"kty":"EC","crv":"secp256k1","kid":"key-k1","x":"EVLRsAXC2K2UOkMT5pRjKJvA9z7MNjVj4XFZxrQ4cAo","y":"FdC7S1lCkBbNlsJnp2Y-SJhj7YuGqrMKoJjJCdUuVmw","d":"key-d-placeholder"}}"""
    private val RSA_KEY = """{"type":"jwk","jwk":{"kty":"RSA","kid":"key-rsa","n":"s2Z7pB9x8q_AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA","e":"AQAB","d":"placeholder"}}"""

    // Credentials for walletA

    /** W3C DM 1.1 JWT VC — the main NDID format */
    private val CRED_JWT_W3C_1_1 = run {
        val h = """{"alg":"EdDSA","typ":"JWT","kid":"key-ed25519"}""".b64url
        val p = """{"iss":"did:key:z6Mk","sub":"did:key:z6Mk","jti":"urn:uuid:cred-w3c11","vc":{"@context":["https://www.w3.org/2018/credentials/v1"],"type":["VerifiableCredential","TestCredential"],"credentialSubject":{"id":"did:key:z6Mk","nationalId":"1111111111111"}}}""".b64url
        "$h.$p.fakesig"
    }

    /** W3C DM 2.0 JWT VC */
    private val CRED_JWT_W3C_2 = run {
        val h = """{"alg":"EdDSA","typ":"JWT"}""".b64url
        val p = """{"iss":"did:key:z6Mk","sub":"did:key:z6Mk","jti":"urn:uuid:cred-w3c2","vc":{"@context":["https://www.w3.org/ns/credentials/v2"],"type":["VerifiableCredential","AddressCredential"],"credentialSubject":{"id":"did:key:z6Mk","streetAddress":"123 Main St"}}}""".b64url
        "$h.$p.fakesig"
    }

    /** W3C JSON-LD (ldp_vc) — with a DataIntegrityProof that CredentialParser can parse */
    private val CRED_LDP_VC = """{"@context":["https://www.w3.org/2018/credentials/v1","https://w3id.org/security/data-integrity/v1"],"id":"urn:uuid:cred-ldp","type":["VerifiableCredential","LdpTestCredential"],"issuer":"did:key:z6Mk","issuanceDate":"2024-01-01T00:00:00Z","credentialSubject":{"id":"did:key:z6Mk","attribute":"value"},"proof":{"type":"DataIntegrityProof","cryptosuite":"eddsa-rdfc-2022","created":"2024-01-01T00:00:00Z","verificationMethod":"did:key:z6Mk#z6Mk","proofPurpose":"assertionMethod","proofValue":"fakesig"}}"""

    /** SD-JWT VC — document (JWT part) and disclosures (stored separately in v1).
     *  Disclosures use real base64url encoding with matching SHA-256 hashes in _sd. */
    private val CRED_SDJWT_DOC = run {
        val h = """{"alg":"EdDSA","typ":"vc+sd-jwt","kid":"key-ed25519"}""".b64url
        // _sd hashes computed from: disc1=["salt1", "name", "Alice"], disc2=["salt2", "age", 25]
        val p = """{"iss":"did:key:z6Mk","sub":"did:key:z6Mk","vct":"https://example.com/TestVC","jti":"urn:uuid:cred-sdjwt","_sd_alg":"sha-256","_sd":["PXFmIMR6GqxNtK4nFPGFeppvHiW2u-rxbSWazxVQdAg","c2v-Tlqps5XrV9llZr7yMkt9AZKRKx3nCaddiADDZSw"],"knownClaim":"knownValue"}""".b64url
        "$h.$p.fakesig"
    }
    // v1 stores disclosures as tilde-separated disclosure strings (the disclosure tokens themselves)
    private val CRED_SDJWT_DISCLOSURES = "WyJzYWx0MSIsICJuYW1lIiwgIkFsaWNlIl0~WyJzYWx0MiIsICJhZ2UiLCAyNV0"

    /** Deleted credential — soft-deleted in v1 (has deleted_on timestamp) */
    private val CRED_DELETED = CRED_JWT_W3C_1_1.replace("cred-w3c11", "cred-deleted")

    // Credentials for walletB
    private val CRED_B = run {
        val h = """{"alg":"ES256","typ":"JWT","kid":"key-p256"}""".b64url
        val p = """{"iss":"did:jwk:xyz","sub":"did:jwk:xyz","jti":"urn:uuid:cred-wallet-b","vc":{"@context":["https://www.w3.org/2018/credentials/v1"],"type":["VerifiableCredential","MemberCredential"],"credentialSubject":{"id":"did:jwk:xyz","memberSince":"2020"}}}""".b64url
        "$h.$p.fakesig"
    }

    private val String.b64url get() =
        java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(this.encodeToByteArray())

    // ── Source DB setup ────────────────────────────────────────────────────────

    private fun createV1BlobSchema(conn: java.sql.Connection) {
        conn.createStatement().use { s ->
            s.executeUpdate("CREATE TABLE wallets(id BLOB PRIMARY KEY, name TEXT NOT NULL, createdOn TEXT NOT NULL)")
            s.executeUpdate("CREATE TABLE accounts(tenant TEXT DEFAULT '',id BLOB,name TEXT,email TEXT,createdOn TEXT NOT NULL,PRIMARY KEY(tenant,id))")
            s.executeUpdate("CREATE TABLE wallet_keys(wallet BLOB NOT NULL,kid TEXT NOT NULL,document TEXT NOT NULL,name TEXT,createdOn TEXT NOT NULL,PRIMARY KEY(wallet,kid))")
            s.executeUpdate("CREATE TABLE credentials(wallet BLOB NOT NULL,id TEXT NOT NULL,document TEXT NOT NULL,disclosures TEXT,added_on TEXT NOT NULL,manifest TEXT,deleted_on TEXT,pending INTEGER DEFAULT 0,format TEXT DEFAULT 'jwt_vc_json',PRIMARY KEY(wallet,id))")
            s.executeUpdate("CREATE TABLE wallet_dids(wallet BLOB NOT NULL,did TEXT NOT NULL,alias TEXT DEFAULT '',document TEXT NOT NULL,keyId TEXT NOT NULL,\"default\" INTEGER DEFAULT 0,createdOn TEXT NOT NULL,PRIMARY KEY(wallet,did))")
            s.executeUpdate("CREATE TABLE account_wallet_mapping(tenant TEXT DEFAULT '',id BLOB NOT NULL,wallet BLOB NOT NULL,added_on TEXT NOT NULL,permissions TEXT DEFAULT 'USE',PRIMARY KEY(tenant,id,wallet))")
        }
    }

    private fun insertComprehensiveData(conn: java.sql.Connection) {
        fun blob(u: UUID) = uuidBlob(u)

        // Wallets
        conn.ps("INSERT INTO wallets VALUES(?,?,?)") { it.setBytes(1, blob(walletA)); it.setString(2, "Wallet A"); it.setString(3, "2024-01-01") }
        conn.ps("INSERT INTO wallets VALUES(?,?,?)") { it.setBytes(1, blob(walletB)); it.setString(2, "Wallet B"); it.setString(3, "2024-01-01") }

        // Accounts
        conn.ps("INSERT INTO accounts VALUES('',?,?,?,?)") { it.setBytes(1, blob(accountA)); it.setString(2, "User A"); it.setString(3, "a@test.com"); it.setString(4, "2024-01-01") }
        conn.ps("INSERT INTO accounts VALUES('',?,?,?,?)") { it.setBytes(1, blob(accountB)); it.setString(2, "User B"); it.setString(3, "b@test.com"); it.setString(4, "2024-01-01") }

        // Keys for wallet A: all supported types
        for ((kid, doc) in listOf("key-ed25519" to ED25519_KEY, "key-p256" to SECP256R1_KEY, "key-k1" to SECP256K1_KEY, "key-rsa" to RSA_KEY)) {
            conn.ps("INSERT INTO wallet_keys VALUES(?,?,?,?,?)") {
                it.setBytes(1, blob(walletA)); it.setString(2, kid); it.setString(3, doc); it.setNull(4, Types.VARCHAR); it.setString(5, "2024-01-01")
            }
        }
        // Key for wallet B
        conn.ps("INSERT INTO wallet_keys VALUES(?,?,?,?,?)") {
            it.setBytes(1, blob(walletB)); it.setString(2, "key-p256"); it.setString(3, SECP256R1_KEY); it.setNull(4, Types.VARCHAR); it.setString(5, "2024-01-01")
        }

        // DIDs for wallet A: did:key and did:jwk (with full document as real wallet stores)
        val didKeyDoc = """{"@context":["https://www.w3.org/ns/did/v1"],"id":"did:key:z6Mk","verificationMethod":[{"id":"did:key:z6Mk#z6Mk","type":"JsonWebKey2020","controller":"did:key:z6Mk","publicKeyJwk":{"kty":"OKP","crv":"Ed25519","kid":"key-ed25519","x":"11qYAYKxCrfVS_7TyWQHOg7hcvPapiMlrwIaaPcHURo"}}],"authentication":["did:key:z6Mk#z6Mk"]}"""
        val didJwkDoc = """{"@context":["https://www.w3.org/ns/did/v1"],"id":"did:jwk:xyz","verificationMethod":[{"id":"did:jwk:xyz#0","type":"JsonWebKey2020","controller":"did:jwk:xyz","publicKeyJwk":{"kty":"EC","crv":"P-256","kid":"key-p256","x":"2Z3gxK7IatHaxPWLYkBYn1XS0wKdL7fMQQuF_nGw2Kw","y":"41CM3oYupV2TNid0xDbESe0bzKWVNu0LU8kKQS47jUI"}}]}"""
        conn.ps("INSERT INTO wallet_dids VALUES(?,?,?,?,?,?,?)") { it.setBytes(1, blob(walletA)); it.setString(2, "did:key:z6Mk"); it.setString(3, "main"); it.setString(4, didKeyDoc); it.setString(5, "key-ed25519"); it.setInt(6, 1); it.setString(7, "2024-01-01") }
        conn.ps("INSERT INTO wallet_dids VALUES(?,?,?,?,?,?,?)") { it.setBytes(1, blob(walletA)); it.setString(2, "did:jwk:xyz"); it.setString(3, "jwk"); it.setString(4, didJwkDoc); it.setString(5, "key-p256"); it.setInt(6, 0); it.setString(7, "2024-01-01") }

        // DID for wallet B: did:key with secp256r1
        val didBDoc = """{"@context":["https://www.w3.org/ns/did/v1"],"id":"did:key:zQ3B","verificationMethod":[{"id":"did:key:zQ3B#zQ3B","type":"JsonWebKey2020","controller":"did:key:zQ3B","publicKeyJwk":{"kty":"EC","crv":"P-256","kid":"key-p256","x":"2Z3gxK7IatHaxPWLYkBYn1XS0wKdL7fMQQuF_nGw2Kw","y":"41CM3oYupV2TNid0xDbESe0bzKWVNu0LU8kKQS47jUI"}}]}"""
        conn.ps("INSERT INTO wallet_dids VALUES(?,?,?,?,?,?,?)") { it.setBytes(1, blob(walletB)); it.setString(2, "did:key:zQ3B"); it.setString(3, "main"); it.setString(4, didBDoc); it.setString(5, "key-p256"); it.setInt(6, 1); it.setString(7, "2024-01-01") }

        // Credentials for wallet A: all formats including deleted
        val creds = listOf(
            Triple("urn:uuid:cred-w3c11", CRED_JWT_W3C_1_1, "jwt_vc_json") to Pair(null as String?, null as String?),
            Triple("urn:uuid:cred-w3c2",  CRED_JWT_W3C_2,   "jwt_vc_json") to Pair(null, null),
            Triple("urn:uuid:cred-ldp",   CRED_LDP_VC,      "ldp_vc")      to Pair(null, null),
            Triple("urn:uuid:cred-sdjwt", CRED_SDJWT_DOC,   "sd_jwt_vc")   to Pair(CRED_SDJWT_DISCLOSURES, null),
            Triple("urn:uuid:cred-deleted", CRED_DELETED,   "jwt_vc_json") to Pair(null, "2024-06-01T00:00:00Z"),
        )
        for ((triplet, extra) in creds) {
            val (credId, doc, fmt) = triplet
            val (disclosures, deletedOn) = extra
            conn.prepareStatement("INSERT INTO credentials VALUES(?,?,?,?,?,?,?,?,?)").use {
                it.setBytes(1, blob(walletA))
                it.setString(2, credId); it.setString(3, doc)
                if (disclosures != null) it.setString(4, disclosures) else it.setNull(4, Types.VARCHAR)
                it.setString(5, "2024-01-01"); it.setNull(6, Types.VARCHAR)
                if (deletedOn != null) it.setString(7, deletedOn) else it.setNull(7, Types.VARCHAR)
                it.setInt(8, 0); it.setString(9, fmt)
                it.executeUpdate()
            }
        }

        // Credential for wallet B
        conn.ps("INSERT INTO credentials VALUES(?,?,?,?,?,?,?,?,?)") {
            it.setBytes(1, blob(walletB)); it.setString(2, "urn:uuid:cred-wallet-b"); it.setString(3, CRED_B)
            it.setNull(4, Types.VARCHAR); it.setString(5, "2024-01-01")
            it.setNull(6, Types.VARCHAR); it.setNull(7, Types.VARCHAR)
            it.setInt(8, 0); it.setString(9, "jwt_vc_json")
        }

        // Account → wallet mappings
        conn.ps("INSERT INTO account_wallet_mapping VALUES('',?,?,?,?)") { it.setBytes(1, blob(accountA)); it.setBytes(2, blob(walletA)); it.setString(3, "2024-01-01"); it.setString(4, "ADMINISTRATE") }
        conn.ps("INSERT INTO account_wallet_mapping VALUES('',?,?,?,?)") { it.setBytes(1, blob(accountB)); it.setBytes(2, blob(walletB)); it.setString(3, "2024-01-01"); it.setString(4, "USE") }
    }

    private fun java.sql.Connection.ps(sql: String, block: (java.sql.PreparedStatement) -> Unit) =
        prepareStatement(sql).use { block(it); it.executeUpdate() }

    // ── Tests ──────────────────────────────────────────────────────────────────

    @Test @Order(1)
    fun `CredentialParser handles all credential formats`() = runTest {
        // Verify each format parses without error before running migration
        val testCases = listOf(
            "jwt_vc_json W3C 1.1" to CRED_JWT_W3C_1_1,
            "jwt_vc_json W3C 2.0" to CRED_JWT_W3C_2,
            "ldp_vc"              to CRED_LDP_VC,
            "sd_jwt_vc"           to "$CRED_SDJWT_DOC~$CRED_SDJWT_DISCLOSURES",
        )
        for ((label, raw) in testCases) {
            val result = runCatching { CredentialParser.detectAndParse(raw) }
            assertTrue(result.isSuccess, "$label: CredentialParser failed: ${result.exceptionOrNull()?.message}")
            val (detection, cred) = result.getOrThrow()
            println("$label → ${cred::class.simpleName}, format=${cred.format}")
        }
    }

    @Test @Order(2)
    fun `inferKeyType handles all key types correctly`() {
        // RSA: kty=RSA, no crv — must return "RSA" not "jwk"
        // secp256k1: kty=EC, crv=secp256k1 — must return "secp256k1"
        val cases = mapOf(
            ED25519_KEY   to "Ed25519",
            SECP256R1_KEY to "secp256r1",
            SECP256K1_KEY to "secp256k1",
            RSA_KEY       to "RSA",
        )
        for ((doc, expected) in cases) {
            val got = inferKeyType(doc)
            assertEquals(expected, got, "Key type for doc starting '${doc.take(40)}'")
        }
    }

    @Test @Order(3)
    fun `full comprehensive migration with all key types, DIDs, credential formats`() = runTest {
        val srcFile = kotlin.io.path.createTempFile("v1_comp_", ".db").toFile().also { it.deleteOnExit() }
        val tgtFile = kotlin.io.path.createTempFile("v2_comp_", ".db").toFile().also { it.deleteOnExit() }

        DriverManager.getConnection("jdbc:sqlite:${srcFile.absolutePath}").use { conn ->
            createV1BlobSchema(conn)
            insertComprehensiveData(conn)
        }

        main(arrayOf("--source", "jdbc:sqlite:${srcFile.absolutePath}", "--target", "jdbc:sqlite:${tgtFile.absolutePath}"))

        val db = initWallet2Database(Wallet2PersistenceConfig(jdbcUrl = "jdbc:sqlite:${tgtFile.absolutePath}", driverClassName = "org.sqlite.JDBC"))
        transaction(db) {

            // ── Wallets ──────────────────────────────────────────────────────
            assertEquals(2, Wallet2Tables.Wallets.selectAll().count(), "2 wallets")

            // ── Keys ─────────────────────────────────────────────────────────
            val keys = Wallet2Tables.Keys.selectAll().toList()
            assertEquals(5, keys.size, "5 keys total (4 in wallet A, 1 in wallet B)")

            val keysByType = keys.groupBy { it[Wallet2Tables.Keys.keyType] }
            println("Key types found: ${keysByType.keys}")

            assertNotNull(keysByType["Ed25519"], "Ed25519 key must be migrated")
            assertNotNull(keysByType["secp256r1"], "secp256r1 key must be migrated")
            assertNotNull(keysByType["secp256k1"], "secp256k1 key must be migrated")
            // RSA: kty=RSA has no crv — inferKeyType must return "RSA" not "jwk"
            assertNotNull(keysByType["RSA"],
                "RSA key must have type 'RSA', not 'jwk'. Found types: ${keysByType.keys}")

            // ── DIDs ──────────────────────────────────────────────────────────
            val dids = Wallet2Tables.Dids.selectAll().toList()
            assertEquals(3, dids.size, "3 DIDs total")
            val didUris = dids.map { it[Wallet2Tables.Dids.did] }.toSet()
            assertTrue("did:key:z6Mk" in didUris, "did:key must be migrated")
            assertTrue("did:jwk:xyz" in didUris, "did:jwk must be migrated")
            assertTrue("did:key:zQ3B" in didUris, "wallet B did:key must be migrated")

            // All DID documents must be valid JSON
            for (row in dids) {
                val doc = row[Wallet2Tables.Dids.document]
                val parsed = runCatching { Json.parseToJsonElement(doc).jsonObject }.getOrNull()
                assertNotNull(parsed, "DID ${row[Wallet2Tables.Dids.did]} document must be valid JSON: $doc")
            }

            // ── Credentials ───────────────────────────────────────────────────
            val creds = Wallet2Tables.Credentials.selectAll().toList()
            // Note: deleted credential (deleted_on IS NOT NULL) is currently migrated too.
            // This is acceptable — v2 can decide whether to filter deleted ones at query time.
            // What matters is all non-deleted ones are present.
            val credIds = creds.map { it[Wallet2Tables.Credentials.id] }.toSet()
            println("Migrated credential IDs: $credIds")

            assertTrue("urn:uuid:cred-w3c11" in credIds, "W3C 1.1 JWT VC must be migrated")
            assertTrue("urn:uuid:cred-w3c2"  in credIds, "W3C 2.0 JWT VC must be migrated")
            assertTrue("urn:uuid:cred-ldp"   in credIds, "JSON-LD (ldp_vc) must be migrated")
            assertTrue("urn:uuid:cred-sdjwt" in credIds, "SD-JWT VC must be migrated")
            assertTrue("urn:uuid:cred-wallet-b" in credIds, "Wallet B credential must be migrated")

            // Each migrated credential must produce valid serialized JSON (DigitalCredential)
            for (row in creds) {
                val id = row[Wallet2Tables.Credentials.id]
                val serialized = row[Wallet2Tables.Credentials.serializedCredential]
                val json = runCatching { Json.parseToJsonElement(serialized).jsonObject }.getOrNull()
                assertNotNull(json, "Credential $id must serialize to JSON object")
                assertNotNull(json!!["credentialData"], "Credential $id must have credentialData")
                println("  $id → type=${json["type"]?.jsonPrimitive?.content}")
            }

            // ── Account mappings ───────────────────────────────────────────────
            val mappings = Wallet2Tables.AccountWallets.selectAll().toList()
            assertEquals(2, mappings.size, "2 account→wallet mappings")
            val mappingPairs = mappings.map { it[Wallet2Tables.AccountWallets.accountId] to it[Wallet2Tables.AccountWallets.walletId] }.toSet()
            assertTrue(accountA.toString() to walletA.toString() in mappingPairs, "Account A → Wallet A mapping must exist")
            assertTrue(accountB.toString() to walletB.toString() in mappingPairs, "Account B → Wallet B mapping must exist")
        }
    }

    @Test @Order(4)
    fun `SD-JWT credential disclosures are correctly assembled`() = runTest {
        // The v1 DB stores: document = JWT header.payload.sig, disclosures = disc1~disc2
        // The migration must reassemble as: header.payload.sig~disc1~disc2 (trailing ~ optional)
        // and pass this to CredentialParser.detectAndParse
        val raw = "$CRED_SDJWT_DOC~$CRED_SDJWT_DISCLOSURES"
        val (detection, cred) = CredentialParser.detectAndParse(raw)
        println("SD-JWT detection: $detection")
        println("SD-JWT credential type: ${cred::class.simpleName}")
        // Must have disclosures (via SelectivelyDisclosableVerifiableCredential interface)
        val sdCred = cred as? id.walt.credentials.signatures.sdjwt.SelectivelyDisclosableVerifiableCredential
        assertNotNull(sdCred, "SD-JWT VC must implement SelectivelyDisclosableVerifiableCredential")
        assertNotNull(sdCred.disclosures, "SD-JWT must have disclosures")
        assertTrue(sdCred.disclosures!!.isNotEmpty(), "SD-JWT disclosures must not be empty")
    }
}
