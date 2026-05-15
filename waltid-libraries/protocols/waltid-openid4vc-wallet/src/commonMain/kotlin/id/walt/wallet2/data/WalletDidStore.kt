package id.walt.wallet2.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList

/**
 * Storage contract for DIDs held by a wallet.
 */
interface WalletDidStore {

    suspend fun getDid(did: String): WalletDidEntry?

    fun listDids(): Flow<WalletDidEntry>

    suspend fun addDid(entry: WalletDidEntry)

    /** Returns true if the DID existed and was removed. */
    suspend fun removeDid(did: String): Boolean

    suspend fun getDefaultDid(): String? =
        listDids().firstOrNull()?.did

    /**
     * Convenience: list all DIDs as a plain list (collects the Flow).
     * Prefer [listDids] for streaming access.
     */
    suspend fun listDidsAsList(): List<WalletDidEntry> =
        listDids().toList()
}
