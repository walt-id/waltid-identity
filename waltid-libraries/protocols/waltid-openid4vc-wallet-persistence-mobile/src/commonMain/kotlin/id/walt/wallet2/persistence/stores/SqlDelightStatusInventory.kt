package id.walt.wallet2.persistence.stores

import id.walt.credentials.CredentialParser
import id.walt.wallet2.data.StoredCredential
import id.walt.wallet2.persistence.db.WalletPersistenceQueries
import id.walt.wallet2.status.CredentialStatusCommit
import id.walt.wallet2.status.CredentialStatusInput
import id.walt.wallet2.status.CredentialStatusInventory
import id.walt.wallet2.status.CredentialStatusNoticeState
import id.walt.wallet2.status.CredentialStatusStoredRecord
import id.walt.wallet2.status.statusFingerprint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.Json

internal class SqlDelightStatusInventory(
    private val queries: WalletPersistenceQueries,
    private val isAuthorized: () -> Boolean = { true },
) : CredentialStatusInventory {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun readCredentialStatusRecord(credentialId: String): CredentialStatusStoredRecord? =
        queries.selectCredentialStatus(credentialId).executeAsOneOrNull()?.let(::decodeStatusRecord)

    override suspend fun readStatusInput(id: String): CredentialStatusInput? =
        queries.selectStatusInput(id, ::SignedInput).executeAsOneOrNull()?.parse()

    override suspend fun statusPage(afterId: String?, limit: Int): List<CredentialStatusInput> {
        require(limit in 1..1001)
        val page = if (afterId == null) queries.firstStatusPage(limit.toLong(), ::SignedInput)
            else queries.nextStatusPage(afterId, limit.toLong(), ::SignedInput)
        return page.executeAsList().map {
            currentCoroutineContext().ensureActive()
            it.parse()
        }
    }

    private inner class SignedInput(val id: String, val serialized: String, val snapshot: String?, val notice: String?) {
        suspend fun parse(): CredentialStatusInput = try {
            CredentialStatusInput.Signed(
            StoredCredential(id, CredentialParser.detectAndParse(serialized).second),
            snapshot?.let(::decodeStatusRecord),
            decodeNotice(notice),
        )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            CredentialStatusInput.Unreadable(id)
        }
    }

    private fun decodeStatusRecord(raw: String): CredentialStatusStoredRecord? =
        runCatching { json.decodeFromString(CredentialStatusStoredRecord.serializer(), raw) }.getOrNull()

    private fun decodeNotice(raw: String?): CredentialStatusNoticeState? =
        raw?.let { runCatching { json.decodeFromString<CredentialStatusNoticeState>(it) }.getOrNull() }

    override suspend fun acknowledgeStatusNotice(credentialId: String, version: String): Boolean = queries.transactionWithResult {
        val notice = decodeNotice(queries.selectCredentialStatusNotice(credentialId).executeAsOneOrNull()?.notice)
            ?.takeIf { it.version == version && !it.acknowledged } ?: return@transactionWithResult false
        if (!isAuthorized()) return@transactionWithResult false
        queries.updateCredentialStatusNotice(json.encodeToString(notice.copy(acknowledged = true)), credentialId)
        true
    }

    override suspend fun compareAndSetCredentialStatus(
        credential: StoredCredential, expected: CredentialStatusStoredRecord?, updated: CredentialStatusStoredRecord,
    ): Boolean = compareAndSetCredentialStatusAndNotice(credential, expected, updated) { null } != CredentialStatusCommit.LOST

    override suspend fun compareAndSetCredentialStatusAndNotice(
        credential: StoredCredential,
        expected: CredentialStatusStoredRecord?,
        updated: CredentialStatusStoredRecord,
        transition: (CredentialStatusNoticeState?) -> CredentialStatusNoticeState?,
    ): CredentialStatusCommit = queries.transactionWithResult {
        require(updated.revision != expected?.revision)
        require(updated.snapshot?.credentialFingerprint?.let { it == credential.statusFingerprint() } != false)
        // The grant, signed bytes, evidence revision and acknowledgement share this transaction.
        if (!isAuthorized()) return@transactionWithResult CredentialStatusCommit.LOST
        val raw = queries.selectCredentialStatus(credential.id).executeAsOneOrNull()
        if (raw?.let(::decodeStatusRecord) != expected) return@transactionWithResult CredentialStatusCommit.LOST
        val previous = decodeNotice(queries.selectCredentialStatusNotice(credential.id).executeAsOneOrNull()?.notice)
        val notice = transition(previous)
        queries.saveCredentialStatusIfCurrent(snapshot = json.encodeToString(CredentialStatusStoredRecord.serializer(), updated),
            notice = notice?.let { json.encodeToString(it) }, credential_id = credential.id,
            expected_credential = credential.credential.serializedForWalletPersistence(), expected_snapshot = raw)
        when {
            queries.credentialStatusChanges().executeAsOne() != 1L -> CredentialStatusCommit.LOST
            notice != null && !notice.acknowledged && notice.version != previous?.version -> CredentialStatusCommit.NOTICE_CREATED
            else -> CredentialStatusCommit.COMMITTED
        }
    }
}
