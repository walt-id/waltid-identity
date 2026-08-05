package id.walt.walletdemo.compose.logic

import android.content.Context
import android.os.LocaleList
import id.walt.wallet2.mobile.MobileWalletConfig
import id.walt.wallet2.mobile.MobileWalletFactory

fun createAndroidDemoWallet(
    context: Context,
    config: DemoWalletConfig = DemoWalletConfig(),
): DemoWallet {

    return LazyDemoWallet {
        val transactionDataProfiles = config.resolveDemoTransactionDataProfiles()
        MobileDemoWallet(
            MobileWalletFactory(context).create(
                MobileWalletConfig(
                    walletId = config.walletId,
                    attestationConfig = config.toWalletAttestationConfig(),
                    transactionDataProfiles = transactionDataProfiles.profiles,
                    preferredLocales = LocaleList.getDefault().let { locales ->
                        List(locales.size()) { index -> locales[index].toLanguageTag() }
                    },
                )
            ),
            warning = transactionDataProfiles.warning,
        )
    }
}
