package id.walt.wallet2.status

import id.walt.credentials.status.CredentialStatus
import id.walt.credentials.status.StatusMeaning
import id.walt.credentials.status.entries
import id.walt.credentials.status.hasCheckError
import id.walt.credentials.status.lastObservation
import id.walt.credentials.status.matches
import id.walt.credentials.status.meaning
import id.walt.wallet2.data.StoredCredential
import kotlinx.serialization.Serializable
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** One notice version and acknowledgement alongside a credential's current evidence. */
@Serializable
data class CredentialStatusNoticeState(
    val version: String,
    val referenceId: String,
    val kind: StatusMeaning,
    val checkedAt: Instant,
    val acknowledged: Boolean = false,
) {
    init { require(kind in adverseNoticeKinds) }
}

/** Holder-facing data, read after wallet unlock. The version fences acknowledgement. */
@Serializable
data class PendingCredentialStatusNotice(
    val reference: WalletCredentialReference,
    val version: String,
    val kind: StatusMeaning,
    val checkedAt: Instant,
    val historical: Boolean,
)

enum class CredentialStatusCommit { LOST, COMMITTED, NOTICE_CREATED }

/** Stores the notice transition in the same guarded transaction as its permitted evidence. */
interface CredentialStatusNoticeStore {
    suspend fun acknowledgeStatusNotice(credentialId: String, version: String): Boolean

    suspend fun compareAndSetCredentialStatusAndNotice(
        credential: StoredCredential,
        expected: CredentialStatusStoredRecord?,
        updated: CredentialStatusStoredRecord,
        transition: (CredentialStatusNoticeState?) -> CredentialStatusNoticeState?,
    ): CredentialStatusCommit
}

internal val adverseNoticeKinds = listOf(StatusMeaning.REVOKED, StatusMeaning.INVALID, StatusMeaning.SUSPENDED)

internal fun CredentialStatus.noticeTransition(
    previous: CredentialStatusNoticeState?,
    authenticated: CredentialStatus,
    at: Instant,
    transferred: CredentialStatusNoticeState? = null,
): CredentialStatusNoticeState? {
    val preceding = listOfNotNull(transferred, previous)
    val evidence = entries
    val primary = adverseNoticeKinds.firstNotNullOfOrNull { kind ->
        val matching = evidence.filter { it.meaning == kind }
        matching.firstOrNull { it.lastObservation()?.isFresh(at) == true } ?: matching.firstOrNull()
    }
    val kind = primary?.meaning
    val observation = primary?.lastObservation()
    if (kind != null && observation != null &&
        authenticated.entries.any { it.matches(primary) && !it.hasCheckError && it.lastObservation() === observation } &&
        observation.checkedAt <= at && observation.validUntil?.let { at >= it } != true
    ) {
        return preceding.firstOrNull { it.kind == kind }?.copy(referenceId = primary.referenceId)
            ?: CredentialStatusNoticeState(Uuid.random().toString(), primary.referenceId, kind, observation.checkedAt)
    }
    // Unavailable evidence alone cannot introduce a warning or confirm recovery.
    return preceding.firstOrNull { notice -> evidence.any { it.referenceId == notice.referenceId && it.meaning == notice.kind } }
}
