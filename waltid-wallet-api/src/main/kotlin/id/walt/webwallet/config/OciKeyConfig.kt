package id.walt.webwallet.config

import kotlinx.serialization.Serializable

@Serializable
data class OciKeyConfig(
    val tenancyOcid: String,
    val userOcid: String,
    val fingerprint: String,
    val managementEndpoint: String,
    val keyId: String,
    val cryptoEndpoint: String,
    val signingKeyPem: String? = null
) : WalletConfig
