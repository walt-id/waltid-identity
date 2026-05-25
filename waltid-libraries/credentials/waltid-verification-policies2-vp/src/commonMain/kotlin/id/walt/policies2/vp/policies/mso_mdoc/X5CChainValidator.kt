@file:Suppress("PackageDirectoryMismatch")

package id.walt.policies2.vp.policies

/**
 * Platform-specific X.509 certificate chain signature verifier.
 * Verifies that each cert in the chain is signed by the next one,
 * and that AKI of each cert matches SKI of its issuer.
 */
expect object X5CChainValidator {
    /**
     * Verifies the chain of DER-encoded X.509 certificates.
     * Throws [IllegalArgumentException] if any signature or AKI/SKI mismatch is found.
     * Does nothing if the chain has only one certificate.
     */
    fun verifyChain(certChainDer: List<ByteArray>)
}
