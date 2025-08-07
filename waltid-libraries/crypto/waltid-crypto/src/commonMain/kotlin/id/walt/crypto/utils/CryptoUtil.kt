package id.walt.crypto.utils

import id.walt.crypto.keys.KeyType

expect fun sha256WithRsa(privateKeyAsPem: String, data: ByteArray): ByteArray

internal fun minimalPem(privateKeyAsPem: String) = privateKeyAsPem.lines()
    .takeWhile { "PUBLIC KEY-" !in privateKeyAsPem }
    .filter { "-" !in it }
    .joinToString("")

