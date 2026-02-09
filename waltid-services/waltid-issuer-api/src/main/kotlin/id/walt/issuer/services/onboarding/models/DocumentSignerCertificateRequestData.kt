@file:OptIn(ExperimentalTime::class)

package id.walt.issuer.services.onboarding.models

import id.walt.x509.X509ValidityPeriod
import id.walt.x509.iso.DS_CERT_MAX_VALIDITY_SECONDS
import id.walt.x509.iso.documentsigner.certificate.DocumentSignerCertificateProfileData
import id.walt.x509.iso.documentsigner.certificate.DocumentSignerPrincipalName
import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
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
) {

    val finalNotBefore: Instant
        get() = notBefore ?: Clock.System.now()

    val finalNotAfter: Instant
        get() = notAfter ?: finalNotBefore.plus(DS_CERT_MAX_VALIDITY_SECONDS.seconds)

    fun toDocumentSignerCertificateProfileData() = DocumentSignerCertificateProfileData(
        principalName = DocumentSignerPrincipalName(
            country = country,
            commonName = commonName,
            stateOrProvinceName = stateOrProvinceName,
            organizationName = organizationName,
            localityName = localityName,
        ),
        validityPeriod = X509ValidityPeriod(
            notBefore = finalNotBefore,
            notAfter = finalNotAfter,
        ),
        crlDistributionPointUri = crlDistributionPointUri,
    )

}
