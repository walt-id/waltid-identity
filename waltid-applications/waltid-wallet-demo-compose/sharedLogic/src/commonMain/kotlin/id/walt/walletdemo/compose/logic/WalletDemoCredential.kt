package id.walt.walletdemo.compose.logic

import kotlinx.serialization.Serializable

@Serializable
data class WalletDemoCredential(
    val id: String,
    val format: String,
    val issuer: String,
    val subject: String,
    val label: String,
    val addedAt: String,
)

data class WalletDemoCredentialDetails(
    val id: String,
    val credentialDataJson: String,
)
