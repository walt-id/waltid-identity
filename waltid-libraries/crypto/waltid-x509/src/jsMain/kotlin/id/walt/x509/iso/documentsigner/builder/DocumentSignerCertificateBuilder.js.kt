@file:OptIn(ExperimentalTime::class)

package id.walt.x509.iso.documentsigner.builder

import id.walt.crypto.keys.Key
import id.walt.x509.iso.documentsigner.certificate.DocumentSignerCertificateBundle
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

internal actual suspend fun platformSignDocumentSignerCertificate(
    country: String,
    commonName: String,
    notBefore: Instant,
    notAfter: Instant,
    crlDistributionPointUri: String,
    dsPublicKey: Key,
    iacaSignerSpec: IACASignerSpecification,
    stateOrProvinceName: String?,
    organizationName: String?,
    localityName: String?
): DocumentSignerCertificateBundle {
    TODO("Not yet implemented")
}