package id.walt.verifier2

import id.walt.commons.config.WaltConfig
import id.walt.verifier.openid.models.authorization.ClientMetadata
import kotlinx.serialization.Serializable

@Serializable
data class OSSVerifier2ServiceConfig(
    val clientId: String,
    val clientMetadata: ClientMetadata,
    val urlPrefix: String
): WaltConfig()
