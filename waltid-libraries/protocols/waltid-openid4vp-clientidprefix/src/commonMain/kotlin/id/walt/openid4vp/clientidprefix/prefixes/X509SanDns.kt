package id.walt.openid4vp.clientidprefix.prefixes

import id.walt.openid4vp.clientidprefix.ClientIdError
import id.walt.openid4vp.clientidprefix.ClientMetadata
import id.walt.openid4vp.clientidprefix.ClientValidationResult
import id.walt.openid4vp.clientidprefix.RequestContext

/**
 * Handles `x509_san_dns` prefix per OpenID4VP 1.0, Section 5.9.3.
 */
class X509SanDns(override val context: RequestContext, private val dnsName: String) : ClientId {
    override suspend fun validate(): ClientValidationResult {
        // The request MUST be signed.
        val jws = context.requestObjectJws ?: return ClientValidationResult.Failure(ClientIdError.MissingRequestObject)

        // TODO:  stub for JOSE/JWT library
        // val x5c: List<String> = parseJwsHeader(jws, "x5c")
        // val leafCertBytes = Base64.decode(x5c.first())
        // if (!verifyJwsSignature(jws, leafCertBytes.toPublicKey())) { ... }

        // TODO: stub for X.509 library
        // val sans: List<String> = extractSanDnsNames(leafCertBytes)
        val sans = listOf("client.example.org", "another.client.com") // Dummy data

        // The original Client Identifier MUST be a DNS name and match a dNSName SAN entry.
        if (dnsName !in sans) {
            return ClientValidationResult.Failure(ClientIdError.SanDnsMismatch)
        }

        // All Verifier metadata other than the public key MUST be obtained from client_metadata.
        return context.clientMetadataJson?.let {
            ClientValidationResult.Success(ClientMetadata(it))
        } ?: ClientValidationResult.Failure(ClientIdError.MissingClientMetadata)
    }
}
