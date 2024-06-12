package id.walt.verifier.config

import id.walt.commons.config.WaltConfig
import kotlinx.serialization.Serializable

@Serializable
data class OIDCVerifierServiceConfig(
    val baseUrl: String,
    val requestSigningKeyFile: String? = null,
    val requestSigningCertFile: String? = null,
    val x509SanDnsClientId: String? = null,
) : WaltConfig()
