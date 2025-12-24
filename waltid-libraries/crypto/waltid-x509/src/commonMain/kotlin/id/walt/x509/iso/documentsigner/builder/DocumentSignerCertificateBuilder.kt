@file:OptIn(ExperimentalTime::class)

package id.walt.x509.iso.documentsigner.builder

import id.walt.crypto.keys.Key
import id.walt.x509.iso.documentsigner.certificate.DocumentSignerCertificateBundle
import id.walt.x509.iso.documentsigner.certificate.DocumentSignerCertificateProfileData
import kotlin.time.ExperimentalTime

class DocumentSignerCertificateBuilder(
    val profileData: DocumentSignerCertificateProfileData,
    val publicKey: Key,
    val iacaSignerSpec: IACASignerSpecification,
) {

    //TODO: Add call to validator before calling platform sign function
    suspend fun build() = platformSignDocumentSignerCertificate(
        profileData = profileData,
        publicKey = publicKey,
        iacaSignerSpec = iacaSignerSpec,
    )
}

internal expect suspend fun platformSignDocumentSignerCertificate(
    profileData: DocumentSignerCertificateProfileData,
    publicKey: Key,
    iacaSignerSpec: IACASignerSpecification,
): DocumentSignerCertificateBundle
