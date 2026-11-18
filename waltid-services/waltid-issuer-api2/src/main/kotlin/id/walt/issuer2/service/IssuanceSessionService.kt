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
import io.ktor.server.plugins.NotFoundException
import id.walt.openid4vci.responses.credential.CredentialResponse
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

    suspend fun saveDeferredProgress(session: IssuanceSession): IssuanceSession = saveIssuanceProgress(session)

    /** Preserve concurrent processing checkpoints when recording selection or issuance results. */
    suspend fun saveIssuanceProgress(session: IssuanceSession): IssuanceSession {
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

    suspend fun saveSession(session: IssuanceSession): IssuanceSession {
        if (session.deferredState == null && !session.requiresCredentialStatusLease) return repository.save(session)
        while (true) {
            val current = getSession(session.sessionId)
            val state = current.deferredState
            val denied = state?.status == DeferredStatus.DENIED
            val replacement = session.copy(
                deferredState = state, deferredStatus = state?.status, expiresAt = current.expiresAt,
                credentialStatusIssuance = current.credentialStatusIssuance,
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
        count: Int,
    ): CredentialStatusIssuanceOperation {
        val saved = updateCredentialStatusClaim(claim) { current ->
            val state = requireNotNull(current.credentialStatusIssuance)
            val operation = state.operations[credentialIdentifier]
                ?: CredentialStatusIssuanceOperation(UUID.randomUUID().toString(), holderKeys, count)
            if (operation.holderKeys != holderKeys || operation.count != count) {
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

    suspend fun releaseCredentialStatusSession(claim: IssuanceSession): IssuanceSession =
        finishCredentialStatusSession(claim, completedCredentialIdentifier = null)

    suspend fun finishCredentialStatusSession(
        claim: IssuanceSession,
        completedCredentialIdentifier: String?,
    ): IssuanceSession = updateCredentialStatusClaim(claim, release = true) { current ->
        val state = requireNotNull(current.credentialStatusIssuance)
        claim.copy(
            issuanceResults = current.issuanceResults + claim.issuanceResults,
            authorizedCredentialIdentifiers = current.authorizedCredentialIdentifiers ?: claim.authorizedCredentialIdentifiers,
            credentialStatusIssuance = state.copy(
                operations = if (completedCredentialIdentifier == null) state.operations
                    else state.operations - completedCredentialIdentifier,
            ),
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
