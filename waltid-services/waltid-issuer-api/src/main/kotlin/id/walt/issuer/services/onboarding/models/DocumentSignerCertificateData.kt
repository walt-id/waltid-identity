@file:OptIn(ExperimentalTime::class)

package id.walt.issuer.services.onboarding.models

import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.serialization.Serializable
import kotlin.time.DurationUnit
import kotlin.time.ExperimentalTime
import kotlin.time.toDuration

@Serializable
data class DocumentSignerCertificateData(
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
        get() = notAfter ?: finalNotBefore.plus((457L).toDuration(DurationUnit.DAYS))

    init {

        // Country code validation
        require(ValidationUtils.isValidISOCountryCode(country)) {
            "Document signer certificate data invalid ISO 3166-1 country code: '$country'. Must be a valid 2-letter uppercase code."
        }

        // Optional string validations
        require(stateOrProvinceName == null || stateOrProvinceName.isNotBlank()) {
            "Document signer certificate data stateOrProvinceName must not be blank if specified"
        }
        require(organizationName == null || organizationName.isNotBlank()) {
            "Document signer certificate data organizationName must not be blank if specified"
        }

        notBefore?.let {
            require(it >= Clock.System.now()) {
                "Document signer certificate data notBefore cannot be in the past"
            }
        }

        require(finalNotBefore < finalNotAfter) {
            "Document signer certificate data notBefore must be before (and not equal to) notAfter"
        }

        require( finalNotAfter.minus(finalNotBefore) <= (457L).toDuration(DurationUnit.DAYS)) {
            "Document signer certificates should not have a validity that is larger than 457 days"
        }

    }

}
