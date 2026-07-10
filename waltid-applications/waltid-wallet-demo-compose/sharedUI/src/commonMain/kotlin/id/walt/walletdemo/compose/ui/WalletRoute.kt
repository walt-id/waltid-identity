package id.walt.walletdemo.compose.ui

import androidx.navigation3.runtime.NavKey

internal sealed interface WalletRoute : NavKey {
    data object Root : WalletRoute
    data class CredentialDetails(val credentialId: String) : WalletRoute
}
