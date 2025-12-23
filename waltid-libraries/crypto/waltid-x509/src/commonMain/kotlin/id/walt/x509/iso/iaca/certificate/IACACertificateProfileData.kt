@file:OptIn(ExperimentalTime::class)

package id.walt.x509.iso.iaca.certificate

import id.walt.x509.iso.CertificateValidityPeriod
import id.walt.x509.iso.IssuerAlternativeName
import kotlin.time.ExperimentalTime

data class IACACertificateProfileData(
    val principalName: IACAPrincipalName,
    val validityPeriod: CertificateValidityPeriod,
    val issuerAlternativeName: IssuerAlternativeName,
    val crlDistributionPointUri: String? = null,
)
