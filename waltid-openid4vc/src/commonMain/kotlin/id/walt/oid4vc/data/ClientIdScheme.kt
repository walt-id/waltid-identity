package id.walt.oid4vc.data

enum class ClientIdScheme(val value: String) {
    PreRegistered("pre-registered"),
    RedirectUri("redirect_uri"),
    EntityId("entity_id"),
    Did("did"),
    VerifierAttestation("verifier_attestation"),
    X509SanDns("x509_san_dns"),
    X509SanUri("x509_san_uri");

    companion object {
        fun fromValue(value: String): ClientIdScheme? {
            return entries.find { it.value == value }
        }
    }
}
