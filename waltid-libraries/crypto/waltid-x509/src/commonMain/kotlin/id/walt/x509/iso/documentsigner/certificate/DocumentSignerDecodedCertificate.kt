@file:OptIn(ExperimentalTime::class)

package id.walt.x509.iso.documentsigner.certificate

import id.walt.crypto.keys.Key
import id.walt.x509.CertificateBasicConstraints
import id.walt.x509.CertificateKeyUsage
import id.walt.x509.X509CertificateHandle
import id.walt.x509.X509V3ExtensionOID
import id.walt.x509.iso.CertificateValidityPeriod
import id.walt.x509.iso.IssuerAlternativeName
import id.walt.x509.iso.documentsigner.validate.DocumentSignerValidator
import id.walt.x509.iso.iaca.certificate.IACADecodedCertificate
import id.walt.x509.iso.iaca.certificate.IACAPrincipalName
import okio.ByteString
import kotlin.time.ExperimentalTime

@ConsistentCopyVisibility
data class DocumentSignerDecodedCertificate internal constructor(
    val issuerPrincipalName: IACAPrincipalName,
    val principalName: DocumentSignerPrincipalName,
    val validityPeriod: CertificateValidityPeriod,
    val issuerAlternativeName: IssuerAlternativeName,
    val crlDistributionPointUri: String,
    val serialNumber: ByteString,
    val keyUsage: Set<CertificateKeyUsage>,
    val extendedKeyUsage: Set<String>,
    val akiHex: String,
    val skiHex: String,
    val basicConstraints: CertificateBasicConstraints,
    val publicKey: Key,
    val criticalExtensionOIDs: Set<X509V3ExtensionOID>,
    val nonCriticalExtensionOIDs: Set<X509V3ExtensionOID>,
    private val certificate: X509CertificateHandle,
) {

    fun toDocumentSignerCertificateProfileData() = DocumentSignerCertificateProfileData(
        principalName = principalName,
        validityPeriod = validityPeriod,
        crlDistributionPointUri = crlDistributionPointUri,
    )

    suspend fun validate(
        validIACADecodedCert: IACADecodedCertificate,
    ) {
        _validator.validateDecodedCertificate(
            dsDecodedCert = this,
            iacaDecodedCert = validIACADecodedCert,
        )
        certificate.verifySignature(validIACADecodedCert.publicKey)
    }

    companion object {

        private val _validator by lazy {
            DocumentSignerValidator()
        }
    }
}
