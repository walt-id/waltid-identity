package id.walt.walletdemo.compose.logic

import android.content.Context
import android.os.LocaleList
import androidx.fragment.app.FragmentActivity
import id.walt.crypto.keys.KeyUseAuthorizationPolicy
import id.walt.crypto.keys.KeyUseAuthorizationPrompt
import id.walt.wallet2.mobile.MobileWalletConfig
import id.walt.wallet2.mobile.MobileWalletFactory

fun createAndroidDemoWallet(
    context: Context,
    config: DemoWalletConfig = DemoWalletConfig(),
    interactionContextProvider: () -> FragmentActivity? = { null },
): DemoWallet {

    return LazyDemoWallet {
        val transactionDataProfiles = config.resolveDemoTransactionDataProfiles()
        MobileDemoWallet(
            MobileWalletFactory(context, interactionContextProvider).create(
                MobileWalletConfig(
                    walletId = config.walletId,
                    attestationConfig = config.toWalletAttestationConfig(),
                    transactionDataProfiles = transactionDataProfiles.profiles,
                    preferredLocales = LocaleList.getDefault().let { locales ->
                        List(locales.size()) { index -> locales[index].toLanguageTag() }
                    },
                    defaultKeyUseAuthorizationPolicy = if (config.biometricEnabled) {
                        KeyUseAuthorizationPolicy.BiometricCurrentSet
                    } else {
                        KeyUseAuthorizationPolicy.None
                    },
                    keyUseAuthorizationPrompt = KeyUseAuthorizationPrompt(
                        message = "Authorize wallet signing",
                        cancelText = "Cancel",
                    ),
                )
            ),
            warning = transactionDataProfiles.warning,
        )
    }
}
