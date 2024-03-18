package id.walt.crypto.keys.oci

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlinx.serialization.Serializable

@OptIn(ExperimentalJsExport::class)
@JsExport
@Serializable
data class OCIKeyMetadata(
    val tenancyOcid: String,
    val userOcid: String,
    val fingerprint: String,
    val managementEndpoint: String,
    val keyId: String,
    val cryptoEndpoint: String,
    val signingKeyPem: String? = null
)
