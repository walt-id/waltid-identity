package id.walt.did.utils

import canonicalize
import io.ktor.utils.io.core.*

@OptIn(ExperimentalJsExport::class)
@JsExport
actual object JsonCanonicalization {
    actual fun getCanonicalBytes(json: String): ByteArray = canonicalize(json).toByteArray()
    actual fun getCanonicalString(json: String): String = canonicalize(json)
}
