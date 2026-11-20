package id.walt.wallet2.status

import id.walt.wallet2.data.StoredCredential

/** The signed data required for status work. An unreadable record still occupies its place in the inventory. */
sealed interface CredentialStatusInput {
    val id: String

    data class Signed(val credential: StoredCredential) : CredentialStatusInput {
        override val id: String get() = credential.id
    }

    data class Unreadable(override val id: String) : CredentialStatusInput
}

/** A status-only view of a store. The page order must be stable by wallet-local ID. */
interface CredentialStatusInventory : CredentialStatusSnapshotStore {
    suspend fun readStatusInput(id: String): CredentialStatusInput?
    suspend fun statusPage(afterId: String?, limit: Int): List<CredentialStatusInput>
}
