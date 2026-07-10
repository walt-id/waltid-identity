package id.walt.did.utils

import org.erdtman.jcs.JsonCanonicalizer

actual object JsonCanonicalization {
    actual fun getCanonicalBytes(json: String): ByteArray = JsonCanonicalizer(json).encodedUTF8
    actual fun getCanonicalString(json: String): String = JsonCanonicalizer(json).encodedString
}
