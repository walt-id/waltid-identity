package id.walt.certificate.x509

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType

expect suspend fun createCertificateTestKey(keyType: KeyType): Key

suspend fun <T> withCertificateTestKey(
    keyType: KeyType,
    block: suspend (Key) -> T,
): T {
    val key = createCertificateTestKey(keyType)
    return try {
        block(key)
    } finally {
        key.deleteKey()
    }
}

fun normalizePem(pem: String): String = pem.replace(Regex("\\s"), "")
