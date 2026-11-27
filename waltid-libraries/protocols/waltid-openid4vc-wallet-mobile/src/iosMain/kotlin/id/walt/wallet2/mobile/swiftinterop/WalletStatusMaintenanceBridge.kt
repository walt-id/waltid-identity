package id.walt.wallet2.mobile.swiftinterop

import id.walt.wallet2.mobile.credentials.CredentialStatusMaintenanceEnrollment
import id.walt.wallet2.mobile.credentials.CredentialStatusMaintenanceResult
import id.walt.wallet2.mobile.credentials.refreshDueStatusesForExistingWallet
import id.walt.wallet2.mobile.digitalcredentials.WalletAppGroupAccess
import kotlinx.coroutines.CancellationException
import kotlin.time.Duration.Companion.milliseconds

/**
 * Enrollment information for the Swift facade.
 *
 * @property preference The saved preference, or null when the persistence configuration cannot enroll.
 * @property binding The opaque grant for the current wallet when maintenance is enabled.
 */
public data class WalletBridgeStatusMaintenanceEnrollment(
    public val preference: String?,
    public val binding: String?,
)

/**
 * Redacted result of a single maintenance pass.
 *
 * @property outcome Whether the pass finished, deferred, or found an ineligible wallet.
 * @property checked Completed attempts, including attempts with per-credential errors.
 * @property unchanged Entries that needed no new observation or lost a concurrent update.
 * @property remaining Entries deferred or left unexamined in this inventory rotation.
 * @property hasNewNotice Whether this pass committed a new retainable adverse change.
 */
public data class WalletBridgeStatusMaintenanceResult(
    public val outcome: String,
    public val checked: Int = 0,
    public val unchanged: Int = 0,
    public val remaining: Int = 0,
    public val hasNewNotice: Boolean = false,
)

/** Independent of WalletSdkBridge construction and its foreground facilities. */
public object WalletStatusMaintenanceBridge {
    /** Performs one bounded pass and propagates caller cancellation through the Swift async bridge. */
    @Throws(CancellationException::class)
    public suspend fun refresh(
        binding: String,
        crossProcessAccess: WalletAppGroupAccess?,
        maxItems: Int,
        timeoutMilliseconds: Long,
    ): WalletBridgeResult<WalletBridgeStatusMaintenanceResult> = walletBridgeCall {
        when (val result = refreshDueStatusesForExistingWallet(binding, crossProcessAccess, maxItems, timeoutMilliseconds.milliseconds)) {
            is CredentialStatusMaintenanceResult.Finished ->
                WalletBridgeStatusMaintenanceResult("FINISHED", result.checked, result.unchanged, result.remaining, result.hasNewNotice)
            CredentialStatusMaintenanceResult.Deferred -> WalletBridgeStatusMaintenanceResult("DEFERRED")
            CredentialStatusMaintenanceResult.Ineligible -> WalletBridgeStatusMaintenanceResult("INELIGIBLE")
        }
    }
}

internal fun CredentialStatusMaintenanceEnrollment?.toMaintenanceBridgeEnrollment() =
    WalletBridgeStatusMaintenanceEnrollment(this?.preference?.name, this?.binding)
