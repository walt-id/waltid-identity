package id.walt.walletdemo.compose.logic

import id.walt.wallet2.persistence.keys.KeyUseAuthorizationPolicy
import id.walt.wallet2.persistence.keys.KeyUseAuthorizationPrompt
import id.walt.wallet2.mobile.MobileWalletConfig
import id.walt.wallet2.mobile.MobileWalletCrossProcessAccess
import id.walt.wallet2.mobile.MobileWalletFactory
import id.walt.mdoc.proximity.mobile.NfcHostPlatformAdapter
import platform.Foundation.NSLocale
import platform.Foundation.preferredLanguages

/**
 * Creates the Compose demo's iOS wallet.
 *
 * @param crossProcessAccess Apple App Group and shared Keychain group so the document-provider
 * extension opens this same wallet. Only the Swift host can resolve them - the Keychain group needs a
 * build-expanded Team ID - so they are passed in rather than defaulted here, and they stay off the
 * portable [DemoWalletConfig] because they describe Apple host configuration, not demo behavior.
 * @param nfcHostPlatformAdapter Swift-owned Core NFC adapter shared with the native demo. Keeping
 * CardSession outside Kotlin limits common code to protocol state and APDU routing.
 * @param onDigitalCredentialRegistryChanged Called after the wallet re-published its desired Apple
 * registration state. Writing Apple's registration store needs `IdentityDocumentServices`, which only
 * the Swift host may call, so the host reconciles here; see [MobileWalletConfig].
 */
fun createIosDemoWallet(
    config: DemoWalletConfig = DemoWalletConfig(),
    crossProcessAccess: MobileWalletCrossProcessAccess,
    nfcHostPlatformAdapter: NfcHostPlatformAdapter,
    onDigitalCredentialRegistryChanged: suspend () -> Unit,
): ProximityDemoWallet {

    return LazyProximityDemoWallet {
        val transactionDataProfiles = config.resolveDemoTransactionDataProfiles()
        MobileDemoWallet(
            MobileWalletFactory(nfcHostPlatformAdapter).create(
                MobileWalletConfig(
                    walletId = config.walletId,
                    attestationConfig = config.toWalletAttestationConfig(),
                    transactionDataProfiles = transactionDataProfiles.profiles,
                    preferredLocales = NSLocale.preferredLanguages.mapNotNull { it as? String },
                    crossProcessAccess = crossProcessAccess,
                    onDigitalCredentialRegistryChanged = onDigitalCredentialRegistryChanged,
                    defaultKeyUseAuthorizationPolicy =
                        config.signingProtectionMode.defaultSelection.toKeyUseAuthorizationPolicy(),
                    keyUseAuthorizationPrompt = KeyUseAuthorizationPrompt(
                        reason = "Authorize wallet signing",
                        cancelText = "Cancel",
                    ),
                )
            ),
            warning = transactionDataProfiles.warning,
        )
    }
}
