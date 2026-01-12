@file:OptIn(ExperimentalTime::class)

package id.walt.x509.iso.iaca.certificate

import id.walt.x509.X509ValidityPeriod
import id.walt.x509.iso.IssuerAlternativeName
import kotlin.time.ExperimentalTime

/**
 * ISO profile input data used to build an IACA X.509 certificate.
 *
 * This is the minimal, platform-independent set of fields required by the
 * specification's profile.
 *
 * @param principalName X.500 subject & issuer name for the IACA X.509 certificate.
 * @param validityPeriod Validity window of the X.509 certificate.
 * @param issuerAlternativeName IssuerAlternativeName extension value.
 * @param crlDistributionPointUri Optional CRL distribution point URI.
 */
data class IACACertificateProfileData(
    val principalName: IACAPrincipalName,
    val validityPeriod: X509ValidityPeriod,
    val issuerAlternativeName: IssuerAlternativeName,
    val crlDistributionPointUri: String? = null,
)
