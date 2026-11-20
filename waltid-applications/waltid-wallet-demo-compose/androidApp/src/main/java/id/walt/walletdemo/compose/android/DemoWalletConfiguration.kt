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
    paymentCredentialIssuerKeys = if (BuildConfig.ITB_PAYMENT_TRUST) itbPaymentCredentialIssuerKeys else emptyMap(),
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

// TS-12 credential signing key from the reference issuer's x509EC/client_certificate.crt.
private val itbPaymentCredentialIssuerKeys = mapOf(
    "https://dev-i4mlab.aegean.gr/rfc-issuer" to
        """{"kty":"EC","crv":"P-256","x":"dMLtMHM-bYdiJRHJmfA7ypihS8JDAmci4q3FOeLy6mA","y":"8ZXh3dOsLoJFT1Y6F--HgR6uBjsOifnTIiiVnQfgoWs"}""",
)
