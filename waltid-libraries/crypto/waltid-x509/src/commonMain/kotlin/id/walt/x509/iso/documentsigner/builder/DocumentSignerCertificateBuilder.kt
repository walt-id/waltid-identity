@file:OptIn(ExperimentalTime::class)

package id.walt.x509.iso.documentsigner.builder

import id.walt.crypto.keys.Key
import id.walt.x509.iso.documentsigner.certificate.DocumentSignerCertificateBundle
import id.walt.x509.iso.CertificateValidityPeriod
import kotlin.time.ExperimentalTime
//TODO: Use data class for profile data
class DocumentSignerCertificateBuilder(
    val country: String,
    val commonName: String,
    val validityPeriod: CertificateValidityPeriod,
    val crlDistributionPointUri: String,
    val documentSignerPublicKey: Key,
    val iacaSignerSpec: IACASignerSpecification,
) {

    val notBefore get() = validityPeriod.notBefore
    val notAfter get() = validityPeriod.notAfter

    var stateOrProvinceName: String? = null
    var organizationName: String? = null
    var localityName: String? = null

    //TODO: Add call to validator before calling platform sign function
    suspend fun build() = platformSignDocumentSignerCertificate(
        country = country,
        commonName = commonName,
        validityPeriod = validityPeriod,
        crlDistributionPointUri = crlDistributionPointUri,
        dsPublicKey = documentSignerPublicKey,
        iacaSignerSpec = iacaSignerSpec,
        stateOrProvinceName = stateOrProvinceName,
        organizationName = organizationName,
        localityName = localityName,
    )
}

internal expect suspend fun platformSignDocumentSignerCertificate(
    country: String,
    commonName: String,
    validityPeriod: CertificateValidityPeriod,
    crlDistributionPointUri: String,
    dsPublicKey: Key,
    iacaSignerSpec: IACASignerSpecification,
    stateOrProvinceName: String? = null,
    organizationName: String? = null,
    localityName: String? = null,
): DocumentSignerCertificateBundle
