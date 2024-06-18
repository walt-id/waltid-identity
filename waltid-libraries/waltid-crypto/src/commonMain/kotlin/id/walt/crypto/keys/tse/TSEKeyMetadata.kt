package id.walt.crypto.keys.tse

import kotlinx.serialization.Serializable
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
@Serializable
data class TSEKeyMetadata(
    val server: String,
    val accessKey: String,
    val namespace: String? = null,
    val id: String? = null
)
