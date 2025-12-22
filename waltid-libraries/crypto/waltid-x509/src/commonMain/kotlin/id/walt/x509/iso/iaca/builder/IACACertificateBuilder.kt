@file:OptIn(ExperimentalTime::class)

package id.walt.x509.iso.iaca.builder

import id.walt.crypto.keys.Key
import id.walt.x509.iso.CertificateValidityPeriod
import id.walt.x509.iso.IssuerAlternativeName
import id.walt.x509.iso.iaca.certificate.IACACertificateBundle
import kotlin.time.ExperimentalTime

//TODO: Use data class for profile data
class IACACertificateBuilder(
    val country: String,
    val commonName: String,
    val validityPeriod: CertificateValidityPeriod,
    val issuerAlternativeName: IssuerAlternativeName,
    val signingKey: Key,
) {

    val notBefore get() = validityPeriod.notBefore
    val notAfter get() = validityPeriod.notAfter

    var stateOrProvinceName: String? = null
    var organizationName: String? = null
    var crlDistributionPointUri: String? = null

    //TODO: Add call to validator before calling platform sign function
    suspend fun build() = platformSignIACACertificate(
        country = country,
        commonName = commonName,
        validityPeriod = validityPeriod,
        issuerAlternativeName = issuerAlternativeName,
        signingKey = signingKey,
        stateOrProvinceName = stateOrProvinceName,
        organizationName = organizationName,
        crlDistributionPointUri = crlDistributionPointUri,
    )

}

internal expect suspend fun platformSignIACACertificate(
    country: String,
    commonName: String,
    validityPeriod: CertificateValidityPeriod,
    issuerAlternativeName: IssuerAlternativeName,
    signingKey: Key,
    stateOrProvinceName: String? = null,
    organizationName: String? = null,
    crlDistributionPointUri: String? = null,
): IACACertificateBundle
