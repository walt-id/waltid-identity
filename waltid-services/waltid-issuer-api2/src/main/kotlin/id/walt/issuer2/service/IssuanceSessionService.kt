package id.walt.issuer2.service

import id.walt.errors.ClientStateConflictException
import id.walt.issuer2.domain.IssuanceSession
import id.walt.issuer2.domain.IssuanceSessionFailure
import id.walt.issuer2.domain.IssuanceSessionStatus
import id.walt.issuer2.repository.IssuanceSessionRepository
import id.walt.openid4vci.deferred.DeferredIssuanceStore
import id.walt.openid4vci.deferred.DeferredSessionSnapshot
import id.walt.openid4vci.deferred.DeferredIssuanceState
import id.walt.openid4vci.deferred.DeferredStatus
import id.walt.openid4vci.requests.notification.IssuedCredentialNotificationChange
import io.ktor.server.plugins.NotFoundException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.util.UUID

class IssuanceSessionService(
    private val repository: IssuanceSessionRepository,
) {
    val deferredStore: DeferredIssuanceStore = object : DeferredIssuanceStore {
        override suspend fun get(id: String): DeferredSessionSnapshot? =
            repository.get(id)?.let { DeferredSessionSnapshot(it.sessionId, it.expiresAt, it.deferredState) }

        override suspend fun compareAndSet(
            expected: DeferredSessionSnapshot,
            replacement: DeferredIssuanceState,
        ): Boolean {
            val current = repository.get(expected.id) ?: return false
            if (current.deferredState != expected.state) return false
            val denied = replacement.status == DeferredStatus.DENIED
            return repository.compareAndSet(current, current.copy(
                deferredState = replacement, deferredStatus = replacement.status,
                expiresAt = replacement.deadline ?: current.expiresAt,
                status = if (denied) IssuanceSessionStatus.UNSUCCESSFUL else current.status,
                isClosed = if (denied) true else current.isClosed,
                statusReason = if (denied) "Credential issuance denied" else current.statusReason,
            ))
        }
    }

    /** Preserve concurrent transaction checkpoints when recording issuance results. */
    suspend fun saveDeferredProgress(session: IssuanceSession): IssuanceSession {
        while (true) {
            val current = getSession(session.sessionId)
            val results = current.issuanceResults + session.issuanceResults
            val selected = current.authorizedCredentialIdentifiers ?: session.authorizedCredentialIdentifiers
            val complete = !selected.isNullOrEmpty() && selected.all { it in results }
            val denied = current.deferredStatus == DeferredStatus.DENIED
            val updated = current.copy(
                issuanceResults = results, authorizedCredentialIdentifiers = selected,
                status = if (denied) IssuanceSessionStatus.UNSUCCESSFUL else if (complete) IssuanceSessionStatus.SUCCESSFUL else session.status,
                statusReason = if (denied) current.statusReason else session.statusReason,
                isClosed = denied || session.isClosed,
            )
            if (repository.compareAndSet(current, updated)) return updated
        }
    }

    suspend fun createSession(session: IssuanceSession): IssuanceSession = repository.save(session)

    internal suspend fun trySaveWalletNotification(
        current: IssuanceSession,
        change: IssuedCredentialNotificationChange,
    ): IssuanceSession? {
        val result = requireNotNull(current.issuanceResults[change.credentialIdentifier])
        val updatedResult = result.copy(
            walletNotification = requireNotNull(result.walletNotification).copy(
                event = change.event,
                eventDescription = change.eventDescription,
            ),
        )
        val updated = current.copy(
            issuanceResults = current.issuanceResults + (change.credentialIdentifier to updatedResult),
        )
        return if (repository.compareAndSet(current, updated)) updated else null
    }

    suspend fun saveSession(session: IssuanceSession): IssuanceSession {
        while (true) {
            currentCoroutineContext().ensureActive()
            val current = getSession(session.sessionId)
            if (current.issuanceClaimId != null) throw ClientStateConflictException("Issuance session is being processed")
            val state = current.deferredState
            val denied = state?.status == DeferredStatus.DENIED
            val replacement = session.copy(
                deferredState = state, deferredStatus = state?.status ?: session.deferredStatus,
                expiresAt = if (state == null) session.expiresAt else current.expiresAt,
                issuanceResults = session.issuanceResults + current.issuanceResults,
                authorizedCredentialIdentifiers = current.authorizedCredentialIdentifiers ?: session.authorizedCredentialIdentifiers,
                status = if (denied) IssuanceSessionStatus.UNSUCCESSFUL else session.status,
                isClosed = denied || session.isClosed,
                issuanceClaimId = null,
            )
            if (repository.compareAndSet(current, replacement)) return replacement
        }
    }

    internal suspend fun establishIssuanceSelection(sessionId: String, identifiers: List<String>): IssuanceSession {
        while (true) {
            currentCoroutineContext().ensureActive()
            val current = getSession(sessionId)
            current.authorizedCredentialIdentifiers?.let { selected ->
                require(identifiers.all { it in selected }) { "Token exceeds the established issuance selection" }
                return current
            }
            if (current.issuanceClaimId != null) throw ClientStateConflictException("Issuance session is being processed")
            val selected = current.issuanceRequests.map { it.credentialIdentifier }.filter { it in identifiers }
            require(identifiers.all { it in selected }) { "Token exceeds the established issuance selection" }
            val replacement = current.copy(authorizedCredentialIdentifiers = selected)
            if (repository.compareAndSet(current, replacement)) return replacement
        }
    }

    suspend fun getSession(sessionId: String): IssuanceSession =
        repository.get(sessionId) ?: throw NotFoundException("Issuance session not found: $sessionId")

    suspend fun getSessionOrNull(sessionId: String): IssuanceSession? = repository.get(sessionId)

    suspend fun removeSession(sessionId: String) = repository.remove(sessionId)

    internal suspend fun claimSession(sessionId: String): IssuanceSession? {
        val claimId = UUID.randomUUID().toString()
        while (true) {
            currentCoroutineContext().ensureActive()
            val current = repository.get(sessionId) ?: return null
            if (current.issuanceClaimId != null || current.isClosed) return null
            val claimed = current.copy(issuanceClaimId = claimId)
            if (repository.compareAndSet(current, claimed)) return claimed
        }
    }

    /** [session] contains only newly issued results. Concurrent wallet reports remain in the live record. */
    internal suspend fun finishClaimedSession(session: IssuanceSession): IssuanceSession {
        val claimId = requireNotNull(session.issuanceClaimId)
        while (true) {
            val current = getSession(session.sessionId)
            if (current.issuanceClaimId != claimId) throw ClientStateConflictException("Issuance session claim has changed")
            val replacement = session.copy(
                issuanceResults = current.issuanceResults + session.issuanceResults,
                issuanceClaimId = null,
            )
            if (repository.compareAndSet(current, replacement)) return replacement
        }
    }

    suspend fun listSessions(): List<IssuanceSession> = repository.list()

    suspend fun findByExternalAuthorizationState(state: String): IssuanceSession? =
        repository.list().firstOrNull { it.externalAuthorizationState == state }

    suspend fun updateStatus(
        sessionId: String,
        status: IssuanceSessionStatus,
        reason: String? = null,
        close: Boolean = false,
        failure: IssuanceSessionFailure? = null,
    ): IssuanceSession {
        val existing = getSession(sessionId)
        val updated = existing.copy(
            status = status,
            statusReason = reason,
            isClosed = existing.isClosed || close,
            failure = failure ?: existing.failure,
        )
        return saveSession(updated)
    }
}
