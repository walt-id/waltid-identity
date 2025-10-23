package id.walt.policies2.policies

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
 * @property vical The trusted VICAL file, encoded as either Base64 or hexadecimal.
 * @property enableDocumentTypeValidation Flag to enable or disable validation of the credentials document type
 * against the VICAL data.
 * @property enableTrustedChainRoot Flag to enable or disable the use of a trusted root certificate (self-signed) in the chain.
 * @property enableSystemTrustAnchors Flag to enable or disable the use of system trust anchors.
 * @property enableRevocation Flag to enable or disable revocation checks.
 */
@Serializable
@SerialName("vical")
data class VicalPolicy(
    val vical: String,
    val enableDocumentTypeValidation: Boolean = false,
    val enableTrustedChainRoot: Boolean = false,
    val enableSystemTrustAnchors: Boolean = false,
    val enableRevocation: Boolean = false
) : VerificationPolicy2() {
    override val id = "vical"

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

            val anchors: List<CertificateDer>? = loadTrustAnchorsFromVical(this.vical, credential.docType)

            validateCertificateChain(signingCert, chain, anchors, enableTrustedChainRoot, enableSystemTrustAnchors, enableRevocation)

        } catch (e: Exception) {
            return Result.failure(e)
        }

        return Result.success(
            JsonPrimitive(true)
        )
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
    private fun loadTrustAnchorsFromVical(vicalBase64: String, allowedDocType: String): List<CertificateDer>? {
        // decode VICAL
        val decodedVical = Vical.decode(vicalBase64.decodeFromBase64())

        val certificateInfos = if (enableDocumentTypeValidation) {
            log.debug { "Document type validation is enabled" }
            decodedVical.vicalData.certificateInfos.filter { allowedDocType in it.docType }
        } else decodedVical.vicalData.certificateInfos

        // Build anchors from VICAL certificateInfos
        val anchorsFromVical: List<CertificateDer> = certificateInfos.map { info -> CertificateDer(info.certificate) }

        return anchorsFromVical.ifEmpty { null }
    }
}
