@file:OptIn(ExperimentalTime::class)

package id.walt.issuer.services.onboarding.models

import kotlinx.serialization.Serializable
import kotlin.time.*

@Serializable
data class IACACertificateData(
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
        get() = notAfter ?: finalNotBefore.plus((15 * 365L).toDuration(DurationUnit.DAYS))

    init {

        // Country code validation
        require(ValidationUtils.isValidISOCountryCode(country)) {
            "IACA certificate data invalid ISO 3166-1 country code: '$country'. Must be a valid 2-letter uppercase code."
        }

        // Optional string validations
        require(stateOrProvinceName == null || stateOrProvinceName.isNotBlank()) {
            "IACA certificate data stateOrProvinceName must not be blank if specified"
        }
        require(organizationName == null || organizationName.isNotBlank()) {
            "IACA certificate data organizationName must not be blank if specified"
        }

        notBefore?.let {
            require(it >= Clock.System.now()) {
                "IACA certificate data notBefore cannot be in the past"
            }
        }

        require(finalNotBefore < finalNotAfter) {
            "IACA certificate data notBefore must be before (and not equal to) notAfter"
        }

    }

}
