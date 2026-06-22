package id.walt.wallet.migration

import id.walt.credentials.CredentialParser
import id.walt.credentials.formats.DigitalCredential
import id.walt.wallet2.persistence.Wallet2Tables
import id.walt.wallet2.persistence.initWallet2Database
import id.walt.wallet2.persistence.Wallet2PersistenceConfig
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.jetbrains.exposed.v1.jdbc.upsert
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import java.sql.DriverManager
import java.sql.ResultSet
import java.time.Instant

private val log = KotlinLogging.logger {}

/**
 * Migrates data from the old `waltid-wallet-api` (v1) database schema to the new
 * `waltid-wallet-api2` schema backed by `waltid-openid4vc-wallet-persistence`.
 *
 * Usage:
 * ```
 * ./gradlew :waltid-services:waltid-wallet-migration:run \
 *   --args="--source jdbc:sqlite:/path/to/old/wallet.db --target jdbc:sqlite:/path/to/new/wallet2.db"
 * ```
 *
 * For PostgreSQL:
 * ```
 * --source "jdbc:postgresql://localhost:5432/walletv1?user=wallet&password=secret"
 * --target "jdbc:postgresql://localhost:5432/walletv2?user=wallet&password=secret"
 * ```
 *
 * Add `--dry-run` to preview what would be migrated without writing anything.
 *
 * Migration mapping:
 *
 * | Old table              | New table(s)                                              |
 * |------------------------|-----------------------------------------------------------|
 * | wallets                | wallet2_wallets + store registration tables + junctions   |
 * | wallet_keys            | wallet2_keys (per wallet key store)                       |
 * | credentials            | wallet2_credentials (re-serialized as DigitalCredential)  |
 * | wallet_dids            | wallet2_dids (document string → JsonObject)               |
 * | account_wallet_mapping | wallet2_account_wallets                                   |
 *
 * Each old wallet gets exactly one key store, one credential store, and one DID store,
 * with deterministic IDs (`wallet-{id}-keys`, `wallet-{id}-creds`, `wallet-{id}-dids`).
 * The migration is idempotent — running it twice will not duplicate data.
 */
fun main(args: Array<String>) {
    val parsed = parseArgs(args)
    val sourceUrl = parsed["source"] ?: error("--source <jdbc-url> is required")
    val targetUrl = parsed["target"] ?: error("--target <jdbc-url> is required")
    val dryRun = "dry-run" in parsed

    log.info { "Source: $sourceUrl" }
    log.info { "Target: $targetUrl" }
    if (dryRun) log.info { "DRY RUN — no writes will be made" }

    // Source: plain JDBC — avoids any Exposed version conflict with the old wallet schema
    val sourceConn = DriverManager.getConnection(sourceUrl)

    // Target: Exposed 1.3.0 via persistence module
    val targetDb = initWallet2Database(Wallet2PersistenceConfig(
        jdbcUrl = targetUrl,
        driverClassName = driverFor(targetUrl),
    ))

    runBlocking {
        var walletCount = 0
        var keyCount = 0
        var credentialCount = 0
        var didCount = 0
        var accountMappingCount = 0
        var errorCount = 0

        // ── Wallets ────────────────────────────────────────────────────────────────

        val walletIds = sourceConn.query("SELECT id FROM wallets") { rs ->
            rs.getString("id")
        }

        log.info { "Found ${walletIds.size} wallets to migrate" }

        for (walletUuid in walletIds) {
            val keyStoreId  = "wallet-$walletUuid-keys"
            val credStoreId = "wallet-$walletUuid-creds"
            val didStoreId  = "wallet-$walletUuid-dids"

            if (!dryRun) {
                suspendTransaction(targetDb) {
                    Wallet2Tables.Wallets.upsert { it[Wallet2Tables.Wallets.id] = walletUuid }
                    Wallet2Tables.KeyStores.upsert { it[Wallet2Tables.KeyStores.id] = keyStoreId }
                    Wallet2Tables.CredentialStores.upsert { it[Wallet2Tables.CredentialStores.id] = credStoreId }
                    Wallet2Tables.DidStores.upsert { it[Wallet2Tables.DidStores.id] = didStoreId }
                    Wallet2Tables.WalletKeyStores.upsert {
                        it[Wallet2Tables.WalletKeyStores.walletId] = walletUuid
                        it[Wallet2Tables.WalletKeyStores.storeId]  = keyStoreId
                        it[Wallet2Tables.WalletKeyStores.position] = 0
                    }
                    Wallet2Tables.WalletCredentialStores.upsert {
                        it[Wallet2Tables.WalletCredentialStores.walletId] = walletUuid
                        it[Wallet2Tables.WalletCredentialStores.storeId]  = credStoreId
                        it[Wallet2Tables.WalletCredentialStores.position] = 0
                    }
                    Wallet2Tables.WalletDidStores.upsert {
                        it[Wallet2Tables.WalletDidStores.walletId] = walletUuid
                        it[Wallet2Tables.WalletDidStores.storeId]  = didStoreId
                    }
                }
            }
            walletCount++

            // ── Keys ──────────────────────────────────────────────────────────────

            val keys = sourceConn.queryWith(
                "SELECT kid, document FROM wallet_keys WHERE wallet = ?", walletUuid
            ) { rs -> rs.getString("kid") to rs.getString("document") }

            for ((keyId, serializedKey) in keys) {
                runCatching {
                    val keyType = inferKeyType(serializedKey)
                    if (!dryRun) {
                        suspendTransaction(targetDb) {
                            Wallet2Tables.Keys.upsert {
                                it[Wallet2Tables.Keys.storeId]       = keyStoreId
                                it[Wallet2Tables.Keys.keyId]         = keyId
                                it[Wallet2Tables.Keys.keyType]       = keyType
                                it[Wallet2Tables.Keys.serializedKey] = serializedKey
                            }
                        }
                    }
                    keyCount++
                }.onFailure { e ->
                    log.warn { "Failed to migrate key $keyId for wallet $walletUuid: ${e.message}" }
                    errorCount++
                }
            }

            // ── Credentials ───────────────────────────────────────────────────────

            data class OldCred(val id: String, val document: String, val disclosures: String?)
            val credentials = sourceConn.queryWith(
                "SELECT id, document, disclosures FROM credentials WHERE wallet = ?", walletUuid
            ) { rs -> OldCred(rs.getString("id"), rs.getString("document"), rs.getString("disclosures")) }

            for (cred in credentials) {
                runCatching {
                    val rawCredential = if (!cred.disclosures.isNullOrBlank())
                        "${cred.document}~${cred.disclosures}" else cred.document
                    val (_, parsed) = CredentialParser.detectAndParse(rawCredential)
                    val serialized  = Json.encodeToString(DigitalCredential.serializer(), parsed)
                    if (!dryRun) {
                        suspendTransaction(targetDb) {
                            Wallet2Tables.Credentials.upsert {
                                it[Wallet2Tables.Credentials.storeId]              = credStoreId
                                it[Wallet2Tables.Credentials.id]                   = cred.id
                                it[Wallet2Tables.Credentials.serializedCredential] = serialized
                                it[Wallet2Tables.Credentials.addedAt]              = Instant.now()
                            }
                        }
                    }
                    credentialCount++
                }.onFailure { e ->
                    log.warn { "Failed to migrate credential ${cred.id} for wallet $walletUuid: ${e.message}" }
                    errorCount++
                }
            }

            // ── DIDs ──────────────────────────────────────────────────────────────

            val dids = sourceConn.queryWith(
                "SELECT did, document FROM wallet_dids WHERE wallet = ?", walletUuid
            ) { rs -> rs.getString("did") to rs.getString("document") }

            for ((did, documentStr) in dids) {
                runCatching {
                    Json.parseToJsonElement(documentStr).jsonObject // validate JSON
                    if (!dryRun) {
                        suspendTransaction(targetDb) {
                            Wallet2Tables.Dids.upsert {
                                it[Wallet2Tables.Dids.storeId]  = didStoreId
                                it[Wallet2Tables.Dids.did]      = did
                                it[Wallet2Tables.Dids.document] = documentStr
                            }
                        }
                    }
                    didCount++
                }.onFailure { e ->
                    log.warn { "Failed to migrate DID $did for wallet $walletUuid: ${e.message}" }
                    errorCount++
                }
            }
        }

        // ── Account → wallet mappings ──────────────────────────────────────────────

        // Old: account_wallet_mapping(tenant, id UUID, wallet UUID)
        // New: wallet2_account_wallets(account_id TEXT, wallet_id TEXT)
        val accountMappings = sourceConn.query(
            "SELECT CAST(id AS TEXT) AS account_id, CAST(wallet AS TEXT) AS wallet_id FROM account_wallet_mapping"
        ) { rs -> rs.getString("account_id") to rs.getString("wallet_id") }

        for ((accountId, walletId) in accountMappings) {
            runCatching {
                if (!dryRun) {
                    suspendTransaction(targetDb) {
                        Wallet2Tables.AccountWallets.upsert {
                            it[Wallet2Tables.AccountWallets.accountId] = accountId
                            it[Wallet2Tables.AccountWallets.walletId]  = walletId
                        }
                    }
                }
                accountMappingCount++
            }.onFailure { e ->
                log.warn { "Failed to migrate account mapping $accountId → $walletId: ${e.message}" }
                errorCount++
            }
        }

        sourceConn.close()

        log.info { "" }
        log.info { "═══════════════════════════════════════════" }
        log.info { "  Migration ${if (dryRun) "DRY RUN " else ""}complete" }
        log.info { "  Wallets:          $walletCount" }
        log.info { "  Keys:             $keyCount" }
        log.info { "  Credentials:      $credentialCount" }
        log.info { "  DIDs:             $didCount" }
        log.info { "  Account mappings: $accountMappingCount" }
        if (errorCount > 0) log.warn { "  Errors:           $errorCount (see warnings above)" }
        log.info { "═══════════════════════════════════════════" }
    }
}

// ── JDBC helpers ───────────────────────────────────────────────────────────────

/** Execute a SELECT with no parameters and map each row. */
private fun <T> java.sql.Connection.query(sql: String, mapper: (ResultSet) -> T): List<T> =
    prepareStatement(sql).use { stmt ->
        stmt.executeQuery().use { rs ->
            val out = mutableListOf<T>()
            while (rs.next()) out += mapper(rs)
            out
        }
    }

/** Execute a SELECT with one String parameter (wallet UUID as TEXT) and map each row. */
private fun <T> java.sql.Connection.queryWith(sql: String, param: String, mapper: (ResultSet) -> T): List<T> =
    prepareStatement(sql).use { stmt ->
        stmt.setString(1, param)
        stmt.executeQuery().use { rs ->
            val out = mutableListOf<T>()
            while (rs.next()) out += mapper(rs)
            out
        }
    }

/** Infer the wallet2 key type string from a KeySerialization JSON document. */
private fun inferKeyType(serializedKey: String): String = runCatching {
    val json = Json.parseToJsonElement(serializedKey).jsonObject
    val crv  = json["jwk"]?.jsonObject?.get("crv")?.toString()?.trim('"')
    when (crv) {
        "P-256"     -> "secp256r1"
        "P-384"     -> "secp384r1"
        "P-521"     -> "secp521r1"
        "Ed25519"   -> "Ed25519"
        "secp256k1" -> "secp256k1"
        else        -> json["type"]?.toString()?.trim('"') ?: "unknown"
    }
}.getOrDefault("unknown")

private fun parseArgs(args: Array<String>): Map<String, String> {
    val result = mutableMapOf<String, String>()
    var i = 0
    while (i < args.size) {
        val key = args[i].removePrefix("--")
        if (i + 1 < args.size && !args[i + 1].startsWith("--")) {
            result[key] = args[++i]; i++
        } else {
            result[key] = "true"; i++
        }
    }
    return result
}

private fun driverFor(url: String): String = when {
    url.contains("sqlite")     -> "org.sqlite.JDBC"
    url.contains("postgresql") -> "org.postgresql.Driver"
    else -> error("Cannot infer JDBC driver from URL: $url. Use --source-driver or --target-driver.")
}
