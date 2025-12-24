@file:OptIn(ExperimentalTime::class)

package id.walt.x509.iso.documentsigner.certificate

import id.walt.crypto.keys.Key
import id.walt.x509.CertificateKeyUsage
import id.walt.x509.iso.CertificateValidityPeriod
import okio.ByteString
import kotlin.time.ExperimentalTime

data class DocumentSignerDecodedCertificate(
    val principalName: DocumentSignerPrincipalName,
    val validityPeriod: CertificateValidityPeriod,
    val crlDistributionPointUri: String,
    val serialNumber: ByteString, //
    val keyUsage: Set<CertificateKeyUsage>, //
    val isCA: Boolean,
    val publicKey: Key,
) {

    fun toDocumentSignerCertificateProfileData() = DocumentSignerCertificateProfileData(
        principalName = principalName,
        validityPeriod = validityPeriod,
        crlDistributionPointUri = crlDistributionPointUri,
    )
}
