package id.walt.webwallet.web.model

import kotlinx.serialization.Serializable

@Serializable
data class DidImportRequest(
    val did: String,
    val keys: List<String>? = null,
    val alias: String? = null,
)