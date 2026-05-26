package id.walt.wallet2.client

data class WalletClientEnvironment(
    val enterpriseBaseUrl: String = "",
    val enterpriseHostHeader: String = "",
    val bearerToken: String = "",
    val walletPath: String = "",
    val attesterServiceRef: String = "",
    val instanceKeyReference: String = "",
) {
    fun normalizedBaseUrl(): String = enterpriseBaseUrl.trim().removeSuffix("/")

    fun withDerivedReferences(): WalletClientEnvironment {
        val prefix = walletPath.trim().removeSuffix(".wallet")
        if (prefix.isBlank()) return this
        return copy(
            attesterServiceRef = "$prefix.client-attester",
            instanceKeyReference = "$prefix.kms.wallet_key",
        )
    }
}

enum class WalletClientEnvironmentProfile {
    QuickstartLocal,
    Local,
    Sandbox,
    Custom,
}

fun WalletClientEnvironmentProfile.toEnvironment(): WalletClientEnvironment =
    when (this) {
        WalletClientEnvironmentProfile.QuickstartLocal -> WalletClientEnvironment(
            enterpriseBaseUrl = "http://10.0.2.2",
            enterpriseHostHeader = "waltid.enterprise.localhost",
            walletPath = "waltid.waltid-tenant01.wallet",
            attesterServiceRef = "waltid.waltid-tenant01.client-attester",
            instanceKeyReference = "waltid.waltid-tenant01.kms.wallet_key",
        )
        WalletClientEnvironmentProfile.Local -> WalletClientEnvironment(
            enterpriseBaseUrl = "http://10.0.2.2:8080",
            walletPath = "org.tenant.wallet",
        )
        WalletClientEnvironmentProfile.Sandbox -> WalletClientEnvironment(
            enterpriseBaseUrl = "https://sandbox.enterprise.walt.id",
            walletPath = "org.tenant.wallet",
        )
        WalletClientEnvironmentProfile.Custom -> WalletClientEnvironment()
    }
