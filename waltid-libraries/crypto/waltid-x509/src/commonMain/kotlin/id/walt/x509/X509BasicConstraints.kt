package id.walt.x509

data class X509BasicConstraints(
    val isCA: Boolean,
    val pathLengthConstraint: Int,
)