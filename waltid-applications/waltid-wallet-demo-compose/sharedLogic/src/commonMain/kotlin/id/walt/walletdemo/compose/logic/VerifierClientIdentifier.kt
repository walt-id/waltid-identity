package id.walt.walletdemo.compose.logic

internal data class VerifierClientIdentifier(
    val scheme: Scheme,
    val value: String,
    val rawValue: String,
) {
    enum class Scheme(
        val prefix: String?,
        val genericLabel: String?,
    ) {
        RedirectUri(prefix = "redirect_uri", genericLabel = null),
        X509SanDns(prefix = "x509_san_dns", genericLabel = null),
        X509Hash(prefix = "x509_hash", genericLabel = "X.509 verifier"),
        DecentralizedIdentifier(prefix = "decentralized_identifier", genericLabel = "DID verifier"),
        LegacyDid(prefix = "did", genericLabel = "DID verifier"),
        VerifierAttestation(prefix = "verifier_attestation", genericLabel = "Verifier attestation"),
        OpenIdFederation(prefix = "openid_federation", genericLabel = "OpenID Federation verifier"),
        PreRegistered(prefix = null, genericLabel = null),
        Unsupported(prefix = null, genericLabel = null),
    }

    companion object {
        fun parse(rawClientId: String): VerifierClientIdentifier? {
            val rawValue = rawClientId.trim().takeIf { it.isNotBlank() } ?: return null
            if (NetworkOrigin.parse(rawValue) != null) {
                return VerifierClientIdentifier(scheme = Scheme.PreRegistered, value = rawValue, rawValue = rawValue)
            }

            val prefixedValue = ClientIdentifierPrefix.split(rawValue)
            if (prefixedValue == null) {
                return VerifierClientIdentifier(scheme = Scheme.PreRegistered, value = rawValue, rawValue = rawValue)
            }

            val scheme = Scheme.entries.firstOrNull { it.prefix == prefixedValue.prefix }
                ?: Scheme.Unsupported

            return VerifierClientIdentifier(scheme = scheme, value = prefixedValue.value, rawValue = rawValue)
        }
    }
}

private data class ClientIdentifierPrefix(
    val prefix: String,
    val value: String,
) {
    companion object {
        fun split(rawValue: String): ClientIdentifierPrefix? {
            val separatorIndex = rawValue.indexOf(':')
            if (separatorIndex <= 0) return null

            return ClientIdentifierPrefix(
                prefix = rawValue.take(separatorIndex),
                value = rawValue.drop(separatorIndex + 1),
            )
        }
    }
}
