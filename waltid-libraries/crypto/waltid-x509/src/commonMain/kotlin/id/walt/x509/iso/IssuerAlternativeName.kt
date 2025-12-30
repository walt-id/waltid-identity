package id.walt.x509.iso

/**
 * ISO profile representation of the IssuerAlternativeName extension.
 *
 * Only URI and email are modeled here because they are the fields used by the
 * ISO X.509 certificate profiles. At least one of the fields is expected to be
 * present when the profile requires this extension.
 *
 * @param uri Optional issuer URI.
 * @param email Optional issuer email address.
 */
data class IssuerAlternativeName(
    val uri: String? = null,
    val email: String? = null,
) {
    companion object
}
