@file:OptIn(ExperimentalTime::class)

package id.walt.x509.iso.iaca.validate

import id.walt.x509.iso.IACA_CERT_MAX_VALIDITY_SECONDS
import id.walt.x509.iso.iaca.certificate.IACACertificateProfileData
import id.walt.x509.iso.isValidIsoCountryCode
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class IACACertificateValidator {

    fun validateProfileData(
        data: IACACertificateProfileData,
    ) {
        require(isValidIsoCountryCode(data.country)) {
            "IACA certificate data invalid ISO 3166-1 country code: '${data.country}'. Must be a valid 2-letter uppercase code."
        }

        require(data.stateOrProvinceName == null || data.stateOrProvinceName.isNotBlank()) {
            "IACA certificate data stateOrProvinceName must not be blank if specified"
        }
        require(data.organizationName == null || data.organizationName.isNotBlank()) {
            "IACA certificate data organizationName must not be blank if specified"
        }

        val timeNow = Clock.System.now()
        require(data.notAfter > timeNow) {
            "IACA certificate notAfter $data.notAfter must be greater than the current time: $timeNow "
        }

        require(data.notBefore < data.notAfter) {
            "IACA certificate data notBefore must be before (and not equal to) notAfter"
        }

        require(data.notAfter.minus(data.notBefore).inWholeSeconds < IACA_CERT_MAX_VALIDITY_SECONDS) {
            "IACA certificates should not have a validity that is larger than 15 years" +
                    "notAfter: ${data.notAfter}, notBefore: ${data.notBefore} " +
                    "and difference in whole days is: ${data.notAfter.minus(data.notBefore).inWholeDays}"
        }
    }
}