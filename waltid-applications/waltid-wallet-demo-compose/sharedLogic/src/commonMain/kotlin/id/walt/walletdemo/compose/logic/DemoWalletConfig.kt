package id.walt.walletdemo.compose.logic

data class DemoWalletConfig(
    val walletId: String = "default",
    val attestationBaseUrl: String = DemoPublicBackendDefaults.attestationBaseUrl,
    val attestationAttesterPath: String = DemoPublicBackendDefaults.attestationAttesterPath,
    val attestationBearerToken: String = DemoPublicBackendDefaults.attestationBearerToken,
    val attestationHostHeader: String = DemoPublicBackendDefaults.attestationHostHeader,
    val walletProviderEnabled: Boolean = true,
    val walletProviderBaseUrl: String = DemoPublicBackendDefaults.walletProviderBaseUrl,
    val walletProviderServicePath: String = DemoPublicBackendDefaults.walletProviderServicePath,
    val walletProviderBearerToken: String = "",
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
    /** Hosted Enterprise Stack demo. The wallet-provider service is not deployed or provisioned here yet. */
    const val walletProviderBaseUrl = "https://org4.enterprise.demo.walt.id"
    const val walletProviderServicePath = "org4.demo.wallet-provider"
}
