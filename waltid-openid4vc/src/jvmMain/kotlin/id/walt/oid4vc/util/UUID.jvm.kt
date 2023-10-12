package id.walt.oid4vc.util

import java.security.MessageDigest
import java.util.*

actual fun randomUUID(): String {
    return UUID.randomUUID().toString()
}

actual fun sha256(data: ByteArray): ByteArray
    = MessageDigest.getInstance("SHA-256").digest(data)