package id.walt.wallet2.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList

/**
 * Storage contract for credentials held by a wallet.
 * Uses [kotlinx.coroutines.flow.Flow] for listing to support large credential stores efficiently.
 */
interface WalletCredentialStore {

    suspend fun getCredential(id: String): StoredCredential?

    fun listCredentials(): Flow<StoredCredential>

    suspend fun addCredential(entry: StoredCredential)

    /** Returns true if the credential existed and was removed. */
    suspend fun removeCredential(id: String): Boolean

    /**
     * Convenience: list all credentials as a plain list (collects the Flow).
     * Prefer [listCredentials] for streaming access.
     */
    suspend fun listCredentialsAsList(): List<StoredCredential> =
        listCredentials().toList()
}
