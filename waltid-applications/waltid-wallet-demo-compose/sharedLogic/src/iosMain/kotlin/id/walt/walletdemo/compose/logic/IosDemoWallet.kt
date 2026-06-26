package id.walt.walletdemo.compose.logic

import id.walt.wallet2.mobile.MobileWalletConfig
import id.walt.wallet2.mobile.MobileWalletFactory
import id.walt.webdatafetching.WebDataFetcherManager
import id.walt.webdatafetching.WebDataFetchingConfiguration
import id.walt.webdatafetching.config.HttpEngine

fun createIosDemoWallet(
    config: DemoWalletConfig = DemoWalletConfig(),
): DemoWallet {
    WebDataFetcherManager.globalDefaultConfiguration = WebDataFetchingConfiguration(http = HttpEngine.Native)

    return MobileDemoWallet(
        MobileWalletFactory().create(
            MobileWalletConfig(
                walletId = config.walletId,
                attestationConfig = config.toWalletAttestationConfig(),
            )
        )
    )
}
