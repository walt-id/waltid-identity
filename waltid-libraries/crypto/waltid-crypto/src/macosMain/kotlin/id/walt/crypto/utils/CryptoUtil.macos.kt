package id.walt.crypto.utils

actual fun sha256WithRsa(privateKeyAsPem: String, data: ByteArray): ByteArray {
    throw UnsupportedOperationException("Not implemented for macOS")
}

