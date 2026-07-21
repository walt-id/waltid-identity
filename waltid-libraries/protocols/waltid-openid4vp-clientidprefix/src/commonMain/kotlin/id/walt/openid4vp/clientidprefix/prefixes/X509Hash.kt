package id.walt.openid4vp.clientidprefix.prefixes

import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.Base64Utils.decodeFromBase64
import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import id.walt.crypto.utils.JwsUtils.decodeJws
import id.walt.openid4vp.clientidprefix.ClientIdError
import id.walt.openid4vp.clientidprefix.ClientValidationResult
import id.walt.openid4vp.clientidprefix.RequestContext
import id.walt.openid4vp.clientidprefix.requestObjectAlgorithmMatchesKey
import id.walt.x509.CertificateDer
import id.walt.x509.validateCertificateChain
import id.walt.x509.platformSupportsPkixCertificatePathValidation
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.jsonArray
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

    suspend fun authenticateX509Hash(clientId: X509Hash, context: RequestContext): ClientValidationResult {
        if (!platformSupportsPkixCertificatePathValidation) {
            return ClientValidationResult.Failure(ClientIdError.UnsupportedPlatformX509Validation)
        }
        val jws = context.requestObjectJws
            ?: return ClientValidationResult.Failure(ClientIdError.MissingRequestObject)

        val decodedJws = runCatching { jws.decodeJws() }
            .getOrElse { return ClientValidationResult.Failure(ClientIdError.InvalidJws) }
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

        // Verify the JWS independently so signature failures are not reported as trust failures.
        val key = runCatching { JWKKey.importFromDerCertificate(leafCertificate.bytes.toByteArray()).getOrThrow() }
            .getOrElse { return ClientValidationResult.Failure(ClientIdError.InvalidSignature) }
        if (!requestObjectAlgorithmMatchesKey(requestObjectAlgorithm, key.keyType)) {
            return ClientValidationResult.Failure(ClientIdError.InvalidSignature)
        }
        key.verifyJws(jws).getOrElse {
            return ClientValidationResult.Failure(ClientIdError.InvalidSignature)
        }

        val calculatedHash = SHA256().digest(leafCertificate.bytes.toByteArray()).encodeToBase64Url()
        if (clientId.hash != calculatedHash) {
            return ClientValidationResult.Failure(ClientIdError.X509HashMismatch)
        }

        val metadata = context.clientMetadata
            ?: return ClientValidationResult.Failure(ClientIdError.MissingClientMetadata)

        return ClientValidationResult.Success(metadata)
    }
}
