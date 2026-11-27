package id.walt.walletdemo.compose.logic

import android.content.Context
import androidx.fragment.app.FragmentActivity
import id.walt.wallet2.mobile.AndroidWalletNfcConfiguration
import id.walt.wallet2.mobile.Wallet
import id.walt.wallet2.mobile.WalletConfiguration
import id.walt.wallet2.mobile.WalletFactory
import id.walt.wallet2.mobile.identity.WalletKeyAuthorization
import id.walt.wallet2.mobile.issuance.IssuanceConfiguration
import id.walt.wallet2.mobile.presentation.PresentationConfiguration
import id.walt.wallet2.persistence.keys.KeyUseAuthorizationPrompt
import id.walt.walletdemo.attestation.DemoKeyAttestationProviders

/**
 * An Android demo [Wallet] together with anything the caller must warn the user about.
 *
 * @property wallet The configured wallet.
 * @property transactionDataProfilesWarning Set when the accepted transaction data profiles could not
 *   be loaded, in which case the wallet rejects every request carrying `transaction_data`.
 */
data class AndroidDemoWallet(
    val wallet: Wallet,
    val transactionDataProfilesWarning: String?,
) {
    suspend fun bootstrap(signingProtection: WalletDemoSigningProtection): WalletDemoBootstrapResult {
        val demoWallet = SdkDemoWallet(wallet, transactionDataProfilesWarning)
        val availability = demoWallet.signingProtectionAvailability(signingProtection)
        check(availability == WalletDemoSigningProtectionAvailability.Available) {
            "Signing protection is unavailable: $availability"
        }
        return demoWallet.bootstrap(signingProtection).also { result ->
            check(result.signingProtection == signingProtection) {
                "Open the wallet app to apply the configured signing protection before using Digital Credentials"
            }
            demoWallet.refreshDigitalCredentialRegistration()
        }
    }
}

/**
 * The single Android [Wallet] construction for this demo app.
 *
 * Every Android entry point must go through here, including the Credential Manager provider
 * activity, which the operating system may launch without the wallet's own UI ever having run. A
 * second, independently written [WalletConfiguration] silently diverges: a different `walletId` opens
 * a different database and so finds no credentials, and omitted `transactionDataProfiles` reject
 * every request carrying `transaction_data` that the main UI would have accepted.
 */
suspend fun openAndroidDemoWallet(
    context: Context,
    config: DemoWalletConfig = DemoWalletConfig(),
    interactionContextProvider: () -> FragmentActivity? = { null },
): AndroidDemoWallet {
    val transactionDataProfiles = config.resolveDemoTransactionDataProfiles()
    return AndroidDemoWallet(
        wallet = WalletFactory(
            context = context,
            interactionContextProvider = interactionContextProvider,
            nfcConfiguration = AndroidWalletNfcConfiguration(DemoMdocHostApduService::class.java),
        ).open(
            WalletConfiguration(
                walletId = config.walletId,
                keyAuthorization = WalletKeyAuthorization(
                    policy = config.signingProtectionMode.defaultSelection.toKeyUseAuthorizationPolicy(),
                    prompt = KeyUseAuthorizationPrompt(
                        reason = "Authorize wallet signing",
                        cancelText = "Cancel",
                    ),
                ),
                signingIdentity = id.walt.wallet2.mobile.identity.SigningIdentityConfiguration(
                    recoveryProviders = listOf(
                        id.walt.wallet2.recovery.blockstore.BlockStoreIdentityRecovery(context, "wallet-demo"),
                        id.walt.wallet2.recovery.blockstore.BlockStoreIdentityRecovery(context, "wallet-demo",
                            id.walt.wallet2.recovery.blockstore.BlockStoreRecoveryMode.DeviceTransfer),
                    ),
                    alternativeAuthorizations = config.signingProtectionMode.alternativeAuthorizations(),
                ),
                issuance = IssuanceConfiguration(
                    attestation = config.toWalletAttestationConfig(),
                    walletProvider = config.toWalletProviderConfig(),
                ),
                presentation = PresentationConfiguration(
                    verifierTrust = DemoClientIdTrust.configuration,
                    transactionDataProfiles = transactionDataProfiles.profiles,
                    paymentCredentialIssuers = config.paymentCredentialIssuers(),
                ),
            ),
        ).attachKeyAttestationProviderResolver(DemoKeyAttestationProviders()),
        transactionDataProfilesWarning = transactionDataProfiles.warning,
    )
}

fun createAndroidDemoWallet(
    context: Context,
    config: DemoWalletConfig = DemoWalletConfig(),
    interactionContextProvider: () -> FragmentActivity? = { null },
): ProximityDemoWallet {

    return LazyProximityDemoWallet {
        openAndroidDemoWallet(context, config, interactionContextProvider).let { created ->
            SdkDemoWallet(created.wallet, warning = created.transactionDataProfilesWarning,
                preferredSigningProtection = config.signingProtectionMode.defaultSelection)
        }
    }
}
