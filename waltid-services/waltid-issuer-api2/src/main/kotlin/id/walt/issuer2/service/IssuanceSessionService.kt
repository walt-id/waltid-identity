package id.walt.issuer2.service

import id.walt.issuer2.domain.IssuanceSession
import id.walt.issuer2.domain.IssuanceSessionFailure
import id.walt.issuer2.domain.IssuanceSessionStatus
import id.walt.issuer2.repository.IssuanceSessionRepository
import id.walt.openid4vci.requests.notification.IssuedCredentialNotificationUpdate
import io.ktor.server.plugins.NotFoundException

class IssuanceSessionService(
    private val repository: IssuanceSessionRepository,
) {
    suspend fun createSession(session: IssuanceSession): IssuanceSession = repository.save(session)

    suspend fun saveSession(session: IssuanceSession): IssuanceSession = repository.save(session)

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
        return repository.save(updated)
    }
}

/** Applies the library's resolved change to the stored record. */
internal fun IssuanceSession.applyWalletNotification(update: IssuedCredentialNotificationUpdate.Changed): IssuanceSession {
    val result = requireNotNull(issuanceResults[update.credentialIdentifier])
    return copy(issuanceResults = issuanceResults + (update.credentialIdentifier to result.copy(
        walletNotification = requireNotNull(result.walletNotification).copy(
            event = update.event,
            eventDescription = update.eventDescription,
        ),
    )))
}
