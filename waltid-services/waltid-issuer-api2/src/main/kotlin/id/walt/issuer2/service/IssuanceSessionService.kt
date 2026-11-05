package id.walt.issuer2.service

import id.walt.issuer2.domain.IssuanceSession
import id.walt.issuer2.domain.IssuanceSessionFailure
import id.walt.issuer2.domain.IssuanceSessionStatus
import id.walt.issuer2.repository.IssuanceSessionRepository
import io.ktor.server.plugins.NotFoundException

class IssuanceSessionService(
    private val repository: IssuanceSessionRepository,
) {
    val deferredStore: id.walt.openid4vci.deferred.DeferredIssuanceStore = object : id.walt.openid4vci.deferred.DeferredIssuanceStore {
        override suspend fun get(sessionId: String): id.walt.openid4vci.deferred.DeferredSessionSnapshot? =
            repository.get(sessionId)?.let { id.walt.openid4vci.deferred.DeferredSessionSnapshot(it.sessionId, it.expiresAt, it.deferredState) }

        override suspend fun compareAndSet(
            expected: id.walt.openid4vci.deferred.DeferredSessionSnapshot,
            replacement: id.walt.openid4vci.deferred.DeferredIssuanceState,
        ): Boolean {
            val current = repository.get(expected.id) ?: return false
            if (current.deferredState != expected.state) return false
            val denied = replacement.status == id.walt.openid4vci.deferred.DeferredStatus.DENIED
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
            val denied = current.deferredStatus == id.walt.openid4vci.deferred.DeferredStatus.DENIED
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

    suspend fun saveSession(session: IssuanceSession): IssuanceSession {
        if (session.deferredState == null) return repository.save(session)
        while (true) {
            val current = getSession(session.sessionId)
            val state = requireNotNull(current.deferredState)
            val denied = state.status == id.walt.openid4vci.deferred.DeferredStatus.DENIED
            val replacement = session.copy(
                deferredState = state, deferredStatus = state.status, expiresAt = current.expiresAt,
                issuanceResults = current.issuanceResults + session.issuanceResults,
                authorizedCredentialIdentifiers = current.authorizedCredentialIdentifiers ?: session.authorizedCredentialIdentifiers,
                status = if (denied) IssuanceSessionStatus.UNSUCCESSFUL else session.status,
                isClosed = denied || session.isClosed,
            )
            if (repository.compareAndSet(current, replacement)) return replacement
        }
    }

    suspend fun getSession(sessionId: String): IssuanceSession =
        repository.get(sessionId) ?: throw NotFoundException("Issuance session not found: $sessionId")

    suspend fun getSessionOrNull(sessionId: String): IssuanceSession? = repository.get(sessionId)

    suspend fun removeSession(sessionId: String) = repository.remove(sessionId)

    suspend fun claimSession(sessionId: String): IssuanceSession? = repository.take(sessionId)

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
