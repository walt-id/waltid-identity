package id.walt.wallet2.mobile.swiftinterop

import id.walt.wallet2.mobile.CredentialStatusMaintenanceEnrollment
import id.walt.wallet2.mobile.CredentialStatusMaintenanceResult
import id.walt.wallet2.mobile.MobileWalletCrossProcessAccess
import id.walt.wallet2.mobile.refreshDueStatusesForExistingWallet
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

internal fun CredentialStatusMaintenanceEnrollment?.toMaintenanceBridgeEnrollment() =
    WalletBridgeStatusMaintenanceEnrollment(this?.preference?.name, this?.binding)

/**
 * A versioned, wallet-local notice for unlocked UI.
 *
 * @property credentialId Credential to open from the authenticated wallet.
 * @property version Opaque acknowledgement version.
 * @property kind Issuer-reported adverse status meaning.
 * @property checkedAt Original observation time in ISO 8601 format.
 * @property historical Whether to display the observation as last reported.
 */
public data class WalletBridgeCredentialStatusNotice(
    public val credentialId: String,
    public val version: String,
    public val kind: String,
    public val checkedAt: String,
    public val historical: Boolean,
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
        appGroupIdentifier: String?,
        keychainAccessGroup: String?,
        maxItems: Int,
        timeoutMilliseconds: Long,
    ): WalletBridgeResult<WalletBridgeStatusMaintenanceResult> = walletBridgeCall {
        val access = if (appGroupIdentifier == null && keychainAccessGroup == null) null else
            MobileWalletCrossProcessAccess(requireNotNull(appGroupIdentifier), requireNotNull(keychainAccessGroup))
        when (val result = refreshDueStatusesForExistingWallet(binding, access, maxItems, timeoutMilliseconds.milliseconds)) {
            is CredentialStatusMaintenanceResult.Finished ->
                WalletBridgeStatusMaintenanceResult("FINISHED", result.checked, result.unchanged, result.remaining, result.hasNewNotice)
            CredentialStatusMaintenanceResult.Deferred -> WalletBridgeStatusMaintenanceResult("DEFERRED")
            CredentialStatusMaintenanceResult.Ineligible -> WalletBridgeStatusMaintenanceResult("INELIGIBLE")
        }
    }
}
