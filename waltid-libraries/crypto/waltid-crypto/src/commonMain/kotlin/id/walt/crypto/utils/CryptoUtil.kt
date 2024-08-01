package id.walt.crypto.utils

expect fun sha256WithRsa(privateKeyAsPem: String, data: ByteArray): ByteArray

internal fun minimalPem(privateKeyAsPem: String) = privateKeyAsPem.lines()
    .takeWhile { "PUBLIC KEY-" !in privateKeyAsPem }
    .filter { "-" !in it }
    .joinToString("")
