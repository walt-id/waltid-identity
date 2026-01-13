package id.walt.x509.iso.iaca.certificate

/**
 * X.500 distinguished name components for an IACA X.509 certificate's subject & issuer fields.
 *
 * The ISO profile requires country and commonName.
 *
 * @param country ISO 3166-1 alpha-2 code.
 * @param commonName Human-readable common name.
 * @param stateOrProvinceName Optional state or province name.
 * @param organizationName Optional organization name.
 */
data class IACAPrincipalName(
    val country: String,
    val commonName: String,
    val stateOrProvinceName: String? = null,
    val organizationName: String? = null,
) {
    companion object
}
