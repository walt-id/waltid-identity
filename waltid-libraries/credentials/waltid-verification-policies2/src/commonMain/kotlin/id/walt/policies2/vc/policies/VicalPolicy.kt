package id.walt.policies2.vc.policies

import id.walt.credentials.formats.DigitalCredential
import id.walt.credentials.formats.MdocsCredential
import id.walt.credentials.representations.X5CList
import id.walt.credentials.signatures.CoseCredentialSignature
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.Base64Utils.decodeFromBase64
import id.walt.vical.Vical
import id.walt.x509.CertificateDer
import id.walt.x509.validateCertificateChain
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.io.bytestring.ByteString
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
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
    val enableTrustedChainRoot: Boolean = false,
    val enableSystemTrustAnchors: Boolean = false,
    val enableRevocation: Boolean = false
) : CredentialVerificationPolicy2() {
    override val id = "vical"

    init {
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

    override suspend fun verify(credential: DigitalCredential): Result<JsonElement> {
        log.debug { "Verifying credential with VICAL policy" }
        try {
            val credentialSignature = credential.signature
            if (!(credential is MdocsCredential && credentialSignature is CoseCredentialSignature))
                throw IllegalArgumentException("VICAL policy can currently only be applied to mdocs")

            val x5cList =
                credentialSignature.x5cList ?: throw IllegalArgumentException("Credential has no x5c list")

            // Loading document signing certificate from x5c list
            val signingCert = loadDocumentSigningCertFromX5CList(credentialSignature, x5cList)

            // Loading the certificate chain from the provided credential
            val chain = x5cList.x5c.map {
                CertificateDer(it.base64Der.decodeFromBase64())
            }.filter { it != signingCert } // Do not put the signing cert in the chain

            val vicalBytes: ByteArray = resolveVicalBytes()
            val anchors: List<CertificateDer>? = loadTrustAnchorsFromVicalBytes(vicalBytes, credential.docType)

            validateCertificateChain(signingCert, chain, anchors, enableTrustedChainRoot, enableSystemTrustAnchors, enableRevocation)

        } catch (e: Exception) {
            return Result.failure(e)
        }

        return Result.success(
            JsonPrimitive(true)
        )
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
        HttpClient().use { client ->
            val response = client.get(url) {
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
     * Loads the document signing certificate from an X.509 certificate chain (x5c) list
     * using the provided credential signature and x5c list. The signing certificate is
     * validated based on its thumbprint.
     *
     * @param credentialSignature The COSE credential signature containing the signer key
     * and optional x5c list.
     * @param x5cList The X5CList containing the chain of X.509 certificates represented
     * as base64 DER encoded strings.
     * @return The document signing certificate as a `CertificateDer`.
     * @throws IllegalArgumentException If the signer key thumbprint cannot be determined,
     * or if the document signing certificate cannot be identified in the x5c list.
     */
    private suspend fun loadDocumentSigningCertFromX5CList(
        credentialSignature: CoseCredentialSignature,
        x5cList: X5CList
    ): CertificateDer {
        val signerKeyThumbprint = credentialSignature.signerKey.key.getThumbprint()

        if (signerKeyThumbprint.isEmpty()) throw IllegalArgumentException("Could not determine signer key thumbprint")

        val signingX5CCertificateString = x5cList.x5c.filter {
            val x5cCertThumbprint = JWKKey.importFromDerCertificate(it.base64Der.decodeFromBase64())
                .getOrNull()?.getThumbprint(); x5cCertThumbprint == signerKeyThumbprint
        }.getOrNull(0)
            ?: throw IllegalArgumentException("Could not determine document signing credential in x5c list")

        val signingCert = CertificateDer(signingX5CCertificateString.base64Der.decodeFromBase64())
        return signingCert
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
    private fun loadTrustAnchorsFromVicalBytes(vicalBytes: ByteArray, allowedDocType: String): List<CertificateDer>? {
        // decode VICAL
        val decodedVical = Vical.decode(vicalBytes)

        val certificateInfos = if (enableDocumentTypeValidation) {
            log.debug { "Document type validation is enabled" }
            decodedVical.vicalData.certificateInfos.filter { allowedDocType in it.docType }
        } else decodedVical.vicalData.certificateInfos

        // Build anchors from VICAL certificateInfos
        val anchorsFromVical: List<CertificateDer> = certificateInfos.map { info -> CertificateDer(info.certificate) }

        return anchorsFromVical.ifEmpty { null }
    }
}
