@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.openid4vp.clientidprefix.prefixes

import id.walt.crypto.utils.Base64Utils.decodeFromBase64
import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import id.walt.crypto2.jose.CompactJws
import id.walt.openid4vp.clientidprefix.ClientIdError
import id.walt.openid4vp.clientidprefix.ClientIdTrustConfiguration
import id.walt.openid4vp.clientidprefix.ClientValidationResult
import id.walt.openid4vp.clientidprefix.RequestContext
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.kotlincrypto.hash.sha2.SHA256

/**
 * Handles `x509_hash` prefix per OpenID4VP 1.0, Section 5.9.3.
 */
@Serializable
data class X509Hash(val hash: String, override val rawValue: String) : ClientId {
    init {
        // Check if it's a valid Base64URL string, common for hashes.
        val b64UrlRegex = "^[A-Za-z0-9_-]+$".toRegex()
        require(b64UrlRegex.matches(hash)) { "Hash must be a valid Base64URL string." }
    }

    companion object {
        /**
         * Derive the `x509_hash` client identifier value from the DER encoding of a leaf
         * certificate: the base64url-encoded SHA-256 hash, per OpenID4VP 1.0, Section 5.9.3.
         *
         * Shared so that producing a `client_id` and verifying one cannot drift apart.
         */
        fun hashOfCertificate(leafCertificateDer: ByteArray): String =
            SHA256().digest(leafCertificateDer).encodeToBase64Url()

        /** Full `x509_hash:<hash>` client identifier for the given DER-encoded leaf certificate. */
        fun clientIdForCertificate(leafCertificateDer: ByteArray): String =
            "x509_hash:${hashOfCertificate(leafCertificateDer)}"
    }

    suspend fun authenticateX509Hash(clientId: X509Hash, context: RequestContext): ClientValidationResult {
        return authenticateX509Hash(clientId, context, ClientIdTrustConfiguration())
    }

    suspend fun authenticateX509Hash(
        clientId: X509Hash,
        context: RequestContext,
        trustConfiguration: ClientIdTrustConfiguration,
    ): ClientValidationResult {
        val jws = context.requestObjectJws
            ?: return ClientValidationResult.Failure(ClientIdError.MissingRequestObject)
        if (trustConfiguration.x509TrustAnchors == null) {
            return ClientValidationResult.Failure(ClientIdError.MissingX509TrustAnchors)
        }

        val decoded = try {
            CompactJws.decodeUnverified(jws)
        } catch (_: Exception) {
            return ClientValidationResult.Failure(ClientIdError.InvalidJws)
        }
        val x5cValue = decoded.protectedHeader["x5c"]
            ?: return ClientValidationResult.Failure(ClientIdError.MissingX5cHeader)
        val x5cHeader = x5cValue as? JsonArray
            ?: return ClientValidationResult.Failure(ClientIdError.InvalidJws)
        val certificates = try {
            x5cHeader.map {
                ClientIdCrypto2.parseCertificate(
                    it.jsonPrimitive.content.decodeFromBase64()
                )
            }
        } catch (_: Exception) {
            return ClientValidationResult.Failure(ClientIdError.InvalidJws)
        }
        val leafCertificate = certificates.firstOrNull()
            ?: return ClientValidationResult.Failure(ClientIdError.EmptyX5cHeader)

        ClientIdCrypto2.validateCertificateChain(certificates, trustConfiguration.x509TrustStore)?.let { error ->
            return error
        }
        try {
            ClientIdCrypto2.verify(jws, leafCertificate.restoreSubjectPublicKey(ClientIdCrypto2.runtime))
        } catch (cause: CancellationException) {
            throw cause
        } catch (_: Exception) {
            return ClientValidationResult.Failure(ClientIdError.InvalidSignature)
        }
        // Via the shared helper rather than hashing inline: deriving an x509_hash client
        // identifier and verifying one must not be able to drift apart.
        val calculatedHash = hashOfCertificate(leafCertificate.encodedDer.toByteArray())
        if (clientId.hash != calculatedHash) {
            return ClientValidationResult.Failure(ClientIdError.X509HashMismatch)
        }
        return context.clientMetadata?.let(ClientValidationResult::Success)
            ?: ClientValidationResult.Failure(ClientIdError.MissingClientMetadata)
    }
}
