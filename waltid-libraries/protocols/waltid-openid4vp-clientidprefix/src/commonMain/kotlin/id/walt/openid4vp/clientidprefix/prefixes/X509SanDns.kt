@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.openid4vp.clientidprefix.prefixes

import id.walt.crypto.utils.Base64Utils.decodeFromBase64
import id.walt.crypto2.jose.CompactJws
import id.walt.openid4vp.clientidprefix.ClientIdError
import id.walt.openid4vp.clientidprefix.ClientValidationResult
import id.walt.openid4vp.clientidprefix.ClientIdTrustConfiguration
import id.walt.openid4vp.clientidprefix.RequestContext
import id.walt.openid4vp.clientidprefix.extractSanDnsNamesFromDer
import id.walt.x509.CertificateDer
import id.walt.x509.validateClientAuthenticationCertificateChain
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.Url
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.coroutines.CancellationException
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
        return authenticateX509SanDns(clientId, context, ClientIdTrustConfiguration())
    }

    suspend fun authenticateX509SanDns(
        clientId: X509SanDns,
        context: RequestContext,
        trustConfiguration: ClientIdTrustConfiguration,
    ): ClientValidationResult {
        val jws = context.requestObjectJws
            ?: return ClientValidationResult.Failure(ClientIdError.MissingRequestObject)

        val decodedJws = try {
            CompactJws.decodeUnverified(jws)
        } catch (_: Exception) {
            return ClientValidationResult.Failure(ClientIdError.InvalidJws)
        }

        val x5cHeader = decodedJws.protectedHeader["x5c"] as? JsonArray
            ?: return ClientValidationResult.Failure(ClientIdError.MissingX5cHeader)
        if (trustConfiguration.x509TrustAnchors.isEmpty()) {
            return ClientValidationResult.Failure(ClientIdError.MissingX509TrustAnchors)
        }

        val certificates = try {
            x5cHeader.map { CertificateDer(it.jsonPrimitive.content.decodeFromBase64()) }
        } catch (_: Exception) {
            return ClientValidationResult.Failure(ClientIdError.InvalidJws)
        }
        val leafCertificate = certificates.firstOrNull()
            ?: return ClientValidationResult.Failure(ClientIdError.EmptyX5cHeader)
        val leafCertDer = leafCertificate.bytes.toByteArray()

        // 1. Validate the certificate path, trust anchor, validity, constraints, and client-auth usage.
        try {
            validateClientAuthenticationCertificateChain(
                leaf = leafCertificate,
                chain = certificates.drop(1),
                trustAnchors = trustConfiguration.x509TrustAnchors,
            )
        } catch (cause: CancellationException) {
            throw cause
        } catch (_: Exception) {
            return ClientValidationResult.Failure(ClientIdError.InvalidSignature)
        }

        // 2. Verify JWS signature using the leaf certificate's public key.
        val key = try {
            ClientIdCrypto2.keyFromCertificate(leafCertDer)
        } catch (cause: CancellationException) {
            throw cause
        } catch (_: Exception) {
            return ClientValidationResult.Failure(ClientIdError.InvalidSignature)
        }
        log.trace { "Imported key from leaf cert der for X509SanDns: $key" }

        try {
            ClientIdCrypto2.verify(jws, key)
        } catch (cause: CancellationException) {
            throw cause
        } catch (_: Exception) {
            return ClientValidationResult.Failure(ClientIdError.InvalidSignature)
        }

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
            val responseUriHost = try {
                Url(responseUri).host
            } catch (_: Exception) {
                return ClientValidationResult.Failure(
                    ClientIdError.ResponseUriHostMismatch(clientId.dnsName, responseUri)
                )
            }
            if (responseUriHost != clientId.dnsName) {
                return ClientValidationResult.Failure(
                    ClientIdError.ResponseUriHostMismatch(clientId.dnsName, responseUriHost)
                )
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
