package id.walt.issuer.services.onboarding.models

import id.walt.certificate.x509.profile.IsoIaCaRootX509CertificateProfile
import kotlinx.serialization.Serializable
import kotlin.time.Clock
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
        get() = notAfter ?: finalNotBefore.plus(IsoIaCaRootX509CertificateProfile.maxValidityTime)

}
