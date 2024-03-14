package id.walt.crypto.keys

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
data class JwkKeyMetadata(
    val keySize: Int? = null
)
