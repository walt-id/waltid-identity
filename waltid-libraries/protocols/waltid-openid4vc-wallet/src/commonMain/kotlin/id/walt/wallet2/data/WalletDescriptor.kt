package id.walt.wallet2.data

import kotlinx.serialization.Serializable

/**
 * Serializable description of a wallet's configuration — store IDs and
 * static fallbacks. This is what gets persisted; the live [Wallet] object
 * (with runtime store instances) is assembled from this by the store
 * implementation at load time.
 *
 * Separating configuration from runtime assembly means persistent
 * implementations never receive un-serializable interface instances.
 */
@Serializable
data class WalletDescriptor(
    val id: String,

    /** IDs of key stores attached to this wallet (ordered; first match wins on lookup). */
    val keyStoreIds: List<String> = emptyList(),

    /** IDs of credential stores attached to this wallet (write to first, read from all). */
    val credentialStoreIds: List<String> = emptyList(),

    /** ID of the DID store attached to this wallet, or null if none. */
    val didStoreId: String? = null,

    /**
     * Serialized static key (using [id.walt.crypto.keys.KeySerialization.serializeKey])
     * for store-less / isolated-only wallets. Null if the wallet uses key stores.
     */
    val serializedStaticKey: String? = null,

    /** Static DID fallback when no DID store is configured. */
    val staticDid: String? = null,
)
