package id.walt.webwallet.web.model

import kotlinx.serialization.Serializable

@Serializable
internal data class CredentialImportRequest(
    val jwt: String,
    val associated_did: String,
)
