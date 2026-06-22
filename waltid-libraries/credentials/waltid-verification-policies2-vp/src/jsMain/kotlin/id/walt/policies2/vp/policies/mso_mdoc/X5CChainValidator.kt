@file:Suppress("PackageDirectoryMismatch")

package id.walt.policies2.vp.policies

actual object X5CChainValidator {
    actual fun verifyChain(certChainDer: List<ByteArray>) {
        // X.509 certificate chain validation is not supported on JS.
        // mdoc verification is only available on JVM.
        throw UnsupportedOperationException("X5CChainValidator is not supported on JS")
    }
}
