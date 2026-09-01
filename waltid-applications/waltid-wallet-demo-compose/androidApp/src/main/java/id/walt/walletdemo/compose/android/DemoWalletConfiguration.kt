package id.walt.walletdemo.compose.android

import android.content.Context
import id.walt.walletdemo.compose.logic.DemoWalletConfig
import id.walt.walletdemo.compose.logic.WalletDemoSigningProtection
import id.walt.walletdemo.compose.logic.WalletDemoSigningProtectionMode
import id.walt.walletdemo.compose.logic.WalletDemoSigningProtectionStore
import id.walt.walletdemo.compose.logic.createAndroidDemoSigningProtectionStore

/**
 * The demo app's build-time wallet configuration, shared by every Android entry point.
 *
 * The Credential Manager provider activity is launched by the operating system independently of
 * [MainActivity], so both must read the same values or they open different wallet databases and apply
 * different transaction-data or signing-protection policies.
 */
internal fun demoWalletConfig(): DemoWalletConfig = DemoWalletConfig(
    attestationBaseUrl = BuildConfig.ATTESTATION_BASE_URL,
    attestationAttesterPath = BuildConfig.ATTESTATION_ATTESTER_PATH,
    attestationBearerToken = BuildConfig.ATTESTATION_BEARER_TOKEN,
    attestationHostHeader = BuildConfig.ATTESTATION_HOST_HEADER,
    transactionDataProfilesUrl = BuildConfig.TRANSACTION_DATA_PROFILES_URL,
    signingProtectionMode = WalletDemoSigningProtectionMode.parse(BuildConfig.WALLET_SIGNING_PROTECTION_MODE),
)

internal fun DemoWalletConfig.signingProtectionStore(context: Context): WalletDemoSigningProtectionStore =
    createAndroidDemoSigningProtectionStore(context, walletId)

internal fun DemoWalletConfig.selectedSigningProtection(context: Context): WalletDemoSigningProtection =
    signingProtectionStore(context).load()?.takeIf(signingProtectionMode::allows)
        ?: when (signingProtectionMode) {
            WalletDemoSigningProtectionMode.Optional -> error(
                "Open the wallet app to choose signing protection before using Digital Credentials",
            )
            WalletDemoSigningProtectionMode.Required,
            WalletDemoSigningProtectionMode.Disabled,
            -> signingProtectionMode.defaultSelection
        }
