package id.walt.issuer.services.onboarding.models

import id.walt.certificate.x509.profile.IsoDocumentSignerX509CertificateProfile
import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.Instant

@Serializable
data class DocumentSignerCertificateRequestData(
    val country: String,
    val commonName: String,
    val crlDistributionPointUri: String,
    val stateOrProvinceName: String? = null,
    val organizationName: String? = null,
    val localityName: String? = null,
    val notBefore: Instant? = null,
    val notAfter: Instant? = null,
    val issuerEmailAddress: String? = null,
    val issuerUri: String? = null,
) {

    val finalNotBefore: Instant
        get() = notBefore ?: Clock.System.now()

    val finalNotAfter: Instant
        get() = notAfter ?: finalNotBefore.plus(IsoDocumentSignerX509CertificateProfile.maxValidityTime)

}
