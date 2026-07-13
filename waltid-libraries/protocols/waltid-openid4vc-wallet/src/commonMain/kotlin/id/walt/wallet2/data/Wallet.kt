package id.walt.wallet2.data

import id.walt.crypto.keys.DirectSerializedKey
import id.walt.crypto.keys.Key
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.toList

/**
 * Represents a wallet instance composed of its storage backends.
 *
 * Supports:
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

    /**
     * ID of the preferred default key. When non-null, [defaultKey] returns this key
     * instead of the first key across the stores.
     */
    val defaultKeyId: String? = null,

    /**
     * Preferred default DID. When non-null, [defaultDid] returns this DID
     * instead of the first DID in the store.
     */
    val defaultDidId: String? = null,
) {
    // ---------------------------------------------------------------------------
    // Aggregate helpers
    // ---------------------------------------------------------------------------

    /** Finds a key by ID across all key stores; returns the first match. */
    suspend fun findKey(keyId: String): Key? =
        keyStores.firstNotNullOfOrNull { it.getKey(keyId) }

    /**
     * Resolves the key to use for signing, following this priority:
     * 1. [inlineKey] - supplied directly by the caller (takes precedence over everything)
     * 2. [keyId] - looked up in all key stores
     * 3. [defaultKey] - the wallet's configured default key
     *
     * Returns null only when no key is available at all. Extracted here to eliminate the
     * identical `resolveKey` private functions that existed in both [WalletIssuanceHandler]
     * and [WalletPresentationHandler].
     */
    suspend fun resolveKey(inlineKey: DirectSerializedKey? = null, keyId: String? = null): Key? = when {
        inlineKey != null -> inlineKey.key
        keyId != null -> findKey(keyId) ?: error("Key '$keyId' not found in any wallet key store")
        else -> defaultKey()
    }

    /**
     * Returns the default key.
     * If [defaultKeyId] is set, that key is returned (looked up across all stores).
     * Falls back to the first key across all stores, then to [staticKey].
     */
    suspend fun defaultKey(): Key? =
        defaultKeyId?.let { findKey(it) }
            ?: keyStores.firstNotNullOfOrNull { it.getDefaultKey() }
            ?: staticKey

    /** Lists all keys across all key stores, in store order. */
    suspend fun listAllKeys(): List<WalletKeyInfo> {
        val storeKeys = keyStores.flatMap { it.listKeys().toList() }
        // Include the static key if present and not already represented in a store.
        val staticKeyInfo = staticKey?.let {
            val keyId = it.getKeyId()
            if (storeKeys.none { k -> k.keyId == keyId })
                WalletKeyInfo(keyId = keyId, keyType = it.keyType.name)
            else null
        }
        return if (staticKeyInfo != null) storeKeys + staticKeyInfo else storeKeys
    }

    /** Streams all credentials across all credential stores, in store order. */
    suspend fun streamAllCredentials(): Flow<StoredCredential> =
        credentialStores.map { it.listCredentials() }.merge()

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

    /**
     * Returns the default DID.
     * If [defaultDidId] is set, that DID is returned.
     * Falls back to the first DID in the store, then to [staticDid].
     */
    suspend fun defaultDid(): String? =
        defaultDidId
            ?: didStore?.getDefaultDid()
            ?: staticDid
}
