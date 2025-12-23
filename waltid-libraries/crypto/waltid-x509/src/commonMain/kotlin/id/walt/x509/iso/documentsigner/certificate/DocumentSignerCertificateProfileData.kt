@file:OptIn(ExperimentalTime::class)

package id.walt.x509.iso.documentsigner.certificate

import id.walt.x509.iso.CertificateValidityPeriod
import kotlin.time.ExperimentalTime

data class DocumentSignerCertificateProfileData(
    val principalName: DocumentSignerPrincipalName,
    val validityPeriod: CertificateValidityPeriod,
    val crlDistributionPointUri: String,
)
