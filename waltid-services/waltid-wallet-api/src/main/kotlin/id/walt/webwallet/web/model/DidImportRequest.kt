package id.walt.webwallet.web.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class DidImportRequest(
    val did: String,
    val key: JsonElement?,
    val alias: String? = null,
)