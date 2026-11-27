package id.walt.wallet2.status

import id.walt.wallet2.data.StoredCredential

/** The signed data required for status work. An unreadable record still occupies its place in the inventory. */
sealed interface CredentialStatusInput {
    val id: String

    /** Read the signed bytes, stored observation and notice from the same row snapshot. */
    data class Signed(
        val credential: StoredCredential,
        val storedStatus: CredentialStatusStoredRecord? = null,
        val notice: CredentialStatusNoticeState? = null,
    ) : CredentialStatusInput {
        override val id: String get() = credential.id
    }

    data class Unreadable(override val id: String) : CredentialStatusInput
}

/** A pageable status-only view. The page order must be stable by wallet-local ID. */
interface CredentialStatusInventory : CredentialStatusNoticeStore {
    suspend fun statusPage(afterId: String?, limit: Int): List<CredentialStatusInput>
}
