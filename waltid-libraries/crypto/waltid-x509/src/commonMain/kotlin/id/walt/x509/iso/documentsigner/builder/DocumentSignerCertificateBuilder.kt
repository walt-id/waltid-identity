@file:OptIn(ExperimentalTime::class)

package id.walt.x509.iso.documentsigner.builder

import id.walt.crypto.keys.Key
import id.walt.x509.iso.documentsigner.certificate.DocumentSignerCertificateBundle
import id.walt.x509.iso.documentsigner.certificate.DocumentSignerCertificateProfileData
import id.walt.x509.iso.documentsigner.validate.DocumentSignerValidator
import id.walt.x509.iso.iaca.validate.IACAValidator
import kotlin.time.ExperimentalTime

class DocumentSignerCertificateBuilder {

    suspend fun build(
        profileData: DocumentSignerCertificateProfileData,
        publicKey: Key,
        iacaSignerSpec: IACASignerSpecification,
    ): DocumentSignerCertificateBundle {
        val iacaValidator = IACAValidator()
        iacaValidator.validateSigningKey(iacaSignerSpec.signingKey)
        iacaValidator.validateCertificateProfileData(iacaSignerSpec.profileData)
        val dsValidator = DocumentSignerValidator()
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
