package id.walt.x509.iso.documentsigner

import id.walt.crypto.keys.Key
import id.walt.x509.iso.documentsigner.builder.IACASignerSpecification
import id.walt.x509.iso.documentsigner.certificate.DocumentSignerCertificateProfileData

data class DocumentSignerBuilderInputTestVector(
    val profileData: DocumentSignerCertificateProfileData,
    val publicKey: Key,
    val iacaSignerSpec: IACASignerSpecification,
)