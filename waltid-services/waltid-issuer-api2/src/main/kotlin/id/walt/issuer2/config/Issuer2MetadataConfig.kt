package id.walt.issuer2.config

import id.walt.commons.config.WaltConfig
import id.walt.sdjwt.metadata.type.SdJwtVcTypeMetadataDraft04
import kotlinx.serialization.json.JsonElement

data class Issuer2MetadataConfig(
    val issuerDisplay: List<JsonElement>? = null,
    val credentialConfigurations: Map<String, JsonElement> = emptyMap(),
    /** Attestation type metadata keyed by SD-JWT credential configuration ID. */
    val sdJwtVcTypeMetadataConfiguration: Map<String, SdJwtVcTypeMetadataDraft04> = emptyMap(),
) : WaltConfig()