package id.walt.x509

import okio.ByteString
import okio.ByteString.Companion.toByteString
import kotlin.io.encoding.Base64

/**
 * DER encoded X.509 certificate as platform-agnostic wrapper.
 */
data class CertificateDer(
    val bytes: ByteString,
) {
    /**
     * Convert certificate DER bytes to PEM-encoded string.
     */
    fun toPEMEncodedString() = "$PEM_HEADER\r\n" +
            Base64.Pem.encode(bytes.toByteArray()) +
            "\r\n$PEM_FOOTER"

    companion object {
        private const val PEM_HEADER = "-----BEGIN CERTIFICATE-----"
        private const val PEM_FOOTER = "-----END CERTIFICATE-----"

        fun fromPEMEncodedString(
            pemEncodedCertificate: String,
        ): CertificateDer {
            val base64Payload = extractPemBase64Payload(
                pemEncodedCertificate = pemEncodedCertificate,
            )
            val decodedPayload = Base64.Pem.decode(
                source = base64Payload,
            )
            return CertificateDer(
                bytes = Base64.Pem.decode(
                    source = base64Payload,
                ).toByteString(),
            )
        }

        private fun extractPemBase64Payload(
            pemEncodedCertificate: String,
        ): String {
            val trimmedPem = pemEncodedCertificate.trim()
            require(
                trimmedPem.startsWith(PEM_HEADER),
            ) {
                "PEM header not found."
            }
            require(
                trimmedPem.endsWith(PEM_FOOTER)
            ) {
                "PEM footer not found."
            }

            val base64Payload = trimmedPem
                .removePrefix(PEM_HEADER)
                .removeSuffix(PEM_FOOTER)
                .filterNot {
                    it.isWhitespace()
                }

            require(
                value = base64Payload.isNotBlank()
            ) {
                "PEM payload is empty."
            }

            return base64Payload
        }
    }
}

/**
 * Validate a leaf certificate against a provided chain and trust anchors.
 *
 * @param leaf         DER-encoded end-entity certificate.
 * @param chain        DER-encoded certs (intermediates, optionally root). Order not required.
 * @param trustAnchors DER-encoded trust roots. If null/empty, an included self-signed root may be used.
 * @property enableTrustedChainRoot Flag to enable or disable the use of a trusted root certificate (self-signed) in the chain.
 * @property enableSystemTrustAnchors Flag to enable or disable the use of system trust anchors.
 * @param enableRevocation Best-effort CRL/OCSP when supported on platform.
 *
 * @throws X509ValidationException when validation fails.
 */
@Throws(X509ValidationException::class)
expect fun validateCertificateChain(
    leaf: CertificateDer,
    chain: List<CertificateDer>,
    trustAnchors: List<CertificateDer>? = null,
    enableTrustedChainRoot: Boolean = false,
    enableSystemTrustAnchors: Boolean = false,
    enableRevocation: Boolean = false
)

class X509ValidationException(message: String, cause: Throwable? = null) : Exception(message, cause)
