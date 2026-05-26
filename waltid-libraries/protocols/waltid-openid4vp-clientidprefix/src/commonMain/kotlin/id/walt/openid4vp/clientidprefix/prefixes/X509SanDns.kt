package id.walt.openid4vp.clientidprefix.prefixes

import id.walt.credentials.trustedauthorities.X5CChainValidatorHelper
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.Base64Utils.decodeFromBase64
import id.walt.crypto.utils.JwsUtils.decodeJws
import id.walt.openid4vp.clientidprefix.ClientIdError
import id.walt.openid4vp.clientidprefix.ClientValidationResult
import id.walt.openid4vp.clientidprefix.RequestContext
import id.walt.openid4vp.clientidprefix.extractSanDnsNamesFromDer
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * Handles `x509_san_dns` prefix per OpenID4VP 1.0, Section 5.9.3.
 */
@Serializable
data class X509SanDns(val dnsName: String, override val rawValue: String) : ClientId {

    companion object {
        private val log = KotlinLogging.logger { }
    }

    init {
        // A simple regex to check for a plausible DNS name format.
        val dnsRegex = "^([a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?.)+[a-zA-Z]{2,6}$".toRegex()
        require(dnsRegex.matches(dnsName)) { "Invalid DNS name format for x509_san_dns." }
    }

    suspend fun authenticateX509SanDns(clientId: X509SanDns, context: RequestContext): ClientValidationResult {
        val jws = context.requestObjectJws
            ?: return ClientValidationResult.Failure(ClientIdError.MissingRequestObject)

        val decodedJws = runCatching { jws.decodeJws() }.getOrElse { return ClientValidationResult.Failure(ClientIdError.InvalidJws) }

        val x5cHeader = decodedJws.header["x5c"]?.jsonArray
            ?: return ClientValidationResult.Failure(ClientIdError.MissingX5cHeader)

        val leafCertDer = x5cHeader.firstOrNull()?.jsonPrimitive?.content?.decodeFromBase64()
            ?: return ClientValidationResult.Failure(ClientIdError.EmptyX5cHeader)

        // 1. Validate the certificate chain when more than one cert is present.
        if (x5cHeader.size > 1) {
            runCatching {
                X5CChainValidatorHelper.verifyChain(x5cHeader.map { it.jsonPrimitive.content.decodeFromBase64() })
            }.getOrElse {
                return ClientValidationResult.Failure(ClientIdError.InvalidSignature)
            }
        }

        // 2. Verify JWS signature using the leaf certificate's public key.
        val key = JWKKey.importFromDerCertificate(leafCertDer).getOrThrow()
        log.trace { "Imported key from leaf cert der for X509SanDns: $key" }

        key.verifyJws(jws).getOrThrow()

        // 3. Extract SANs using the isolated JCA utility function.
        val sans = extractSanDnsNamesFromDer(leafCertDer).getOrElse {
            return ClientValidationResult.Failure(ClientIdError.CannotExtractSanDnsNamesFromDer)
        }

        // 4. Check if the client_id's DNS name is in the SAN list.
        if (clientId.dnsName !in sans) {
            return ClientValidationResult.Failure(ClientIdError.SanDnsMismatch(clientId.dnsName, sans))
        }

        // 5. Warn if response_uri FQDN does not match the client_id DNS name.
        // Per OID4VP §x509_san_dns, ecosystems MAY require this match. We log a warning but do not reject.
        val responseUri = context.responseUri ?: context.redirectUri
        if (responseUri != null) {
            val responseUriHost = runCatching {
                io.ktor.http.Url(responseUri).host
            }.getOrNull()
            if (responseUriHost != null && !responseUriHost.endsWith(clientId.dnsName) && responseUriHost != clientId.dnsName) {
                log.warn {
                    "x509_san_dns: response_uri host '$responseUriHost' does not match client_id DNS name '${clientId.dnsName}'. " +
                        "Some ecosystems (e.g. HAIP) require this to match. Consider enabling strict response_uri validation."
                }
            }
        }

        val metadataJson = context.clientMetadata
            ?: return ClientValidationResult.Failure(ClientIdError.MissingClientMetadata)

        return runCatching { metadataJson }
            .fold(
                onSuccess = { ClientValidationResult.Success(it) },
                onFailure = { ClientValidationResult.Failure(ClientIdError.InvalidSignature) }
            )
    }
}

