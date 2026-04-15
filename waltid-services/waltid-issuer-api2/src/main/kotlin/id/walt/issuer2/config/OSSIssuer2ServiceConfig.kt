package id.walt.issuer2.config

import id.walt.commons.config.WaltConfig
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class OSSIssuer2ServiceConfig(
    val baseUrl: String,
    val tokenKey: JsonObject? = null,
) : WaltConfig()
