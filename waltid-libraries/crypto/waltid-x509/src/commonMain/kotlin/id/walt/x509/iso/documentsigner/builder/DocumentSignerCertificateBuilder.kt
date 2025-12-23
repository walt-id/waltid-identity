@file:OptIn(ExperimentalTime::class)

package id.walt.x509.iso.documentsigner.builder

import id.walt.crypto.keys.Key
import id.walt.x509.iso.CertificateValidityPeriod
import id.walt.x509.iso.documentsigner.certificate.DocumentSignerCertificateBundle
import id.walt.x509.iso.documentsigner.certificate.DocumentSignerPrincipalName
import kotlin.time.ExperimentalTime

//TODO: Use data class for profile data
class DocumentSignerCertificateBuilder(
    val principalName: DocumentSignerPrincipalName,
    val validityPeriod: CertificateValidityPeriod,
    val crlDistributionPointUri: String,
    val documentSignerPublicKey: Key,
    val iacaSignerSpec: IACASignerSpecification,
) {

    //TODO: Add call to validator before calling platform sign function
    suspend fun build() = platformSignDocumentSignerCertificate(
        principalName = principalName,
        validityPeriod = validityPeriod,
        crlDistributionPointUri = crlDistributionPointUri,
        dsPublicKey = documentSignerPublicKey,
        iacaSignerSpec = iacaSignerSpec,
    )
}

internal expect suspend fun platformSignDocumentSignerCertificate(
    principalName: DocumentSignerPrincipalName,
    validityPeriod: CertificateValidityPeriod,
    crlDistributionPointUri: String,
    dsPublicKey: Key,
    iacaSignerSpec: IACASignerSpecification,
): DocumentSignerCertificateBundle
