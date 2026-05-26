package id.walt.credentials.trustedauthorities

/**
 * Platform-specific helper for validating X.509 certificate chains in the key resolver path.
 * See [id.walt.credentials.keyresolver.resolvers.X5CKeyResolver].
 */
expect object X5CChainValidatorHelper {
    /**
     * Verifies the signature chain of DER-encoded X.509 certificates.
     * Throws [IllegalArgumentException] if any link in the chain is invalid.
     * Does nothing if the chain contains only one certificate.
     */
    fun verifyChain(certChainDer: List<ByteArray>)
}
