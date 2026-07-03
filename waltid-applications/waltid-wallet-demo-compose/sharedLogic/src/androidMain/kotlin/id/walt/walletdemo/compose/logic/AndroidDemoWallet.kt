package id.walt.walletdemo.compose.logic

import android.content.Context
import id.walt.wallet2.mobile.MobileWalletConfig
import id.walt.wallet2.mobile.MobileWalletFactory
import id.walt.webdatafetching.WebDataFetcherManager
import id.walt.webdatafetching.WebDataFetchingConfiguration
import id.walt.webdatafetching.config.HttpEngine

fun createAndroidDemoWallet(
    context: Context,
    config: DemoWalletConfig = DemoWalletConfig(),
): DemoWallet {
    WebDataFetcherManager.globalDefaultConfiguration = WebDataFetchingConfiguration(http = HttpEngine.OkHttp)

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
