package id.walt.verifier.entra

import id.walt.verifier.base.config.BaseConfig
import kotlinx.serialization.Serializable

@Serializable
data class EntraConfig (val callbackUrl: String): BaseConfig
