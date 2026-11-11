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
}

internal sealed interface PreAuthorizedCodeRedemption {
    data class Redeemed(val record: PreAuthorizedCodeRecord) : PreAuthorizedCodeRedemption
    data object WrongTxCode : PreAuthorizedCodeRedemption
    data object Invalid : PreAuthorizedCodeRedemption
}

internal suspend fun PreAuthorizedCodeRepository.redeem(
    record: PreAuthorizedCodeRecord,
    providedTxCodeHash: String?,
    maxFailedAttempts: Int,
): PreAuthorizedCodeRedemption {
    var current = record
    while (current.isRedeemableAt(Clock.System.now())) {
        val expectedHash = current.txCodeValue
        val requiresTxCode = current.txCode != null
        val hasStoredVerifier = !expectedHash.isNullOrBlank()
        // Fail closed on an incomplete stored grant or a submission that does not match its shape.
        if (requiresTxCode != hasStoredVerifier || requiresTxCode != (providedTxCodeHash != null)) {
            return PreAuthorizedCodeRedemption.Invalid
        }
        val failures = current.redemptionState?.failedAttempts ?: 0
        if (failures >= maxFailedAttempts) return PreAuthorizedCodeRedemption.Invalid

        val matches = expectedHash == providedTxCodeHash
        val next = when {
            matches -> PreAuthorizedCodeRedemptionState(failures, PreAuthorizedCodeStatus.REDEEMED)
            failures + 1 >= maxFailedAttempts ->
                PreAuthorizedCodeRedemptionState(failures + 1, PreAuthorizedCodeStatus.LOCKED)
            else -> PreAuthorizedCodeRedemptionState(failures + 1, PreAuthorizedCodeStatus.ACTIVE)
        }
        if (compareAndSetRedemptionState(current, next)) {
            return when {
                !current.isRedeemableAt(Clock.System.now()) -> PreAuthorizedCodeRedemption.Invalid
                matches -> PreAuthorizedCodeRedemption.Redeemed(current)
                next.status == PreAuthorizedCodeStatus.LOCKED -> PreAuthorizedCodeRedemption.Invalid
                else -> PreAuthorizedCodeRedemption.WrongTxCode
            }
        }
        current = get(record.code) ?: return PreAuthorizedCodeRedemption.Invalid
    }
    return PreAuthorizedCodeRedemption.Invalid
}
