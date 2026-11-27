@file:OptIn(ExperimentalObjCRefinement::class)

package id.walt.wallet2.status

import id.walt.wallet2.data.StoredCredential
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC

/**
 * Foreground store integration for deferring credential parsing. Opt in only if these reads
 * apply the same visibility and ordering as the store's status inventory.
 */
interface StoredCredentialStatusInventory : CredentialStatusInventory {
    @HiddenFromObjC
    suspend fun readStatusRow(id: String): StoredCredentialStatusRow?

    @HiddenFromObjC
    suspend fun statusRows(afterId: String?, limit: Int): List<StoredCredentialStatusRow>
}

/** Exact credential representation, status evidence and notice from a single store read. */
@HiddenFromObjC
interface StoredCredentialStatusRow {
    val id: String
    val storedStatus: CredentialStatusStoredRecord?
    val notice: CredentialStatusNoticeState?

    /** A collision-resistant digest of the complete stored credential, including disclosures. */
    fun credentialDigest(): String

    /** Decodes the same stored credential. An unreadable row returns null. */
    suspend fun readCredential(): StoredCredential?
}
