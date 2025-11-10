package id.walt.commons.config.list

import kotlinx.serialization.Serializable

@Serializable
data class DevModeConfig(
    val enableDidWebResolverHttps: Boolean = true,
)