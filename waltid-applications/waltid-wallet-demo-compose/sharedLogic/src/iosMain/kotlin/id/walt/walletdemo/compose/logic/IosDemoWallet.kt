package id.walt.walletdemo.compose.logic

import id.walt.wallet2.mobile.MobileWalletConfig
import id.walt.wallet2.mobile.MobileWalletCrossProcessAccess
import id.walt.wallet2.mobile.MobileWalletFactory
import platform.Foundation.NSLocale
import platform.Foundation.preferredLanguages

/**
 * Creates the Compose demo's iOS wallet.
 *
 * @param crossProcessAccess Apple App Group and shared Keychain group so the document-provider
 * extension opens this same wallet. Only the Swift host can resolve them - the Keychain group needs a
 * build-expanded Team ID - so they are passed in rather than defaulted here, and they stay off the
 * portable [DemoWalletConfig] because they describe Apple host configuration, not demo behavior.
 */
fun createIosDemoWallet(
    config: DemoWalletConfig = DemoWalletConfig(),
    crossProcessAccess: MobileWalletCrossProcessAccess? = null,
): DemoWallet {

    return LazyDemoWallet {
        val transactionDataProfiles = config.resolveDemoTransactionDataProfiles()
        MobileDemoWallet(
            MobileWalletFactory().create(
                MobileWalletConfig(
                    walletId = config.walletId,
                    attestationConfig = config.toWalletAttestationConfig(),
                    transactionDataProfiles = transactionDataProfiles.profiles,
                    preferredLocales = NSLocale.preferredLanguages.mapNotNull { it as? String },
                    crossProcessAccess = crossProcessAccess,
                )
            ),
            warning = transactionDataProfiles.warning,
        )
    }
}
