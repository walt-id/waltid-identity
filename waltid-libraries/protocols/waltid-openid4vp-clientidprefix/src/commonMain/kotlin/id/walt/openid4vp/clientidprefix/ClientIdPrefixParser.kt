package id.walt.openid4vp.clientidprefix

import id.walt.openid4vp.clientidprefix.prefixes.*
import io.ktor.http.*

enum class ClientIdPrefix(val value: String) {
    PRE_REGISTERED("pre-registered"),
    REDIRECT_URI("redirect_uri"),
    X509_SAN_DNS("x509_san_dns"),
    X509_HASH("x509_hash"),
    DECENTRALIZED_IDENTIFIER("decentralized_identifier"),
    VERIFIER_ATTESTATION("verifier_attestation"),
    OPENID_FEDERATION("openid_federation");

    companion object {
        fun fromValue(value: String): ClientIdPrefix? = entries.find { it.value == value }
    }
}

object ClientIdPrefixParser {
    fun parse(clientIdString: String): Result<ClientId> {
        return runCatching {
            val parts = clientIdString.split(":", limit = 2)
            if (parts.size < 2) {
                PreRegistered(clientIdString)
            } else {
                val prefix = parts[0]
                val id = parts[1]
                when (ClientIdPrefix.fromValue(prefix)) {
                    ClientIdPrefix.REDIRECT_URI -> RedirectUri(Url(id), clientIdString)
                    ClientIdPrefix.X509_SAN_DNS -> X509SanDns(id, clientIdString)
                    ClientIdPrefix.X509_HASH -> X509Hash(id, clientIdString)
                    ClientIdPrefix.DECENTRALIZED_IDENTIFIER -> DecentralizedIdentifier(id, clientIdString)
                    ClientIdPrefix.VERIFIER_ATTESTATION -> VerifierAttestation(id, clientIdString)
                    ClientIdPrefix.OPENID_FEDERATION -> OpenIdFederation(id, clientIdString)

                    // Pre-registered clients are represented by the absence of a prefix.
                    ClientIdPrefix.PRE_REGISTERED, null -> Unsupported(prefix, clientIdString)
                }
            }
        }
    }
}
