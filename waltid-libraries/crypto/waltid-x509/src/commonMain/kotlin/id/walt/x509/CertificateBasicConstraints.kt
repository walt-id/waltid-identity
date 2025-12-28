package id.walt.x509

//TODO: Figure out a better name for this
data class CertificateBasicConstraints(
    val isCA: Boolean,
    val pathLengthConstraint: Int,
)