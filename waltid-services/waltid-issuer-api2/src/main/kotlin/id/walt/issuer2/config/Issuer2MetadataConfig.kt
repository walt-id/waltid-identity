package id.walt.issuer2.config

import id.walt.commons.config.WaltConfig
import kotlinx.serialization.json.JsonElement

data class Issuer2MetadataConfig(
    val issuerDisplay: List<JsonElement>? = null,
    val credentialConfigurations: Map<String, JsonElement> = emptyMap(),
) : WaltConfig()