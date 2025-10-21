package id.walt.openid4vp.verifier

import id.walt.commons.config.WaltConfig
import id.walt.verifier.openid.models.authorization.ClientMetadata
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class OSSVerifier2ServiceConfig(
    val clientId: String,
    val clientMetadata: ClientMetadata,
    val urlPrefix: String,
    val urlHost: String,
    val key: JsonObject? = null,
    val x5c: List<String>? = null
) : WaltConfig()
