package id.walt.commons.config.list

import kotlinx.serialization.Serializable

@Serializable
data class DevModeConfig(
    val mock: Boolean = false,
    val enableDidWebResolverHttps: Boolean = true,
)