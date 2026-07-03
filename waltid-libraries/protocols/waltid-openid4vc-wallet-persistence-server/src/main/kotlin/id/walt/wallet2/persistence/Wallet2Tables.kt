package id.walt.wallet2.persistence

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.timestamp

/**
 * Exposed table definitions for the wallet2 persistence layer.
 *
 * Schema design principles:
 * - All primary keys are plain TEXT (wallet/store IDs are application-generated UUIDs)
 * - Credentials stored as serialized [id.walt.credentials.formats.DigitalCredential] JSON
 *   (via kotlinx.serialization) — no re-parsing on load, format info preserved
 * - Keys stored as [id.walt.crypto.keys.KeySerialization.serializeKey] JSON
 *   — round-trips via [id.walt.crypto.keys.KeyManager.resolveSerializedKey]
 * - DID documents stored as JSON text
 * - All tables are prefixed `wallet2_` to avoid conflicts with old wallet-api tables
 */
object Wallet2Tables {

    /** Wallet descriptors — configuration without live store instances. */
    object Wallets : Table("wallet2_wallets") {
        val id = varchar("id", 128)
        val serializedStaticKey = text("static_key").nullable()
        val staticDid = varchar("static_did", 1024).nullable()
        override val primaryKey = PrimaryKey(id)
    }

    /** Named key stores — one row per store instance. */
    object KeyStores : Table("wallet2_key_stores") {
        val id = varchar("id", 128)
        override val primaryKey = PrimaryKey(id)
    }

    /** Named credential stores — one row per store instance. */
    object CredentialStores : Table("wallet2_credential_stores") {
        val id = varchar("id", 128)
        override val primaryKey = PrimaryKey(id)
    }

    /** Named DID stores — one row per store instance. */
    object DidStores : Table("wallet2_did_stores") {
        val id = varchar("id", 128)
        override val primaryKey = PrimaryKey(id)
    }

    /** wallet ↔ key store association (ordered; position determines lookup priority). */
    object WalletKeyStores : Table("wallet2_wallet_key_stores") {
        val walletId = reference("wallet_id", Wallets.id)
        val storeId = reference("store_id", KeyStores.id)
        val position = integer("position").default(0)
        override val primaryKey = PrimaryKey(walletId, storeId)
    }

    /** wallet ↔ credential store association (ordered). */
    object WalletCredentialStores : Table("wallet2_wallet_credential_stores") {
        val walletId = reference("wallet_id", Wallets.id)
        val storeId = reference("store_id", CredentialStores.id)
        val position = integer("position").default(0)
        override val primaryKey = PrimaryKey(walletId, storeId)
    }

    /** wallet ↔ DID store association (at most one per wallet). */
    object WalletDidStores : Table("wallet2_wallet_did_stores") {
        val walletId = reference("wallet_id", Wallets.id).uniqueIndex()
        val storeId = reference("store_id", DidStores.id)
        override val primaryKey = PrimaryKey(walletId)
    }

    /**
     * Keys stored per key store.
     * [serializedKey]: output of [id.walt.crypto.keys.KeySerialization.serializeKey] —
     * a JSON string of the form `{"type":"jwk","jwk":{...}}` that round-trips via
     * [id.walt.crypto.keys.KeyManager.resolveSerializedKey].
     */
    object Keys : Table("wallet2_keys") {
        val storeId = reference("store_id", KeyStores.id)
        val keyId = varchar("key_id", 512)
        val keyType = varchar("key_type", 64)
        val serializedKey = text("serialized_key")
        override val primaryKey = PrimaryKey(storeId, keyId)
    }

    /**
     * Credentials stored per credential store.
     * [serializedCredential]: output of
     * `Json.encodeToString(DigitalCredential.serializer(), credential)` —
     * preserves the exact parsed type including format-specific fields.
     * No re-parsing on load: deserialize with
     * `Json.decodeFromString(DigitalCredential.serializer(), serializedCredential)`.
     */
    object Credentials : Table("wallet2_credentials") {
        val storeId = reference("store_id", CredentialStores.id)
        val id = varchar("id", 256)
        val serializedCredential = text("serialized_credential")
        val label = varchar("label", 512).nullable()
        val addedAt = timestamp("added_at")
        override val primaryKey = PrimaryKey(storeId, id)
    }

    /**
     * DIDs stored per DID store.
     * [document]: DID document as a JSON text string.
     */
    object Dids : Table("wallet2_dids") {
        val storeId = reference("store_id", DidStores.id)
        val did = varchar("did", 1024)
        val document = text("document")
        override val primaryKey = PrimaryKey(storeId, did)
    }

    /** Account ↔ wallet ownership mapping (used when auth is enabled). */
    object AccountWallets : Table("wallet2_account_wallets") {
        val accountId = varchar("account_id", 256)
        val walletId = reference("wallet_id", Wallets.id)
        override val primaryKey = PrimaryKey(accountId, walletId)
    }

    /** All tables in creation order (respects foreign key constraints). */
    val ALL = arrayOf(
        Wallets,
        KeyStores, CredentialStores, DidStores,
        WalletKeyStores, WalletCredentialStores, WalletDidStores,
        Keys, Credentials, Dids,
        AccountWallets
    )
}
