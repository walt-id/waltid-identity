package id.walt.config

import kotlinx.serialization.Serializable

@Serializable
data class RemoteWalletConfig(val remoteWallet: String = "http://localhost:8080") : WalletConfig

