@file:OptIn(ExperimentalTime::class)

package id.walt.x509.iso.documentsigner.certificate

import id.walt.crypto.keys.Key
import id.walt.x509.CertificateDer
import id.walt.x509.CertificateKeyUsage
import id.walt.x509.iso.CertificateValidityPeriod
import id.walt.x509.iso.iaca.certificate.IACADecodedCertificate
import okio.ByteString
import kotlin.time.ExperimentalTime

@ConsistentCopyVisibility
data class DocumentSignerDecodedCertificate internal constructor(
    val principalName: DocumentSignerPrincipalName,
    val validityPeriod: CertificateValidityPeriod,
    val crlDistributionPointUri: String,
    val serialNumber: ByteString, //
    val keyUsage: Set<CertificateKeyUsage>, //
    val isCA: Boolean,
    val publicKey: Key,
    private val certificate: CertificateDer,
) {

    fun toDocumentSignerCertificateProfileData() = DocumentSignerCertificateProfileData(
        principalName = principalName,
        validityPeriod = validityPeriod,
        crlDistributionPointUri = crlDistributionPointUri,
    )

    suspend fun validate(
        iacaDecodedCertificate: IACADecodedCertificate,
    ) {

    }
}
