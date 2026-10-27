package id.walt.walletdemo.compose.logic

data class DemoWalletConfig(
    val walletId: String = "default",
    val attestationBaseUrl: String = DemoPublicBackendDefaults.attestationBaseUrl,
    val attestationAttesterPath: String = DemoPublicBackendDefaults.attestationAttesterPath,
    val attestationBearerToken: String = DemoPublicBackendDefaults.attestationBearerToken,
    val attestationHostHeader: String = DemoPublicBackendDefaults.attestationHostHeader,
    val walletProviderEnabled: Boolean = false,
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
    /**
     * Hosted Enterprise Stack demo. Same values as
     * `EnterpriseDemoWalletProvider` in the wallet library. The wallet-provider service is not
     * deployed or provisioned there yet, so sample apps leave it disabled.
     */
    const val walletProviderBaseUrl = id.walt.wallet2.walletprovider.EnterpriseDemoWalletProvider.BASE_URL
    const val walletProviderServicePath = id.walt.wallet2.walletprovider.EnterpriseDemoWalletProvider.SERVICE_PATH
}
