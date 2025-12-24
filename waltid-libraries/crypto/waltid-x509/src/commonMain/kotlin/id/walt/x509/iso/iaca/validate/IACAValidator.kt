@file:OptIn(ExperimentalTime::class)

package id.walt.x509.iso.iaca.validate

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import id.walt.x509.iso.IACA_CERT_MAX_VALIDITY_SECONDS
import id.walt.x509.iso.iaca.certificate.IACACertificateProfileData
import id.walt.x509.iso.isValidIsoCountryCode
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

internal class IACAValidator {

    fun validateCertificateBuilderInputs(
        data: IACACertificateProfileData,
        signingKey: Key,
    ) {
        require(signingKey.hasPrivateKey) {
            "IACA signing key must have a private key."
        }

        require(allowedSigningKeyTypes.contains(signingKey.keyType)) {
            "IACA signing key type must be one of ${allowedSigningKeyTypes}, but was found to be ${signingKey.keyType}"
        }

        require(isValidIsoCountryCode(data.principalName.country)) {
            "IACA certificate data invalid ISO 3166-1 country code: '${data.principalName.country}'. Must be a valid 2-letter uppercase code."
        }

        require(data.principalName.stateOrProvinceName == null || data.principalName.stateOrProvinceName.isNotBlank()) {
            "IACA certificate data stateOrProvinceName must not be blank if specified"
        }
        require(data.principalName.organizationName == null || data.principalName.organizationName.isNotBlank()) {
            "IACA certificate data organizationName must not be blank if specified"
        }

        val timeNow = Clock.System.now()
        require(data.validityPeriod.notAfter > timeNow) {
            "IACA certificate notAfter ${data.validityPeriod.notAfter} must be greater than the current time: $timeNow "
        }

        require(data.validityPeriod.notBefore < data.validityPeriod.notAfter) {
            "IACA certificate data notBefore must be before (and not equal to) notAfter"
        }

        require(data.validityPeriod.notAfter.minus(data.validityPeriod.notBefore).inWholeSeconds < IACA_CERT_MAX_VALIDITY_SECONDS) {
            "IACA certificates should not have a validity that is larger than 15 years" +
                    "notAfter: ${data.validityPeriod.notAfter}, notBefore: ${data.validityPeriod.notBefore} " +
                    "and difference in whole days is: ${data.validityPeriod.notAfter.minus(data.validityPeriod.notBefore).inWholeDays}"
        }
    }

    companion object {

        private val allowedSigningKeyTypes = setOf(
            KeyType.secp256r1,
            KeyType.secp384r1,
            KeyType.secp521r1,
        )
    }
}
