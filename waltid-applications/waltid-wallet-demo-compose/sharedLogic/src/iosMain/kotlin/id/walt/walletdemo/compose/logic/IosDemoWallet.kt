package id.walt.walletdemo.compose.logic

import id.walt.wallet2.mobile.MobileWalletConfig
import id.walt.wallet2.mobile.MobileWalletFactory

fun createIosDemoWallet(
    config: DemoWalletConfig = DemoWalletConfig(),
): DemoWallet {

    return LazyDemoWallet {
        MobileDemoWallet(
            MobileWalletFactory().create(
                MobileWalletConfig(
                    walletId = config.walletId,
                    attestationConfig = config.toWalletAttestationConfig(),
                )
            )
        )
    }
}
