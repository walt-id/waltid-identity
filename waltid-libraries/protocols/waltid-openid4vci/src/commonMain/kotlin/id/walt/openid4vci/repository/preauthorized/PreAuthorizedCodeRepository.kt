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
    suspend fun get(code: String): PreAuthorizedCodeRecord?

    /**
     * Atomically replace the redemption state only if the stored grant still matches [expected]
     * and is active. Preserve its original expiry and do not insert a missing record.
     * Grant data other than the redemption state is immutable.
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
