package id.walt.verifier.base.config

import kotlinx.serialization.Serializable

@Serializable
data class OIDCVerifierServiceConfig(
    val baseUrl: String
) : BaseConfig
