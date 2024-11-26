package id.walt.crypto.keys.azure

import kotlinx.serialization.Serializable
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport


@OptIn(ExperimentalJsExport::class)
@JsExport
@Serializable
data class AzureAuth(
    val clientId: String? = null,
    val clientSecret: String? = null,
    val tenantId: String? = null,
    val keyVaultUrl: String? = null,
) {
    init {
        requireAuthenticationMethod()
    }

    private fun requireAuthenticationMethod() {
        val servicePrincipal = clientId != null && clientSecret != null && tenantId != null


        if (!servicePrincipal) {
            throw IllegalArgumentException("AzureAuth requires clientId, clientSecret, and tenantId")
        }
    }
}
