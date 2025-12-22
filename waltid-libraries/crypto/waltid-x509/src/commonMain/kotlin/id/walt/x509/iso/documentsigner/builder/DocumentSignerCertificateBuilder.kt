@file:OptIn(ExperimentalTime::class)

package id.walt.x509.iso.documentsigner.builder

import id.walt.crypto.keys.Key
import id.walt.x509.iso.documentsigner.certificate.DocumentSignerCertificateBundle
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
//TODO: Use data class for profile data
class DocumentSignerCertificateBuilder(
    val country: String,
    val commonName: String,
    val notBefore: Instant,
    val notAfter: Instant,
    val crlDistributionPointUri: String,
    val documentSignerPublicKey: Key,
    val iacaSignerSpec: IACASignerSpecification,
) {

    var stateOrProvinceName: String? = null
    var organizationName: String? = null
    var localityName: String? = null

    //TODO: Add call to validator before calling platform sign function
    suspend fun build() = platformSignDocumentSignerCertificate(
        country = country,
        commonName = commonName,
        notBefore = notBefore,
        notAfter = notAfter,
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
    notBefore: Instant,
    notAfter: Instant,
    crlDistributionPointUri: String,
    dsPublicKey: Key,
    iacaSignerSpec: IACASignerSpecification,
    stateOrProvinceName: String? = null,
    organizationName: String? = null,
    localityName: String? = null,
): DocumentSignerCertificateBundle