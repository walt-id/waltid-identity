package id.walt.credentials.trustedauthorities

actual object X5CChainValidatorHelper {
    actual fun verifyChain(certChainDer: List<ByteArray>) {
        throw UnsupportedOperationException("X5CChainValidatorHelper is not supported on JS")
    }
}
