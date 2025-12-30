package id.walt.x509.iso.documentsigner.certificate

 /**
 * X.500 distinguished name components for a Document Signer X.509 certificate's subject field.
 *
 * The ISO profile requires country and commonName.
 *
 * @param country ISO 3166-1 alpha-2 code.
 * @param commonName Human-readable common name.
 * @param stateOrProvinceName Optional state or province name.
 * @param organizationName Optional organization name.
 * @param localityName Optional locality name.
 */
data class DocumentSignerPrincipalName(
    val country: String,
    val commonName: String,
    val stateOrProvinceName: String? = null,
    val organizationName: String? = null,
    val localityName: String? = null,
) {
    companion object
}
