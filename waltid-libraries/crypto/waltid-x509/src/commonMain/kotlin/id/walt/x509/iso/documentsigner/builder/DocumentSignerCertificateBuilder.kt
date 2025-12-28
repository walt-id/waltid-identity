@file:OptIn(ExperimentalTime::class)

package id.walt.x509.iso.documentsigner.builder

import id.walt.crypto.keys.Key
import id.walt.x509.iso.documentsigner.certificate.DocumentSignerCertificateBundle
import id.walt.x509.iso.documentsigner.certificate.DocumentSignerCertificateProfileData
import id.walt.x509.iso.documentsigner.validate.DocumentSignerValidator
import kotlin.time.ExperimentalTime

class DocumentSignerCertificateBuilder {

    private val dsValidator by lazy {
        DocumentSignerValidator()
    }

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

}

internal expect suspend fun platformSignDocumentSignerCertificate(
    profileData: DocumentSignerCertificateProfileData,
    publicKey: Key,
    iacaSignerSpec: IACASignerSpecification,
): DocumentSignerCertificateBundle
