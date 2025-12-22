@file:OptIn(ExperimentalTime::class)

package id.walt.x509.iso.documentsigner.certificate

import kotlin.time.ExperimentalTime
import kotlin.time.Instant

data class DocumentSignerCertificateProfileData(
    val country: String,
    val commonName: String,
    val notBefore: Instant,
    val notAfter: Instant,
    val crlDistributionPointUri: String,
    val stateOrProvinceName: String? = null,
    val organizationName: String? = null,
    val localityName: String? = null,
)