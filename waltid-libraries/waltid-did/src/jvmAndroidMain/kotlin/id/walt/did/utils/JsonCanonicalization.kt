package id.walt.did.utils

import id.walt.json.JsonCanonicalization as SharedJsonCanonicalization

actual object JsonCanonicalization {
    actual fun getCanonicalBytes(json: String): ByteArray = SharedJsonCanonicalization.getCanonicalBytes(json)
    actual fun getCanonicalString(json: String): String = SharedJsonCanonicalization.getCanonicalString(json)
}
