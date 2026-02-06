package id.walt.issuer.services.onboarding.models

import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class DocumentSignerCertificateData(
    val country: String,
    val commonName: String,
    val notBefore: Instant,
    val notAfter: Instant,
    val crlDistributionPointUri: String,
    val stateOrProvinceName: String? = null,
    val organizationName: String? = null,
    val localityName: String? = null,
)
