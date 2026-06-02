@file:Suppress("PackageDirectoryMismatch")

package id.walt.policies2.vp.policies

actual object X5CChainValidator {
    actual fun verifyChain(certChainDer: List<ByteArray>) {
        throw UnsupportedOperationException("X5CChainValidator is not yet supported on iOS")
    }
}
