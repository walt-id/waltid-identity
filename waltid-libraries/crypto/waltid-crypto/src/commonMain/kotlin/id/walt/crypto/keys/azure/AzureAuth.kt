package id.walt.crypto.keys.azure

import kotlinx.serialization.Serializable
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport


@OptIn(ExperimentalJsExport::class)
@JsExport
@Serializable
data class AzureAuth(
    val clientId: String,
    val clientSecret: String,
    val tenantId: String,
    val keyVaultUrl: String,
)
