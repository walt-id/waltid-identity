@file:OptIn(ExperimentalTime::class)

package id.walt.x509.iso.documentsigner.builder

import id.walt.crypto.keys.Key
import id.walt.x509.iso.blockingBridge
import id.walt.x509.iso.documentsigner.certificate.DocumentSignerCertificateBundle
import id.walt.x509.iso.documentsigner.certificate.DocumentSignerCertificateProfileData
import id.walt.x509.iso.documentsigner.validate.DocumentSignerValidator
import kotlin.time.ExperimentalTime

/**
 * Builder for ISO Document Signer X.509 certificates.
 *
 * The builder validates profile data, the public key, and
 * the issuing IACA profile data before delegating to platform-specific signing.
 *
 * Validity period precision: input [kotlin.time.Instant] values are truncated
 * to whole seconds when encoded. Sub-second precision is discarded and the
 * returned decoded certificate reflects this truncation.
 */
class DocumentSignerCertificateBuilder {

    private val dsValidator by lazy {
        DocumentSignerValidator()
    }

    /**
     * Build a new Document Signer certificate.
     *
     * @param profileData ISO profile inputs for generating the Document Signer X.509 certificate.
     * @param publicKey Document Signer public key (must not include a private key).
     * @param iacaSignerSpec Issuing IACA profile data and key for signing the Document Signer's X.509 certificate.
     *
     * Note: Validity period instants are stored with second-level precision;
     * any milliseconds or nanoseconds in the input are discarded. The decoded
     * certificate returned by this builder exposes the truncated values.
     */
    suspend fun build(
        profileData: DocumentSignerCertificateProfileData,
        publicKey: Key,
        iacaSignerSpec: IACASignerSpecification,
    ): DocumentSignerCertificateBundle {
        dsValidator.validateDocumentSignerPublicKey(publicKey)
        dsValidator.validateDocumentSignerProfileData(profileData)
        dsValidator.validateProfileDataAgainstIACAProfileData(
            dsProfileData = profileData,
            iacaProfileData = iacaSignerSpec.profileData,
        )
        return platformSignDocumentSignerCertificate(
            profileData = profileData,
            publicKey = publicKey,
            iacaSignerSpec = iacaSignerSpec,
        )
    }

    /**
     * Blocking variant of [build].
     */
    fun buildBlocking(
        profileData: DocumentSignerCertificateProfileData,
        publicKey: Key,
        iacaSignerSpec: IACASignerSpecification,
    ): DocumentSignerCertificateBundle = blockingBridge {
        build(
            profileData = profileData,
            publicKey = publicKey,
            iacaSignerSpec = iacaSignerSpec,
        )
    }

}

/**
 * Platform-specific signing implementation for Document Signer X.509 certificates.
 */
internal expect suspend fun platformSignDocumentSignerCertificate(
    profileData: DocumentSignerCertificateProfileData,
    publicKey: Key,
    iacaSignerSpec: IACASignerSpecification,
): DocumentSignerCertificateBundle
