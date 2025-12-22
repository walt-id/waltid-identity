@file:OptIn(ExperimentalTime::class)

package id.walt.x509.iso.documentsigner.certificate

import id.walt.x509.CertificateKeyUsage
import id.walt.x509.iso.CertificateValidityPeriod
import okio.ByteString
import kotlin.time.ExperimentalTime

data class DocumentSignerDecodedCertificate(
    val country: String,
    val commonName: String,
    val validityPeriod: CertificateValidityPeriod,
    val crlDistributionPointUri: String,
    val serialNumber: ByteString, //
    val keyUsage: Set<CertificateKeyUsage>, //
    val isCA: Boolean,
    val stateOrProvinceName: String? = null,
    val organizationName: String? = null,
    val localityName: String? = null,
) {

    fun toDocumentSignerCertificateProfileData() = DocumentSignerCertificateProfileData(
        country = country,
        commonName = commonName,
        validityPeriod = validityPeriod,
        crlDistributionPointUri = crlDistributionPointUri,
        stateOrProvinceName = stateOrProvinceName,
        organizationName = organizationName,
        localityName = localityName,
    )
}
