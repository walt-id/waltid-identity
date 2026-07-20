package id.walt.walletdemo.compose.logic

import id.walt.wallet2.mobile.MobileWalletConfig
import id.walt.wallet2.mobile.MobileWalletFactory
import platform.Foundation.NSBundle

fun createIosDemoWallet(
    config: DemoWalletConfig = DemoWalletConfig(),
): DemoWallet {

    return LazyDemoWallet {
        val transactionDataProfiles = config.resolveDemoTransactionDataProfiles()
        MobileDemoWallet(
            MobileWalletFactory().create(
                MobileWalletConfig(
                    walletId = config.walletId,
                    attestationConfig = config.toWalletAttestationConfig(),
                    transactionDataProfiles = transactionDataProfiles.profiles,
                    preferredLocales = NSBundle.mainBundle.preferredLocalizations.mapNotNull { it as? String },
                )
            ),
            warning = transactionDataProfiles.warning,
        )
    }
}
