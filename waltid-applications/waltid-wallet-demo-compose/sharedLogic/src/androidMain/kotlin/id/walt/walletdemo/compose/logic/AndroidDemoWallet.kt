package id.walt.walletdemo.compose.logic

import android.content.Context
import android.os.LocaleList
import id.walt.wallet2.mobile.MobileWallet
import androidx.fragment.app.FragmentActivity
import id.walt.wallet2.persistence.keys.KeyUseAuthorizationPolicy
import id.walt.wallet2.persistence.keys.KeyUseAuthorizationPrompt
import id.walt.wallet2.mobile.MobileWalletConfig
import id.walt.wallet2.mobile.MobileWalletFactory

/**
 * An Android demo [MobileWallet] together with anything the caller must warn the user about.
 *
 * @property wallet The configured wallet.
 * @property transactionDataProfilesWarning Set when the accepted transaction data profiles could not
 *   be loaded, in which case the wallet rejects every request carrying `transaction_data`.
 */
data class AndroidDemoMobileWallet(
    val wallet: MobileWallet,
    val transactionDataProfilesWarning: String?,
)

/**
 * The single Android [MobileWallet] construction for this demo app.
 *
 * Every Android entry point must go through here, including the Credential Manager provider
 * activity, which the operating system may launch without the wallet's own UI ever having run. A
 * second, independently written [MobileWalletConfig] silently diverges: a different `walletId` opens
 * a different database and so finds no credentials, and omitted `transactionDataProfiles` reject
 * every request carrying `transaction_data` that the main UI would have accepted.
 */
suspend fun createAndroidDemoMobileWallet(
    context: Context,
    config: DemoWalletConfig = DemoWalletConfig(),
    interactionContextProvider: () -> FragmentActivity? = { null },
): AndroidDemoMobileWallet {
    val transactionDataProfiles = config.resolveDemoTransactionDataProfiles()
    return AndroidDemoMobileWallet(
        wallet = MobileWalletFactory(context, interactionContextProvider).create(
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
                    reason = "Authorize wallet signing",
                    cancelText = "Cancel",
                ),
            )
        ),
        transactionDataProfilesWarning = transactionDataProfiles.warning,
    )
}

fun createAndroidDemoWallet(
    context: Context,
    config: DemoWalletConfig = DemoWalletConfig(),
    interactionContextProvider: () -> FragmentActivity? = { null },
): DemoWallet {

    return LazyDemoWallet {
        createAndroidDemoMobileWallet(context, config, interactionContextProvider).let { created ->
            MobileDemoWallet(created.wallet, warning = created.transactionDataProfilesWarning)
        }
    }
}
