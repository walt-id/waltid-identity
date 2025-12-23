package id.walt.x509.iso.iaca.certificate

data class IACAPrincipalName(
    val country: String,
    val commonName: String,
    val stateOrProvinceName: String? = null,
    val organizationName: String? = null,
)
