package id.walt.openid4vp.clientidprefix.prefixes

import id.walt.openid4vp.clientidprefix.ClientValidationResult
import id.walt.openid4vp.clientidprefix.RequestContext

/**
 * Represents a parsed Client Identifier that can be validated.
 */
sealed interface ClientId {
    /** The request context needed for validation. */
    val context: RequestContext

    /**
     * Validates the client based on the rules for its specific prefix type.
     * @return A [ClientValidationResult] indicating success or failure.
     */
    suspend fun validate(): ClientValidationResult

    /**
     * Factory object to parse a raw client_id string and create the appropriate handler instance.
     */
    object Parser {
        fun parse(context: RequestContext): ClientId {
            val parts = context.clientId.split(":", limit = 2)
            return if (parts.size < 2) {
                PreRegistered(context)
            } else {
                val prefix = parts[0]
                val id = parts[1]
                when (prefix) {
                    "redirect_uri" -> RedirectUri(context)
                    "x509_san_dns" -> X509SanDns(context, id)
                    "x509_hash" -> X509Hash(context, id)
                    "decentralized_identifier" -> DecentralizedIdentifier(context, id)
                    "verifier_attestation" -> VerifierAttestation(context, id)
                    "openid_federation" -> OpenIdFederation(context, id)

                    // As per OpenID4VP 1.0:
                    // "origin: The Wallet MUST NOT accept this Client Identifier Prefix in requests."
                    "origin"-> Unsupported(context, prefix)
                    else -> Unsupported(context, prefix)
                }
            }
        }
    }
}
