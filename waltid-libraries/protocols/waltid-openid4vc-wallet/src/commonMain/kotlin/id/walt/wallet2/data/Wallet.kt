package id.walt.wallet2.data

import id.walt.crypto.keys.DirectSerializedKey
import id.walt.crypto.keys.Key
import id.walt.crypto2.jose.Jwk
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.Key as Crypto2Key
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.inferKeySpec
import id.walt.crypto2.keys.toPublicJwk
import id.walt.crypto2.serialization.BinaryData
import id.walt.wallet2.handlers.WalletIssuanceHandler
import id.walt.wallet2.handlers.WalletPresentationHandler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

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
    private var resolvedStaticCrypto2Key: Crypto2Key? = null

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

    /** Finds a crypto2 key by ID across all key stores, then the static-key sidecar. */
    suspend fun findCrypto2Key(keyId: String, usages: Set<KeyUsage> = emptySet()): Crypto2Key? =
        keyStores.firstNotNullOfOrNull { it.getCrypto2Key(keyId, usages) }
            ?: resolvedStaticCrypto2Key?.takeIf { it.id.value == keyId }?.also { key ->
                require(usages.all(key.usages::contains)) { "Wallet crypto2 static key does not permit requested usages" }
            }

    suspend fun resolveCrypto2Key(
        keyId: String? = null,
        usages: Set<KeyUsage> = emptySet(),
    ): Crypto2Key? = when {
        keyId != null -> findCrypto2Key(keyId, usages)
            ?: error("Crypto2 key '$keyId' not found in any wallet key store")
        else -> defaultCrypto2Key(usages)
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

    suspend fun defaultCrypto2Key(usages: Set<KeyUsage> = emptySet()): Crypto2Key? =
        defaultKeyId?.let { findCrypto2Key(it, usages) }
            ?: keyStores.firstNotNullOfOrNull { it.getDefaultCrypto2Key(usages) }
            ?: resolvedStaticCrypto2Key?.also { key ->
                require(usages.all(key.usages::contains)) { "Wallet crypto2 static key does not permit requested usages" }
            }

    /** Attaches a validated runtime crypto2 counterpart without changing the published constructor. */
    suspend fun attachStaticCrypto2Key(key: Crypto2Key): Wallet = apply {
        val legacyKey = requireNotNull(staticKey) { "A crypto2 static key requires serialized legacy key material" }
        val legacyPublicJwk = EncodedKey.Jwk(
            BinaryData(Json.encodeToString(legacyKey.getPublicKey().exportJWKObject()).encodeToByteArray()),
            privateMaterial = false,
        )
        val crypto2PublicJwk = requireNotNull(key.capabilities.publicKeyExporter) {
            "Wallet crypto2 static key does not export public material"
        }.exportPublicKey().toPublicJwk(key.spec)
        val expectedUsages = if (legacyKey.hasPrivateKey) {
            setOf(KeyUsage.SIGN, KeyUsage.VERIFY)
        } else {
            setOf(KeyUsage.VERIFY)
        }
        require(key.id.value == legacyKey.getKeyId()) { "Wallet crypto2 static key ID does not match the legacy key" }
        require(key.spec == legacyPublicJwk.inferKeySpec()) {
            "Wallet crypto2 static key specification does not match the legacy key"
        }
        require(key.usages == expectedUsages) { "Wallet crypto2 static key usages do not match the legacy key" }
        require(Jwk.sha256Thumbprint(legacyPublicJwk) == Jwk.sha256Thumbprint(crypto2PublicJwk)) {
            "Wallet crypto2 static key public material does not match the legacy key"
        }
        resolvedStaticCrypto2Key = key
    }

    /** Returns the attached runtime sidecar without exposing it as a serializable bean property. */
    fun attachedStaticCrypto2Key(): Crypto2Key? = resolvedStaticCrypto2Key

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

internal suspend fun Wallet.resolveKeyMaterial(
    keyId: String?,
    crypto2Usages: Set<KeyUsage>,
): WalletKeyStoreEntry? {
    val preferredKeyId = keyId ?: defaultKeyId
    if (preferredKeyId != null) {
        keyStores.forEach { store ->
            store.getKeyMaterial(preferredKeyId, crypto2Usages)?.let { material ->
                requireMatchingKeyMaterial(material.legacyKey, material.crypto2Key)
                return material
            }
        }
        staticKey?.takeIf { it.getKeyId() == preferredKeyId }
            ?.let { return staticKeyMaterial(crypto2Usages) }
        if (keyId != null) return null
    }
    keyStores.forEach { store ->
        val defaultKeyId = store.listKeys().firstOrNull()?.keyId ?: return@forEach
        store.getKeyMaterial(defaultKeyId, crypto2Usages)?.let { material ->
            requireMatchingKeyMaterial(material.legacyKey, material.crypto2Key)
            return material
        }
    }
    return staticKeyMaterial(crypto2Usages)
}

private suspend fun Wallet.staticKeyMaterial(usages: Set<KeyUsage>): WalletKeyStoreEntry? = staticKey?.let { legacyKey ->
    val crypto2Key = attachedStaticCrypto2Key()?.also { key ->
        require(usages.all(key.usages::contains)) { "Wallet crypto2 static key does not permit requested usages" }
    }
    WalletKeyStoreEntry(legacyKey.getKeyId(), legacyKey, crypto2Key)
}

private suspend fun requireMatchingKeyMaterial(legacyKey: Key?, crypto2Key: Crypto2Key?) {
    if (legacyKey == null || crypto2Key == null) return
    val legacyJwk = EncodedKey.Jwk(
        BinaryData(Json.encodeToString(legacyKey.getPublicKey().exportJWKObject()).encodeToByteArray()),
        privateMaterial = false,
    )
    val crypto2Jwk = requireNotNull(crypto2Key.capabilities.publicKeyExporter) {
        "Wallet crypto2 key does not export public material"
    }.exportPublicKey().toPublicJwk(crypto2Key.spec)
    require(Jwk.sha256Thumbprint(legacyJwk) == Jwk.sha256Thumbprint(crypto2Jwk)) {
        "Wallet legacy and crypto2 key material do not match"
    }
}
