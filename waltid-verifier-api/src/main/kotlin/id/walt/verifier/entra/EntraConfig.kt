package id.walt.verifier.entra

import id.walt.config.WaltConfig
import kotlinx.serialization.Serializable

@Serializable
data class EntraConfig (val callbackUrl: String): WaltConfig()
