package id.walt.crypto.keys.jwk

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
data class JWKKeyMetadata(
    val keySize: Int? = null
)
