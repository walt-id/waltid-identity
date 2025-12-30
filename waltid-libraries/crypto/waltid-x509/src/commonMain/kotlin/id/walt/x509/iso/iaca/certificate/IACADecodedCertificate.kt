@file:OptIn(ExperimentalTime::class)

package id.walt.x509.iso.iaca.certificate

import id.walt.crypto.keys.Key
import id.walt.x509.*
import id.walt.x509.iso.IssuerAlternativeName
import okio.ByteString
import kotlin.time.ExperimentalTime

/**
 * Decoded view (not validated) of an IACA X.509 certificate.
 *
 * Clients should validate instances via the [id.walt.x509.iso.iaca.validate.IACAValidator]
 * class
 *
 * Instances are created by platform parsers and expose both the parsed ISO
 * profile data, among other relevant data points necessary for, subsequent, validations.
 */
@ConsistentCopyVisibility
data class IACADecodedCertificate internal constructor(
    val principalName: IACAPrincipalName,
    val validityPeriod: X509ValidityPeriod,
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

    /**
     * Convert the decoded certificate into the specification's profile data shape.
     */
    fun toIACACertificateProfileData() = IACACertificateProfileData(
        principalName = principalName,
        validityPeriod = validityPeriod,
        issuerAlternativeName = issuerAlternativeName,
        crlDistributionPointUri = crlDistributionPointUri,
    )

    /**
     * Verify the certificate signature.
     *
     * For IACA certificates this is typically verified with its own public key,
     * because IACA certificates are self-signed.
     */
    internal suspend fun verifySignature(
        verificationKey: Key,
    ) {
        certificate.verifySignature(verificationKey)
    }

}
