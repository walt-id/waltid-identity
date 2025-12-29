@file:OptIn(ExperimentalTime::class)

package id.walt.x509.iso.iaca.certificate

import id.walt.crypto.keys.Key
import id.walt.x509.X509BasicConstraints
import id.walt.x509.X509KeyUsage
import id.walt.x509.X509CertificateHandle
import id.walt.x509.X509V3ExtensionOID
import id.walt.x509.iso.CertificateValidityPeriod
import id.walt.x509.iso.IssuerAlternativeName
import okio.ByteString
import kotlin.time.ExperimentalTime

@ConsistentCopyVisibility
data class IACADecodedCertificate internal constructor(
    val principalName: IACAPrincipalName,
    val validityPeriod: CertificateValidityPeriod,
    val issuerAlternativeName: IssuerAlternativeName,
    val publicKey: Key,
    val serialNumber: ByteString,
    val basicConstraints: X509BasicConstraints,
    val keyUsage: Set<X509KeyUsage>,
    val skiHex: String,
    val criticalExtensionOIDs: Set<X509V3ExtensionOID>,
    val nonCriticalExtensionOIDs: Set<X509V3ExtensionOID>,
    val crlDistributionPointUri: String? = null,
    private val certificate: X509CertificateHandle,
) {

    fun toIACACertificateProfileData() = IACACertificateProfileData(
        principalName = principalName,
        validityPeriod = validityPeriod,
        issuerAlternativeName = issuerAlternativeName,
        crlDistributionPointUri = crlDistributionPointUri,
    )

    internal suspend fun verifySignature(
        verificationKey: Key,
    ) {
        certificate.verifySignature(verificationKey)
    }

}
