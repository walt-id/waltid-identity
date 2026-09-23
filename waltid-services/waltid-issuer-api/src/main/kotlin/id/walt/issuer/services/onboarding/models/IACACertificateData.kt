package id.walt.issuer.services.onboarding.models

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
)
