package id.walt.policies2.policies

import id.walt.credentials.formats.DigitalCredential
import id.walt.credentials.formats.MdocsCredential
import id.walt.credentials.representations.X5CList
import id.walt.credentials.signatures.CoseCredentialSignature
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.Base64Utils.decodeFromBase64
import id.walt.crypto.utils.JsonUtils.toJsonElement
import id.walt.vical.Vical
import id.walt.x509.CertificateDer
import id.walt.x509.validateCertificateChain
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

private val log = KotlinLogging.logger { }

@Serializable
@SerialName("vical")
data class VicalPolicy(
    /** the trusted VICAL file (base64 or hex encoded) */
    val vical: String,
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

            val anchors: List<CertificateDer>? = loadTrustAnchorsFromVical(this.vical)

            validateCertificateChain(signingCert, chain, anchors, enableRevocation)

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
     * @throws IllegalArgumentException If the signer key thumbprint cannot be determined
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
     * @return A list of DER-encoded certificates (`CertificateDer`) parsed from the VICAL data, or null if no anchors are available.
     */
    private fun loadTrustAnchorsFromVical(vicalBase64: String): List<CertificateDer>? {
        // decode VICAL
        val decodedVical = Vical.decode(vicalBase64.decodeFromBase64())

        // Build anchors from VICAL certificateInfos
        val anchorsFromVical: List<CertificateDer> =
            decodedVical.vicalData.certificateInfos
                .map { info -> CertificateDer(info.certificate) }

        // TODO: Handling of allowed document types
        // Optional: filter anchors by supported document type(s) declared in CertificateInfo.docType
        // val anchorsFiltered = anchorsFromVical.filter { itInfo -> itInfo.docType.any { dt -> dt in docTypes } }

        // Use anchorsFromVical if not empty, otherwise pass null so JVM implementation can try self-signed roots
        return anchorsFromVical.ifEmpty { null }
    }
}
