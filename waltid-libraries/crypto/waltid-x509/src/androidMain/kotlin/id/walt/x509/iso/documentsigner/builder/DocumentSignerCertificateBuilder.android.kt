package id.walt.x509.iso.documentsigner.builder

import id.walt.x509.iso.documentsigner.certificate.DocumentSignerCertificateBundle
import id.walt.x509.iso.documentsigner.certificate.DocumentSignerCertificateProfileData

internal actual suspend fun platformSignDocumentSignerCertificate(
    profileData: DocumentSignerCertificateProfileData,
    publicKey: id.walt.crypto.keys.Key,
    iacaSignerSpec: IACASignerSpecification
): DocumentSignerCertificateBundle {
    TODO("Not yet implemented")
}