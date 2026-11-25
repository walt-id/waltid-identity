package id.walt.issuer2.service

import id.walt.issuer2.domain.IssuanceSession
import id.walt.issuer2.domain.IssuanceSessionFailure
import id.walt.issuer2.domain.IssuanceSessionStatus
import id.walt.issuer2.domain.CredentialStatusIssuanceState
import id.walt.issuer2.domain.CredentialStatusIssuanceOperation
import id.walt.issuer2.repository.CredentialStatusOperationException
import id.walt.issuer2.repository.IssuanceSessionRepository
import id.walt.openid4vci.deferred.DeferredIssuanceStore
import id.walt.openid4vci.deferred.DeferredSessionSnapshot
import id.walt.openid4vci.deferred.DeferredIssuanceState
import id.walt.openid4vci.deferred.DeferredStatus
import id.walt.openid4vci.requests.notification.IssuedCredentialNotificationChange
import io.ktor.server.plugins.NotFoundException
import id.walt.openid4vci.responses.credential.CredentialResponse
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class IssuanceSessionService(
    private val repository: IssuanceSessionRepository,
    private val now: () -> Instant = { Clock.System.now() },
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

    /** Persist newly issued results while preserving concurrent transaction checkpoints and wallet reports. */
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
            check(current.issuanceClaimId == null) { "Issuance session is being processed" }
            val state = current.deferredState
            val denied = state?.status == DeferredStatus.DENIED
            val replacement = session.copy(
                deferredState = state, deferredStatus = state?.status ?: session.deferredStatus,
                expiresAt = if (state == null && !current.requiresCredentialStatusLease) session.expiresAt else current.expiresAt,
                credentialStatusIssuance = current.credentialStatusIssuance,
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
            check(current.issuanceClaimId == null) { "Issuance session is being processed" }
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
            check(current.issuanceClaimId == claimId) { "Issuance session claim has changed" }
            val replacement = session.copy(
                issuanceResults = current.issuanceResults + session.issuanceResults,
                issuanceClaimId = null,
            )
            if (repository.compareAndSet(current, replacement)) return replacement
        }
    }

    /** A persistent claim retains the session and key sidecar if the issuing process dies. */
    suspend fun claimCredentialStatusSession(sessionId: String): IssuanceSession? {
        while (true) {
            val current = repository.get(sessionId) ?: return null
            if (current.isClosed || current.expiresAt <= now()) return null
            val state = current.credentialStatusIssuance ?: CredentialStatusIssuanceState()
            if (state.leaseId != null && state.leaseExpiresAt?.let { it > now() } == true) return null
            val replacement = current.copy(credentialStatusIssuance = state.copy(
                leaseId = UUID.randomUUID().toString(), leaseExpiresAt = now() + 5.minutes,
            ))
            if (repository.compareAndSet(current, replacement)) return replacement
        }
    }

    suspend fun prepareCredentialStatusOperation(
        claim: IssuanceSession,
        credentialIdentifier: String,
        holderKeys: List<String>,
    ): CredentialStatusIssuanceOperation {
        val saved = updateCredentialStatusClaim(claim) { current ->
            val state = requireNotNull(current.credentialStatusIssuance)
            val operation = state.operations[credentialIdentifier]
                ?: CredentialStatusIssuanceOperation(UUID.randomUUID().toString(), holderKeys)
            if (operation.holderKeys != holderKeys) {
                throw CredentialStatusOperationException("Credential request conflicts with its pending status operation")
            }
            current.copy(credentialStatusIssuance = state.copy(
                operations = state.operations + (credentialIdentifier to operation),
            ))
        }
        return saved.credentialStatusIssuance!!.operations.getValue(credentialIdentifier)
    }

    suspend fun checkpointCredentialStatusResponse(
        claim: IssuanceSession,
        credentialIdentifier: String,
        operationId: String,
        response: CredentialResponse,
    ) {
        updateCredentialStatusClaim(claim) { current ->
            val state = requireNotNull(current.credentialStatusIssuance)
            val operation = state.operations.getValue(credentialIdentifier)
            check(operation.id == operationId) { "Credential status operation changed" }
            current.copy(credentialStatusIssuance = state.copy(
                operations = state.operations + (credentialIdentifier to operation.copy(response = response)),
            ))
        }
    }

    suspend fun discardUnreservedCredentialStatusOperation(claim: IssuanceSession, credentialIdentifier: String) {
        updateCredentialStatusClaim(claim) { current ->
            val state = requireNotNull(current.credentialStatusIssuance)
            current.copy(credentialStatusIssuance = state.copy(operations = state.operations - credentialIdentifier))
        }
    }

    suspend fun releaseCredentialStatusSession(
        claim: IssuanceSession,
        update: (IssuanceSession) -> IssuanceSession = { it },
    ): IssuanceSession = updateCredentialStatusClaim(claim, release = true, transform = update)

    suspend fun finishCredentialStatusSession(
        claim: IssuanceSession,
        credentialIdentifier: String,
        recordIssuance: (IssuanceSession) -> IssuanceSession,
    ): IssuanceSession = updateCredentialStatusClaim(claim, release = true) { current ->
        val state = requireNotNull(current.credentialStatusIssuance)
        recordIssuance(current).copy(
            credentialStatusIssuance = state.copy(operations = state.operations - credentialIdentifier),
        )
    }

    private suspend fun updateCredentialStatusClaim(
        claim: IssuanceSession,
        release: Boolean = false,
        transform: (IssuanceSession) -> IssuanceSession,
    ): IssuanceSession {
        val claimId = requireNotNull(claim.credentialStatusIssuance?.leaseId)
        while (true) {
            val current = repository.get(claim.sessionId)
                ?: throw CredentialStatusOperationException("Credential status processing session expired")
            val state = current.credentialStatusIssuance
            if (state?.leaseId != claimId || state.leaseExpiresAt?.let { it <= now() } != false ||
                current.expiresAt <= now() || current.isClosed) {
                throw CredentialStatusOperationException("Credential status processing lease was lost")
            }
            val replacement = transform(current).let { transformed ->
                transformed.copy(credentialStatusIssuance = requireNotNull(transformed.credentialStatusIssuance).copy(
                    leaseId = if (release) null else claimId,
                    leaseExpiresAt = if (release) null else now() + 5.minutes,
                ))
            }
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
