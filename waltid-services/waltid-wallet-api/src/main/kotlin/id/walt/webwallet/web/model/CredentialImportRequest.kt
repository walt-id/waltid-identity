package id.walt.webwallet.web.model

import kotlinx.serialization.Serializable

@Serializable
data class CredentialImportRequest(
    val jwt: String,
    val associated_did: String,
)