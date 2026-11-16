package id.walt.openid4vci.repository.preauthorized

import id.walt.openid4vci.repository.authorization.DuplicateCodeException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock

/**
 * Storage abstraction for pre-authorized code sessions.
 */
interface PreAuthorizedCodeRepository {
    @Throws(DuplicateCodeException::class, CancellationException::class)
    suspend fun save(record: PreAuthorizedCodeRecord)

    /** Read an unexpired, active grant and its current redemption state. */
    suspend fun get(code: String): PreAuthorizedCodeRecord?

    /**
     * Token-handler lookup. Adapters can include unexpired locked grants so callers can reconcile a
     * terminal issuance session after an interrupted request. Redeemed grants are excluded.
     */
    suspend fun getForRedemption(code: String): PreAuthorizedCodeRecord? = get(code)

    /**
     * Callers must pass the unchanged record instance returned by [get] or [getForRedemption]
     * in the same repository scope. If the update fails, read a fresh record before retrying.
     * The in-memory adapter checks object identity. Persistent adapters check stored values or a
     * storage version.
     *
     * Atomically match the same immutable grant (storage namespace, code and original expiry, or a
     * stronger identity/version) and the same redemption-state snapshot as [expected]. A null state
     * represents a legacy active grant with zero attempts.
     *
     * Apply [replacement] only to an existing, unexpired, active grant. Retain the payload, including
     * application data, and the original expiry. Return false for a missing, expired, terminal or
     * changed snapshot. Both the token handler and [consume] use this operation.
     */
    suspend fun compareAndSetRedemptionState(
        expected: PreAuthorizedCodeRecord,
        replacement: PreAuthorizedCodeRedemptionState,
    ): Boolean

    /**
     * Claims a code after the trusted caller has performed any required authentication.
     * Token handlers must validate the transaction code before claiming the grant.
     */
    suspend fun consume(code: String): PreAuthorizedCodeRecord? {
        while (true) {
            val current = get(code)?.takeIf { it.isRedeemableAt(Clock.System.now()) } ?: return null
            val redeemed = PreAuthorizedCodeRedemptionState(
                current.redemptionState?.failedAttempts ?: 0,
                PreAuthorizedCodeStatus.REDEEMED,
            )
            if (compareAndSetRedemptionState(current, redeemed)) {
                return current.takeIf { it.isRedeemableAt(Clock.System.now()) }
            }
        }
    }
}
