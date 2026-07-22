package id.walt.crypto.keys.azure

import id.walt.crypto.utils.requireHttpEndpoint
import kotlinx.serialization.Serializable
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport


@OptIn(ExperimentalJsExport::class)
@JsExport
@Serializable
data class AzureAuth(
    var clientId: String? = null,
    var clientSecret: String? = null,
    var tenantId: String? = null,
    val keyVaultUrl: String,
) {
    init {
        requireHttpEndpoint(keyVaultUrl, "Azure Key Vault URL")
    }
}
