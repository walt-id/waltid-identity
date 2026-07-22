package id.walt.crypto.keys.oci

import id.walt.crypto.utils.requireEndpointHost
import id.walt.crypto.utils.requireHttpEndpoint
import kotlinx.serialization.Serializable
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
@Serializable
data class OCIKeyMetadata(
    val tenancyOcid: String,
    val compartmentOcid: String,
    val userOcid: String,
    val fingerprint: String,
    val managementEndpoint: String,
    val cryptoEndpoint: String,
    val signingKeyPem: String? = null
) {
    init {
        validateEndpoint(managementEndpoint, "OCI management endpoint")
        validateEndpoint(cryptoEndpoint, "OCI crypto endpoint")
    }

    private fun validateEndpoint(value: String, name: String) =
        if ("://" in value) requireHttpEndpoint(value, name) else requireEndpointHost(value, name)
}
