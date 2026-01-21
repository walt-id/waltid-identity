@file:OptIn(ExperimentalTime::class)

package id.walt.issuer.services.onboarding.models

import id.walt.x509.X509ValidityPeriod
import id.walt.x509.iso.IACA_CERT_MAX_VALIDITY_SECONDS
import id.walt.x509.iso.IssuerAlternativeName
import id.walt.x509.iso.iaca.certificate.IACACertificateProfileData
import id.walt.x509.iso.iaca.certificate.IACAPrincipalName
import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@Serializable
data class IACACertificateRequestData(
    val country: String,
    val commonName: String,
    val issuerAlternativeNameConf: IssuerAlternativeNameConfiguration,
    val stateOrProvinceName: String? = null,
    val organizationName: String? = null,
    val notBefore: Instant? = null,
    val notAfter: Instant? = null,
    val crlDistributionPointUri: String? = null,
) {

    val finalNotBefore: Instant
        get() = notBefore ?: Clock.System.now()

    val finalNotAfter: Instant
        get() = notAfter ?: finalNotBefore.plus(IACA_CERT_MAX_VALIDITY_SECONDS.seconds)

    fun toIACACertificateProfileData() = IACACertificateProfileData(
        principalName = IACAPrincipalName(
            country = country,
            commonName = commonName,
            stateOrProvinceName = stateOrProvinceName,
            organizationName = organizationName,
        ),
        validityPeriod = X509ValidityPeriod(
            notAfter = finalNotAfter,
            notBefore = finalNotBefore,
        ),
        issuerAlternativeName = IssuerAlternativeName(
            uri = issuerAlternativeNameConf.uri,
            email = issuerAlternativeNameConf.email,
        ),
        crlDistributionPointUri = crlDistributionPointUri,
    )

}
