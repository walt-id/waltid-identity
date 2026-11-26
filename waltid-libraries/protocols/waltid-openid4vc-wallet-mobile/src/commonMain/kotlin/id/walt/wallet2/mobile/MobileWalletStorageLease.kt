package id.walt.wallet2.mobile

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Coordinates in-process handles that can erase the same database, key namespace or host store. */
internal class MobileWalletStorageLease(
    private val database: String,
    private val keyNamespace: String,
    private val stores: List<Any>,
) {
    suspend fun open() = mutex.withLock {
        check(owners.none { it.erasing && overlaps(it) }) { "Wallet storage is being erased" }
        owners.add(this)
        Unit
    }

    suspend fun beginErase() = mutex.withLock {
        check(owners.none { it !== this && overlaps(it) }) { "Close other wallets using this storage before erasing it" }
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

    private fun overlaps(other: MobileWalletStorageLease) =
        database == other.database || keyNamespace == other.keyNamespace || stores.any { ours -> other.stores.any { ours === it } }

    private var erasing = false

    private companion object {
        val mutex = Mutex()
        val owners = mutableSetOf<MobileWalletStorageLease>()
    }
}
