@file:OptIn(ExperimentalTime::class)

package id.walt.x509.iso.documentsigner.certificate

import id.walt.x509.CertificateKeyUsage
import okio.ByteString
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

data class DocumentSignerDecodedCertificate(
    val country: String,
    val commonName: String,
    val notBefore: Instant,
    val notAfter: Instant,
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
        notBefore = notBefore,
        notAfter = notAfter,
        crlDistributionPointUri = crlDistributionPointUri,
        stateOrProvinceName = stateOrProvinceName,
        organizationName = organizationName,
        localityName = localityName,
    )
}
