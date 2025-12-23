@file:OptIn(ExperimentalTime::class)

package id.walt.x509.iso.iaca.certificate

import id.walt.x509.CertificateKeyUsage
import id.walt.x509.iso.CertificateValidityPeriod
import id.walt.x509.iso.IssuerAlternativeName
import okio.ByteString
import kotlin.time.ExperimentalTime

data class IACADecodedCertificate(
    val principalName: IACAPrincipalName,
    val validityPeriod: CertificateValidityPeriod,
    val issuerAlternativeName: IssuerAlternativeName,
    val serialNumber: ByteString, //
    //TODO: Add SKI stuff? And if so, in what format?
    val isCA: Boolean = true,//
    val pathLengthConstraint: Int = 0,//
    val keyUsage: Set<CertificateKeyUsage> = setOf(CertificateKeyUsage.KeyCertSign, CertificateKeyUsage.CRLSign),
    val crlDistributionPointUri: String? = null,
) {

    fun toIACACertificateProfileData() = IACACertificateProfileData(
        principalName = principalName,
        validityPeriod = validityPeriod,
        issuerAlternativeName = issuerAlternativeName,
        crlDistributionPointUri = crlDistributionPointUri,
    )
}
