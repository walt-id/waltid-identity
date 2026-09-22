package id.walt.issuer2.service

import id.walt.issuer2.domain.IssuanceSession
import id.walt.issuer2.domain.IssuanceSessionFailure
import id.walt.issuer2.domain.IssuanceSessionStatus
import id.walt.issuer2.repository.IssuanceSessionRepository
import id.walt.openid4vci.requests.notification.NotificationEvent
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
        issuedCredentialFormat: String? = null,
        close: Boolean = false,
        failure: IssuanceSessionFailure? = null,
    ): IssuanceSession {
        val existing = getSession(sessionId)
        val updated = existing.copy(
            status = status,
            statusReason = reason,
            issuedCredentialFormat = issuedCredentialFormat ?: existing.issuedCredentialFormat,
            isClosed = existing.isClosed || close,
            failure = failure ?: existing.failure,
        )
        return repository.save(updated)
    }

    /**
     * Records the latest wallet notification for [notificationId].
     * An identical event and description is left unchanged. A different event replaces the previous one.
     * Returns null when this session was not issued with that notification id.
     */
    suspend fun updateWalletNotificationEvent(
        sessionId: String,
        notificationId: String,
        event: NotificationEvent,
        eventDescription: String? = null,
    ): WalletNotificationUpdate? {
        val existing = getSessionOrNull(sessionId) ?: return null
        if (existing.walletNotificationId != notificationId) return null
        if (
            existing.walletNotificationEvent == event &&
            existing.walletNotificationEventDescription == eventDescription
        ) {
            return WalletNotificationUpdate(existing, changed = false)
        }
        val saved = repository.save(
            existing.copy(
                walletNotificationEvent = event,
                walletNotificationEventDescription = eventDescription,
            )
        )
        return WalletNotificationUpdate(saved, changed = true)
    }
}

data class WalletNotificationUpdate(
    val session: IssuanceSession,
    val changed: Boolean,
)
