package id.walt.walletdemo.compose.logic

import android.content.Context
import id.walt.wallet2.mobile.MobileWalletConfig
import id.walt.wallet2.mobile.MobileWalletFactory

fun createAndroidDemoWallet(
    context: Context,
    config: DemoWalletConfig = DemoWalletConfig(),
): DemoWallet {

    return LazyDemoWallet {
        MobileDemoWallet(
            MobileWalletFactory(context).create(
                MobileWalletConfig(
                    walletId = config.walletId,
                    attestationConfig = config.toWalletAttestationConfig(),
                )
            )
        )
    }
}
