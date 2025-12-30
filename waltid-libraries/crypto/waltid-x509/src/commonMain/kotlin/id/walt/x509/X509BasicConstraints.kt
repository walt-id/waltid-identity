package id.walt.x509

/**
 * Platform-agnostic representation of the X.509 basicConstraints extension.
 *
 * @param isCA Whether the subject of the X.509 certificate is a CA, or not.
 * @param pathLengthConstraint Maximum number of additional CA certificates
 * allowed below this certificate in the chain.
 */
data class X509BasicConstraints(
    val isCA: Boolean,
    val pathLengthConstraint: Int,
)
