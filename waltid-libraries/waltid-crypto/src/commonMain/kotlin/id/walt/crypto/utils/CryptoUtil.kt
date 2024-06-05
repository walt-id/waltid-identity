package id.walt.crypto.utils

expect fun sha256WithRsa(privateKeyAsPem: String, data: ByteArray): ByteArray
