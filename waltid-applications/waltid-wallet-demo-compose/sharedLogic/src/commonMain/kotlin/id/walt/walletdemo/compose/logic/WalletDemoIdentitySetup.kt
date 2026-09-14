package id.walt.walletdemo.compose.logic

/** UI descriptions refer to SDK-owned options retained by the mobile adapter. */
data class WalletDemoIdentityChoice(val id: String, val title: String, val detail: String, val recoverable: Boolean, val destructive: Boolean = false)

sealed interface WalletDemoIdentitySetup {
    data class Choose(val choices: List<WalletDemoIdentityChoice>, val message: String? = null) : WalletDemoIdentitySetup
    data class Pending(val identityId: String) : WalletDemoIdentitySetup
}

/** Public facts and SDK-issued actions only; no recovery secret reaches UI state. */
data class WalletDemoIdentityDetails(val storage: String, val origin: String, val authorization: String,
    val recovery: String, val choices: List<WalletDemoIdentityChoice>)
