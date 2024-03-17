package id.walt.crypto.keys

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlinx.serialization.Serializable

@Serializable
@OptIn(ExperimentalJsExport::class)
@JsExport
data class OCIKeyMetadata(
    val tenancyOcid: String,
    val userOcid: String,
    val fingerprint: String,
    val managementEndpoint: String,
    val cryptoEndpoint: String,
    val signingKeyPem: String? = null,
)
