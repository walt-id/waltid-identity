@file:OptIn(ExperimentalTime::class)

package id.walt.x509.iso.documentsigner.builder

import id.walt.crypto.keys.Key
import id.walt.x509.iso.CertificateValidityPeriod
import id.walt.x509.iso.documentsigner.certificate.DocumentSignerCertificateBundle
import id.walt.x509.iso.documentsigner.certificate.DocumentSignerPrincipalName
import kotlin.time.ExperimentalTime

internal actual suspend fun platformSignDocumentSignerCertificate(
    principalName: DocumentSignerPrincipalName,
    validityPeriod: CertificateValidityPeriod,
    crlDistributionPointUri: String,
    dsPublicKey: Key,
    iacaSignerSpec: IACASignerSpecification,
): DocumentSignerCertificateBundle {
    TODO("Not yet implemented")
}
