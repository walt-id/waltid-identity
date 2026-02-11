package id.walt.issuer.services.onboarding.models

import id.walt.x509.X509ValidityPeriod
import id.walt.x509.iso.IssuerAlternativeName
import id.walt.x509.iso.iaca.certificate.IACACertificateProfileData
import id.walt.x509.iso.iaca.certificate.IACAPrincipalName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class IACACertificateData(
    val country: String,
    val commonName: String,
    val notBefore: Instant,
    val notAfter: Instant,
    val issuerAlternativeNameConf: IssuerAlternativeNameConfiguration,
    val stateOrProvinceName: String? = null,
    val organizationName: String? = null,
    val crlDistributionPointUri: String? = null,
) {

    fun toIACACertificateProfileData() = IACACertificateProfileData(
        principalName = IACAPrincipalName(
            country = country,
            commonName = commonName,
            stateOrProvinceName = stateOrProvinceName,
            organizationName = organizationName,
        ),
        validityPeriod = X509ValidityPeriod(
            notBefore = notBefore,
            notAfter = notAfter,
        ),
        issuerAlternativeName = IssuerAlternativeName(
            uri = issuerAlternativeNameConf.uri,
            email = issuerAlternativeNameConf.email,
        ),
        crlDistributionPointUri = crlDistributionPointUri,
    )

}
