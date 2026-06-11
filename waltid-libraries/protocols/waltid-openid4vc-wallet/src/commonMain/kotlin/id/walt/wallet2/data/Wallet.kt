package id.walt.wallet2.data

import id.walt.crypto.keys.Key
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Represents a wallet instance composed of its storage backends.
 *
 * Supports the same flexibility as the Enterprise wallet:
 * - Multiple key stores (e.g. software key store + HSM)
 * - Multiple credential stores (e.g. separate stores per credential type)
 * - One optional DID store
 * - staticKey / staticDid fallbacks for store-less / isolated-only wallets
 *
 * The OSS default (POST /wallet with empty body) auto-creates one in-memory
 * store of each type. The multi-store fields are only needed for advanced use.
 *
 * Both OSS (in-memory) and Enterprise (MongoDB resource tree) implement the
 * same interfaces; wallet logic in this library works against them only.
 */
data class Wallet(
    val id: String,

    /**
     * Key stores for this wallet. Keys are looked up across all stores in order;
     * the first match wins. Maps to KeyManagementServiceReference dependencies
     * in the Enterprise.
     */
    val keyStores: List<WalletKeyStore> = emptyList(),

    /**
     * DID store for this wallet. Null means no DID management.
     * Maps to DidStoreServiceReference dependency in the Enterprise.
     */
    val didStore: WalletDidStore? = null,

    /**
     * Credential stores for this wallet. New credentials are written to the
     * first store; reads aggregate across all stores.
     * Maps to CredentialStoreServiceReference dependencies in the Enterprise.
     */
    val credentialStores: List<WalletCredentialStore> = emptyList(),

    /**
     * Fallback key when no keyStores are configured or no match is found.
     * Mirrors WalletServiceConfiguration.staticKey in the Enterprise.
     */
    val staticKey: Key? = null,

    /**
     * Fallback DID when didStore is null or empty.
     * Mirrors WalletServiceConfiguration.staticDid in the Enterprise.
     */
    val staticDid: String? = null,
) {
    // ---------------------------------------------------------------------------
    // Aggregate helpers — hide multi-store complexity from handler code
    // ---------------------------------------------------------------------------

    /** Finds a key by ID across all key stores; returns the first match. */
    suspend fun findKey(keyId: String): Key? =
        keyStores.firstNotNullOfOrNull { it.getKey(keyId) }

    /** Returns the default key: first key across all stores, or staticKey. */
    suspend fun defaultKey(): Key? =
        keyStores.firstNotNullOfOrNull { it.getDefaultKey() } ?: staticKey

    /** Lists all keys across all key stores, in store order. */
    suspend fun listAllKeys(): List<WalletKeyInfo> =
        keyStores.flatMap { it.listKeys() }

    /** Streams all credentials across all credential stores, in store order. */
    fun streamAllCredentials(): Flow<StoredCredential> = flow {
        credentialStores.forEach { store ->
            store.listCredentials().collect { emit(it) }
        }
    }

    /** Finds a credential by wallet-assigned ID across all credential stores. */
    suspend fun findCredential(id: String): StoredCredential? =
        credentialStores.firstNotNullOfOrNull { it.getCredential(id) }

    /**
     * Adds a credential to the primary (first) credential store.
     * Throws if no credential stores are configured.
     */
    suspend fun addCredential(entry: StoredCredential) {
        val store = credentialStores.firstOrNull()
            ?: error("Wallet '$id' has no credential stores configured")
        store.addCredential(entry)
    }

    /** Returns the default DID from the DID store, or staticDid. */
    suspend fun defaultDid(): String? =
        didStore?.getDefaultDid() ?: staticDid
}
