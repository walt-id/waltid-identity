@file:OptIn(ExperimentalTime::class)

package id.walt.x509.iso.documentsigner.certificate

import id.walt.crypto.keys.Key
import id.walt.x509.*
import id.walt.x509.iso.IssuerAlternativeName
import id.walt.x509.iso.iaca.certificate.IACAPrincipalName
import okio.ByteString
import kotlin.time.ExperimentalTime

/**
 * Decoded view (not validated) of a Document Signer X.509 certificate.
 *
 * Clients should validate instances via the [id.walt.x509.iso.documentsigner.validate.DocumentSignerValidator]
 * class
 *
 * Instances are created by platform parsers and expose the parsed ISO
 * profile data, among other relevant data points necessary for, subsequent, validations.
 */
@ConsistentCopyVisibility
data class DocumentSignerDecodedCertificate internal constructor(
    val issuerPrincipalName: IACAPrincipalName,
    val principalName: DocumentSignerPrincipalName,
    val validityPeriod: X509ValidityPeriod,
    val issuerAlternativeName: IssuerAlternativeName,
    val crlDistributionPointUri: String,
    val serialNumber: ByteString,
    val keyUsage: Set<X509KeyUsage>,
    val extendedKeyUsage: Set<String>,
    val akiHex: String,
    val skiHex: String,
    val basicConstraints: X509BasicConstraints,
    val publicKey: Key,
    val criticalExtensionOIDs: Set<X509V3ExtensionOID>,
    val nonCriticalExtensionOIDs: Set<X509V3ExtensionOID>,
    private val certificate: X509CertificateHandle,
) {

    /**
     * Convert the decoded certificate into the specification's profile data shape.
     */
    fun toDocumentSignerCertificateProfileData() = DocumentSignerCertificateProfileData(
        principalName = principalName,
        validityPeriod = validityPeriod,
        crlDistributionPointUri = crlDistributionPointUri,
    )

    /**
     * Verify the certificate signature using the provided IACA public key.
     */
    internal suspend fun verifySignature(
        verificationKey: Key,
    ) {
        certificate.verifySignature(verificationKey)
    }

}
