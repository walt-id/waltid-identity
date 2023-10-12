package id.walt.oid4vc.providers

data class CredentialWalletConfig(
    val redirectUri: String? = null
) : OpenIDProviderConfig()
