package id.walt.policies2.vc.policies

import id.walt.certificate.x509.X509Certificate
import id.walt.certificate.x509.X509CertificateTrustStore
import id.walt.certificate.x509.X509CertificateUtil
import id.walt.certificate.x509.truststore.InMemoryTrustStore
import id.walt.credentials.formats.DigitalCredential
import id.walt.credentials.formats.MdocsCredential
import id.walt.credentials.signatures.CoseCredentialSignature
import id.walt.crypto.utils.Base64Utils.decodeFromBase64
import id.walt.vical.Vical
import id.walt.webdatafetching.WebDataFetcher
import id.walt.webdatafetching.WebDataFetcherId
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.io.bytestring.ByteString
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

private val log = KotlinLogging.logger { }

/**
 * A verification policy for VICAL-based credentials. This policy validates the authenticity,
 * integrity, and trustworthiness of digital credentials using VICAL data. It provides
 * configuration options for document type validation, system trust anchors, trusted chain roots,
 * and revocation checks.
 *
 * The VICAL can be supplied in one of two ways (exactly one must be set):
 * - [vical]: The trusted VICAL, encoded as Base64 (inline, embedded in the policy config).
 * - [vicalUrl]: An `http(s)://` URL from which the VICAL will be fetched at verification time.
 *   The response body is expected to be the raw CBOR VICAL bytes (as returned by the VICAL
 *   service `/latest` endpoint).
 *
 * The URL-based pattern is preferred in production because it decouples the policy configuration
 * from the VICAL artifact size and allows the trust list to be updated without redeploying config.
 *
 * @property vical The trusted VICAL file, encoded as Base64. Leave empty when [vicalUrl] is set.
 * @property vicalUrl An HTTP(S) URL to fetch the raw VICAL bytes from at verification time.
 * @property enableDocumentTypeValidation Flag to enable or disable validation of the credentials document type
 * against the VICAL data.
 * @property enableTrustedChainRoot Flag to enable or disable the use of a trusted root certificate (self-signed) in the chain.
 * @property enableSystemTrustAnchors Flag to enable or disable the use of system trust anchors.
 * @property enableRevocation Flag to enable or disable revocation checks.
 */
@Serializable
@SerialName("vical")
data class VicalPolicy(
    val vical: String = "",
    val vicalUrl: String? = null,
    val enableDocumentTypeValidation: Boolean = false,
    val enableRevocation: Boolean = false
) : CredentialVerificationPolicy2() {
    override val id = "vical"

    companion object {
        private val fetcher = WebDataFetcher(WebDataFetcherId.VICAL_POLICY)
    }

    init {
        require(!enableRevocation) { "VICAL policy does not support certificate revocation checks" }
        require(vical.isNotBlank() || vicalUrl != null) {
            "VicalPolicy: either 'vical' (Base64-encoded) or 'vicalUrl' (http/https URL) must be provided"
        }
        require(vical.isBlank() || vicalUrl == null) {
            "VicalPolicy: 'vical' and 'vicalUrl' are mutually exclusive — set exactly one"
        }
        vicalUrl?.let {
            require(it.startsWith("http://") || it.startsWith("https://")) {
                "VicalPolicy: 'vicalUrl' must be an http or https URL, got: $it"
            }
        }
    }

    override suspend fun verify(
        credential: DigitalCredential,
        context: PolicyExecutionContext
    ): Result<JsonElement> {
        log.debug { "Verifying credential with VICAL policy" }
        try {
            val credentialSignature = credential.signature
            if (!(credential is MdocsCredential && credentialSignature is CoseCredentialSignature))
                throw IllegalArgumentException("VICAL policy can currently only be applied to mdocs")

            val x5cList =
                credentialSignature.x5cList ?: throw IllegalArgumentException("Credential has no x5c list")

            // Loading the certificate chain from the provided credential
            val chain = x5cList.x5c.map {
                X509CertificateUtil.parseCertificateDerEncoded(ByteString(it.base64Der.decodeFromBase64()))
            }

            val vicalBytes: ByteArray = resolveVicalBytes()
            val anchors = loadTrustAnchorsFromVicalBytes(vicalBytes, credential.docType)
            val validationResult = X509CertificateUtil.validateCertificateChain(chain, anchors)
            return if (validationResult.valid) {
                Result.success(
                    JsonPrimitive(true)
                )
            } else {
                Result.failure(Exception("Certificate Chain validation failed"))
            }
        } catch (e: Exception) {
            return Result.failure(e)
        }

    }

    /**
     * Resolves the raw VICAL CBOR bytes from either the inline Base64 [vical] string
     * or by fetching [vicalUrl] over HTTP(S).
     */
    private suspend fun resolveVicalBytes(): ByteArray {
        return if (vicalUrl != null) {
            log.debug { "Fetching VICAL from URL: $vicalUrl" }
            fetchVicalFromUrl(vicalUrl)
        } else {
            log.debug { "Decoding inline Base64 VICAL" }
            vical.decodeFromBase64()
        }
    }

    /**
     * Fetches the VICAL bytes from the given HTTP(S) URL.
     *
     * Supports two response encodings:
     * - **Binary** (`application/cbor`, `application/octet-stream`): raw CBOR bytes used as-is.
     * - **Text** (`text/plain` or similar): assumed to be a hex-encoded CBOR string, decoded to bytes.
     *
     * Tip: append `?format=cbor` to the URL if the server defaults to hex.
     *
     * @throws IllegalStateException if the server returns a non-2xx status.
     * @throws Exception if the network request fails.
     */
    private suspend fun fetchVicalFromUrl(url: String): ByteArray {
        val response = fetcher.rawFetch(url) {
            headers {
                append(HttpHeaders.Accept, "application/cbor, application/octet-stream, */*")
            }
        }
        if (!response.status.isSuccess()) {
            throw IllegalStateException(
                "Failed to fetch VICAL from $url \u2014 HTTP ${response.status.value} ${response.status.description}"
            )
        }
        val contentType = response.contentType()
        return if (contentType?.match(ContentType.Application.Cbor) == true ||
                   contentType?.match(ContentType.Application.OctetStream) == true) {
            log.debug { "VICAL response is binary ($contentType)" }
            response.body()
        } else {
            // Assume hex-encoded text (the default format of the enterprise VICAL endpoint)
            val text = response.bodyAsText().trim()
            log.debug { "VICAL response is text ($contentType), decoding ${text.length} hex chars" }
            text.decodeHexToBytes()
        }
    }

    /** Decodes a hex string (even-length, lowercase or uppercase) to a [ByteArray]. */
    private fun String.decodeHexToBytes(): ByteArray {
        require(length % 2 == 0) { "Hex string must have even length, got $length" }
        return ByteArray(length / 2) { i ->
            val hi = hexCharToInt(this[i * 2])
            val lo = hexCharToInt(this[i * 2 + 1])
            ((hi shl 4) or lo).toByte()
        }
    }

    private fun hexCharToInt(c: Char): Int = when (c) {
        in '0'..'9' -> c - '0'
        in 'a'..'f' -> c - 'a' + 10
        in 'A'..'F' -> c - 'A' + 10
        else -> throw IllegalArgumentException("Invalid hex character: $c")
    }

    /**
     * Loads a list of trust anchors from the provided VICAL data encoded as a Base64 string.
     * The method decodes the input, processes certificate information, and returns a list of
     * DER-encoded certificates to be used as trust anchors. If no anchors are available after
     * processing, the method returns null.
     *
     * @param vicalBase64 The Base64-encoded string representing the VICAL data.
     * @param allowedDocType If `documentTypeValidation` is `true`, the document type to be validated against the VICAL data.
     * @return A list of DER-encoded certificates (`CertificateDer`) parsed from the VICAL data, or null if no anchors are available.
     */
    private fun loadTrustAnchorsFromVicalBytes(
        vicalBytes: ByteArray,
        allowedDocType: String
    ): X509CertificateTrustStore? {
        // decode VICAL
        val decodedVical = Vical.decode(vicalBytes)

        val certificateInfos = if (enableDocumentTypeValidation) {
            log.debug { "Document type validation is enabled" }
            decodedVical.vicalData.certificateInfos.filter { allowedDocType in it.docType }
        } else decodedVical.vicalData.certificateInfos

        // Build anchors from VICAL certificateInfos
        val anchorsFromVical: List<X509Certificate> = certificateInfos.map { info ->
            X509CertificateUtil.parseCertificateDerEncoded(ByteString(info.certificate))
        }
        return if (anchorsFromVical.isEmpty()) {
            null
        } else {
            InMemoryTrustStore(anchorsFromVical)
        }
    }
}
