package id.walt.openid4vp.clientidprefix

import id.walt.openid4vp.clientidprefix.prefixes.*
import io.ktor.http.*

object ClientIdPrefixParser {
    fun parse(clientIdString: String): Result<ClientId> {
        return runCatching {
            val parts = clientIdString.split(":", limit = 2)
            if (parts.size < 2) {
                PreRegistered(clientIdString)
            } else {
                val prefix = parts[0]
                val id = parts[1]
                when (prefix) {
                    "redirect_uri" -> RedirectUri(Url(id), clientIdString)
                    "x509_san_dns" -> X509SanDns(id, clientIdString)
                    "x509_hash" -> X509Hash(id, clientIdString)
                    "decentralized_identifier" -> DecentralizedIdentifier(id, clientIdString)
                    "verifier_attestation" -> VerifierAttestation(id, clientIdString)
                    "openid_federation" -> OpenIdFederation(id, clientIdString)

                    // The 'origin' prefix is reserved and MUST NOT be accepted.
                    else -> Unsupported(prefix, clientIdString)
                }
            }
        }
    }
}
