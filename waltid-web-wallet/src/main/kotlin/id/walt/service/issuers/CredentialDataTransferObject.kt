package id.walt.service.issuers

import kotlinx.serialization.Serializable

@Serializable
data class CredentialDataTransferObject(
    val id: String,
    val format: String,
    val types: List<String>,
)
