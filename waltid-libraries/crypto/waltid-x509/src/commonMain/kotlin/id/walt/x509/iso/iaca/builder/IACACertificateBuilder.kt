@file:OptIn(ExperimentalTime::class)

package id.walt.x509.iso.iaca.builder

import id.walt.crypto.keys.Key
import id.walt.x509.iso.CertificateValidityPeriod
import id.walt.x509.iso.IssuerAlternativeName
import id.walt.x509.iso.iaca.certificate.IACACertificateBundle
import id.walt.x509.iso.iaca.certificate.IACAPrincipalName
import kotlin.time.ExperimentalTime

//TODO: Use data class for profile data
class IACACertificateBuilder(
    val principalName: IACAPrincipalName,
    val validityPeriod: CertificateValidityPeriod,
    val issuerAlternativeName: IssuerAlternativeName,
    val signingKey: Key,
) {

    var crlDistributionPointUri: String? = null

    //TODO: Add call to validator before calling platform sign function
    suspend fun build() = platformSignIACACertificate(
        principalName = principalName,
        validityPeriod = validityPeriod,
        issuerAlternativeName = issuerAlternativeName,
        signingKey = signingKey,
        crlDistributionPointUri = crlDistributionPointUri,
    )

}

internal expect suspend fun platformSignIACACertificate(
    principalName: IACAPrincipalName,
    validityPeriod: CertificateValidityPeriod,
    issuerAlternativeName: IssuerAlternativeName,
    signingKey: Key,
    crlDistributionPointUri: String? = null,
): IACACertificateBundle
