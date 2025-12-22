@file:OptIn(ExperimentalTime::class)

package id.walt.x509.iso.documentsigner.certificate

import id.walt.x509.iso.CertificateValidityPeriod
import kotlin.time.ExperimentalTime

data class DocumentSignerCertificateProfileData(
    val country: String,
    val commonName: String,
    val validityPeriod: CertificateValidityPeriod,
    val crlDistributionPointUri: String,
    val stateOrProvinceName: String? = null,
    val organizationName: String? = null,
    val localityName: String? = null,
)
