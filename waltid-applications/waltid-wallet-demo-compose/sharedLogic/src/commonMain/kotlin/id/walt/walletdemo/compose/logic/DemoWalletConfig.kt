package id.walt.walletdemo.compose.logic

data class DemoWalletConfig(
    val walletId: String = "default",
    val attestationBaseUrl: String = DemoPublicBackendDefaults.attestationBaseUrl,
    val attestationAttesterPath: String = DemoPublicBackendDefaults.attestationAttesterPath,
    val attestationBearerToken: String = DemoPublicBackendDefaults.attestationBearerToken,
    val attestationHostHeader: String = DemoPublicBackendDefaults.attestationHostHeader,
    val transactionDataProfilesUrl: String = DemoPublicBackendDefaults.transactionDataProfilesUrl,
    /** Product constraint for runtime signing protection selection. */
    val signingProtectionMode: WalletDemoSigningProtectionMode = WalletDemoSigningProtectionMode.Optional,
)

object DemoPublicBackendDefaults {
    const val attestationBaseUrl = ""
    const val attestationAttesterPath = ""
    const val attestationBearerToken = ""
    const val attestationHostHeader = ""
    const val transactionDataProfilesUrl = "https://wallet.demo.walt.id/wallet-api/transaction-data-profiles"
}
