package id.walt.openid4vp.clientidprefix.prefixes

import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.Base64Utils.decodeFromBase64
import id.walt.crypto.utils.JwsUtils.decodeJws
import id.walt.openid4vp.clientidprefix.ClientIdError
import id.walt.openid4vp.clientidprefix.ClientValidationResult
import id.walt.openid4vp.clientidprefix.RequestContext
import id.walt.openid4vp.clientidprefix.extractSanDnsNamesFromDer
import id.walt.openid4vp.clientidprefix.requestObjectAlgorithmMatchesKey
import id.walt.x509.CertificateDer
import id.walt.x509.validateCertificateChain
import id.walt.x509.platformSupportsPkixCertificatePathValidation
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.Url
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
        require(isValidDnsName(dnsName)) { "Invalid DNS name format for x509_san_dns." }
    }

    suspend fun authenticateX509SanDns(clientId: X509SanDns, context: RequestContext): ClientValidationResult {
        if (!platformSupportsPkixCertificatePathValidation) {
            return ClientValidationResult.Failure(ClientIdError.UnsupportedPlatformX509Validation)
        }
        val jws = context.requestObjectJws
            ?: return ClientValidationResult.Failure(ClientIdError.MissingRequestObject)

        val decodedJws = runCatching { jws.decodeJws() }.getOrElse { return ClientValidationResult.Failure(ClientIdError.InvalidJws) }

        val x5cHeader = decodedJws.header["x5c"]?.jsonArray
            ?: return ClientValidationResult.Failure(ClientIdError.MissingX5cHeader)

        val certificates = runCatching {
            x5cHeader.map { CertificateDer(it.jsonPrimitive.content.decodeFromBase64()) }
        }.getOrElse { return ClientValidationResult.Failure(ClientIdError.InvalidJws) }
        val leafCertificate = certificates.firstOrNull()
            ?: return ClientValidationResult.Failure(ClientIdError.EmptyX5cHeader)
        val trustPolicy = context.x509TrustPolicy
            ?: return ClientValidationResult.Failure(ClientIdError.MissingX509TrustPolicy)
        val requestObjectAlgorithm = decodedJws.header["alg"]?.jsonPrimitive?.content
        if (trustPolicy.allowedRequestObjectAlgorithms.isNotEmpty() &&
            requestObjectAlgorithm !in trustPolicy.allowedRequestObjectAlgorithms
        ) {
            return ClientValidationResult.Failure(
                ClientIdError.UnsupportedRequestObjectAlgorithm(requestObjectAlgorithm)
            )
        }

        if (trustPolicy.requireTrustAnchorOmittedFromX5c && certificates.any { it in trustPolicy.trustAnchors }) {
            return ClientValidationResult.Failure(ClientIdError.TrustAnchorIncludedInX5c)
        }
        if (trustPolicy.rejectLeafTrustAnchor && leafCertificate in trustPolicy.trustAnchors) {
            return ClientValidationResult.Failure(ClientIdError.SelfSignedLeafCertificate)
        }
        runCatching {
            validateCertificateChain(
                leaf = leafCertificate,
                chain = certificates.drop(1),
                trustAnchors = trustPolicy.trustAnchors,
                enableTrustedChainRoot = false,
                enableSystemTrustAnchors = trustPolicy.enableSystemTrustAnchors,
                enableRevocation = trustPolicy.enableRevocation,
            )
        }.getOrElse {
            return ClientValidationResult.Failure(ClientIdError.UntrustedCertificateChain)
        }

        // 2. Verify JWS signature using the leaf certificate's public key.
        val leafCertDer = leafCertificate.bytes.toByteArray()
        val key = runCatching { JWKKey.importFromDerCertificate(leafCertDer).getOrThrow() }
            .getOrElse { return ClientValidationResult.Failure(ClientIdError.InvalidSignature) }
        if (!requestObjectAlgorithmMatchesKey(requestObjectAlgorithm, key.keyType)) {
            return ClientValidationResult.Failure(ClientIdError.InvalidSignature)
        }
        log.trace { "Imported key from leaf cert der for X509SanDns: $key" }

        key.verifyJws(jws).getOrElse {
            return ClientValidationResult.Failure(ClientIdError.InvalidSignature)
        }

        // 3. Extract SANs using the isolated JCA utility function.
        val sans = extractSanDnsNamesFromDer(leafCertDer).getOrElse {
            return ClientValidationResult.Failure(ClientIdError.CannotExtractSanDnsNamesFromDer)
        }

        // 4. Check if the client_id's DNS name is in the SAN list.
        if (sans.none { it.equals(clientId.dnsName, ignoreCase = true) }) {
            return ClientValidationResult.Failure(ClientIdError.SanDnsMismatch(clientId.dnsName, sans))
        }

        // 5. Warn if response_uri FQDN does not match the client_id DNS name.
        // Per OID4VP §x509_san_dns, ecosystems MAY require this match. We log a warning but do not reject.
        val responseUri = context.responseUri ?: context.redirectUri
        if (responseUri != null) {
            val responseUriHost = runCatching {
                Url(responseUri).host
            }.getOrNull()
            val normalizedHost = responseUriHost?.trimEnd('.')?.lowercase()
            val normalizedClientDns = clientId.dnsName.trimEnd('.').lowercase()
            val hostMatches = normalizedHost == normalizedClientDns ||
                normalizedHost?.endsWith(".$normalizedClientDns") == true
            if (responseUriHost != null && !hostMatches) {
                log.warn {
                    "x509_san_dns: response_uri host '$responseUriHost' does not match client_id DNS name '${clientId.dnsName}'. " +
                        "Some ecosystems (e.g. HAIP) require this to match. Consider enabling strict response_uri validation."
                }
            }
        }

        val metadataJson = context.clientMetadata
            ?: return ClientValidationResult.Failure(ClientIdError.MissingClientMetadata)

        return ClientValidationResult.Success(metadataJson)
    }

    private fun isValidDnsName(value: String): Boolean {
        val normalized = value.trimEnd('.')
        if (normalized.isEmpty() || normalized.length > 253 || normalized.any { it.code > 0x7f }) return false
        return normalized.split('.').all { label ->
            label.length in 1..63 &&
                label.first().isLetterOrDigit() &&
                label.last().isLetterOrDigit() &&
                label.all { it.isLetterOrDigit() || it == '-' }
        }
    }
}
