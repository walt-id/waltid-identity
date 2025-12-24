@file:OptIn(ExperimentalTime::class)

package id.walt.x509.iso.documentsigner.builder

import id.walt.crypto.keys.Key
import id.walt.x509.iso.documentsigner.certificate.DocumentSignerCertificateBundle
import id.walt.x509.iso.documentsigner.certificate.DocumentSignerCertificateProfileData
import id.walt.x509.iso.documentsigner.validate.DocumentSignerValidator
import id.walt.x509.iso.iaca.validate.IACAValidator
import kotlin.time.ExperimentalTime

class DocumentSignerCertificateBuilder(
    val profileData: DocumentSignerCertificateProfileData,
    val publicKey: Key,
    val iacaSignerSpec: IACASignerSpecification,
) {

    suspend fun build(): DocumentSignerCertificateBundle {
        val iacaValidator = IACAValidator()
        iacaValidator.validateIACASigningKey(iacaSignerSpec.signingKey)
        iacaValidator.validateIACACertificateProfileData(iacaSignerSpec.profileData)
        val dsValidator = DocumentSignerValidator()
        dsValidator.validateDocumentSignerPublicKey(publicKey)
        dsValidator.validateDocumentSignerProfileData(profileData)
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
