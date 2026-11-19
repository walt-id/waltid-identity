package id.walt.wallet2.mobile.swiftinterop

import id.walt.wallet2.mobile.CredentialStatusMaintenanceEnrollment
import id.walt.wallet2.mobile.CredentialStatusMaintenanceResult
import id.walt.wallet2.mobile.MobileWalletCrossProcessAccess
import id.walt.wallet2.mobile.refreshDueStatusesForExistingWallet
import kotlinx.coroutines.CancellationException
import kotlin.time.Duration.Companion.milliseconds

/** Swift bridge value. A null preference means this persistence configuration cannot enroll. */
public data class WalletBridgeStatusMaintenanceEnrollment(
    public val preference: String?,
    public val binding: String?,
)

internal fun CredentialStatusMaintenanceEnrollment?.toMaintenanceBridgeEnrollment() =
    WalletBridgeStatusMaintenanceEnrollment(this?.preference?.name, this?.binding)

/** Redacted result of a single maintenance pass. */
public data class WalletBridgeStatusMaintenanceResult(
    public val outcome: String,
    public val checked: Int = 0,
    public val unchanged: Int = 0,
    public val remaining: Int = 0,
)

/** Independent of WalletSdkBridge construction and its foreground facilities. */
public object WalletStatusMaintenanceBridge {
    /** Performs one bounded pass and propagates caller cancellation through the Swift async bridge. */
    @Throws(CancellationException::class)
    public suspend fun refresh(
        binding: String,
        appGroupIdentifier: String?,
        keychainAccessGroup: String?,
        maxItems: Int,
        timeoutMilliseconds: Long,
    ): WalletBridgeResult<WalletBridgeStatusMaintenanceResult> = walletBridgeCall {
        val access = if (appGroupIdentifier == null && keychainAccessGroup == null) null else
            MobileWalletCrossProcessAccess(requireNotNull(appGroupIdentifier), requireNotNull(keychainAccessGroup))
        when (val result = refreshDueStatusesForExistingWallet(binding, access, maxItems, timeoutMilliseconds.milliseconds)) {
            is CredentialStatusMaintenanceResult.Finished ->
                WalletBridgeStatusMaintenanceResult("FINISHED", result.checked, result.unchanged, result.remaining)
            CredentialStatusMaintenanceResult.Deferred -> WalletBridgeStatusMaintenanceResult("DEFERRED")
            CredentialStatusMaintenanceResult.Ineligible -> WalletBridgeStatusMaintenanceResult("INELIGIBLE")
        }
    }
}
