package id.walt.did.utils

import canonicalize

@OptIn(ExperimentalJsExport::class)
@JsExport
actual object JsonCanonicalization {
    actual fun getCanonicalBytes(json: String): ByteArray = getCanonicalString(json).encodeToByteArray()
    actual fun getCanonicalString(json: String): String = canonicalize(JSON.parse<dynamic>(json))
}
