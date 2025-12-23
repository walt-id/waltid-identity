package id.walt.x509.iso.documentsigner.certificate

data class DocumentSignerPrincipalName(
    val country: String,
    val commonName: String,
    val stateOrProvinceName: String? = null,
    val organizationName: String? = null,
    val localityName: String? = null,
)
