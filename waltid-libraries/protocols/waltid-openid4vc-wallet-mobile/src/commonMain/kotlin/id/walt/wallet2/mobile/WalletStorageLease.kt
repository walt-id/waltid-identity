package id.walt.wallet2.mobile

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Coordinates in-process handles that can erase the same database, key namespace or host store. */
internal class WalletStorageLease(
    private val database: String,
    private val keyNamespace: String,
    stores: List<Any>,
) {
    suspend fun open() = mutex.withLock {
        if (owners.any { it.erasing && overlaps(it) }) throw WalletStorageInUseException("Wallet storage is being erased")
        owners.add(this)
        Unit
    }

    suspend fun beginErase() = mutex.withLock {
        if (owners.any { it !== this && overlaps(it) }) {
            throw WalletStorageInUseException("Close other wallets using this storage before erasing it")
        }
        owners.add(this)
        erasing = true
    }

    suspend fun close() = mutex.withLock {
        if (!erasing) owners.remove(this)
        Unit
    }

    suspend fun finishErase() = mutex.withLock {
        owners.remove(this)
        erasing = false
    }

    private fun overlaps(other: WalletStorageLease) =
        database == other.database || keyNamespace == other.keyNamespace || stores.any { ours -> other.stores.any(ours::matches) }

    private val stores = stores.map { (it as? WalletStoreAdapter)?.storeOwner ?: WalletStoreOwner(it) }
    private var erasing = false

    private companion object {
        val mutex = Mutex()
        val owners = mutableSetOf<WalletStorageLease>()
    }
}

/** Exposes the original storage owner when a platform integration wraps a host store. */
internal interface WalletStoreAdapter {
    val storeOwner: WalletStoreOwner
}

/** Retains the adapter so its process-local reference identity stays valid for the lifetime of the lease. */
internal class WalletStoreOwner(private val reference: Any, private val hostIdentity: Long? = null) {
    fun matches(other: WalletStoreOwner): Boolean =
        reference === other.reference || hostIdentity != null && hostIdentity == other.hostIdentity
}

internal class WalletStorageInUseException(message: String) : IllegalStateException(message)
