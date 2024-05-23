package id.walt.webwallet.config

import kotlinx.serialization.Serializable

@Serializable
data class TenantConfig(val useCloudTenants: Boolean = false) : WalletConfig

