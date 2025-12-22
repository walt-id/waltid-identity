@file:OptIn(ExperimentalTime::class)

package id.walt.x509.iso.iaca.certificate

import id.walt.x509.iso.IssuerAlternativeName
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

data class IACACertificateProfileData(
    val country: String,
    val commonName: String,
    val notBefore: Instant,
    val notAfter: Instant,
    val issuerAlternativeName: IssuerAlternativeName,
    val stateOrProvinceName: String? = null,
    val organizationName: String? = null,
    val crlDistributionPointUri: String? = null,
)
