package id.walt.issuer2.service

import id.walt.issuer2.domain.IssuanceSession
import id.walt.issuer2.domain.IssuanceSessionFailure
import id.walt.issuer2.domain.IssuanceSessionStatus
import id.walt.issuer2.repository.IssuanceSessionRepository
import id.walt.openid4vci.requests.notification.IssuedCredentialNotification
import id.walt.openid4vci.requests.notification.IssuedCredentialNotificationUpdate
import id.walt.openid4vci.requests.notification.NotificationEvent
import id.walt.openid4vci.requests.notification.applyIssuedCredentialNotification
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

internal fun IssuanceSession.issuedCredentialNotifications(): List<IssuedCredentialNotification> =
    issuanceResults.mapNotNull { (identifier, result) ->
        val notificationId = result.walletNotificationId ?: return@mapNotNull null
        IssuedCredentialNotification(
            credentialIdentifier = identifier,
            notificationId = notificationId,
            event = result.walletNotificationEvent,
            eventDescription = result.walletNotificationEventDescription,
        )
    }

internal fun IssuanceSession.applyWalletNotification(
    notificationId: String,
    event: NotificationEvent,
    eventDescription: String?,
    authorizedCredentialIdentifiers: Set<String>?,
): WalletNotificationUpdate? =
    when (
        val update = applyIssuedCredentialNotification(
            issued = issuedCredentialNotifications(),
            notificationId = notificationId,
            event = event,
            eventDescription = eventDescription,
            authorizedCredentialIdentifiers = authorizedCredentialIdentifiers,
        )
    ) {
        IssuedCredentialNotificationUpdate.UnknownNotificationId -> null
        IssuedCredentialNotificationUpdate.Unchanged -> WalletNotificationUpdate(this, changed = false)
        is IssuedCredentialNotificationUpdate.Changed -> WalletNotificationUpdate(
            copy(
                issuanceResults = issuanceResults + (
                    update.credentialIdentifier to requireNotNull(issuanceResults[update.credentialIdentifier]).copy(
                        walletNotificationEvent = update.event,
                        walletNotificationEventDescription = update.eventDescription,
                    )
                )
            ),
            changed = true,
        )
    }

data class WalletNotificationUpdate(
    val session: IssuanceSession,
    val changed: Boolean,
)
