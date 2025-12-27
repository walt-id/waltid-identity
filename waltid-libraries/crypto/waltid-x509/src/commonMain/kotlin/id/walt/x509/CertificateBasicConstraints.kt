package id.walt.x509

data class CertificateBasicConstraints(
    val isCA: Boolean,
    val pathLengthConstraint: Int,
)