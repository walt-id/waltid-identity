package id.walt.entrawallet.core.service.exchange

import id.walt.oid4vc.data.CredentialFormat
import kotlinx.serialization.Serializable

@Serializable
data class CredentialDataResult(
    val id: String,
    val document: String,
    val manifest: String? = null,
    val disclosures: String? = null,
    val type: String,
    val format: CredentialFormat,
)
