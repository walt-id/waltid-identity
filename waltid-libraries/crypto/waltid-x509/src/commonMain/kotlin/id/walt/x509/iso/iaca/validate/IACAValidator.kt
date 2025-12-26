@file:OptIn(ExperimentalTime::class)

package id.walt.x509.iso.iaca.validate

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import id.walt.x509.iso.CertificateValidityPeriod
import id.walt.x509.iso.IACA_CERT_MAX_VALIDITY_SECONDS
import id.walt.x509.iso.IssuerAlternativeName
import id.walt.x509.iso.iaca.certificate.IACACertificateProfileData
import id.walt.x509.iso.iaca.certificate.IACADecodedCertificate
import id.walt.x509.iso.iaca.certificate.IACAPrincipalName
import id.walt.x509.iso.isValidIsoCountryCode
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

internal class IACAValidator {

    fun validateDecodedCertificate(
        decodedCert: IACADecodedCertificate,
    ) {

    }

    fun validateSigningKey(
        signingKey: Key,
    ) {

        require(signingKey.hasPrivateKey) {
            "IACA signing key must have a private key."
        }

        require(allowedSigningKeyTypes.contains(signingKey.keyType)) {
            "IACA signing key type must be one of ${allowedSigningKeyTypes}, but was found to be ${signingKey.keyType}"
        }

    }

    fun validateCertificateProfileData(
        data: IACACertificateProfileData,
    ) {

        validatePrincipalName(data.principalName)

        validateIssuerAlternativeName(data.issuerAlternativeName)

        validateCertificateValidityPeriod(data.validityPeriod)

        data.crlDistributionPointUri?.let {
            require(it.isNotBlank()) {
                "IACA CRL distribution point, when optionally specified, must not be blank."
            }
        }

    }

    private fun validatePrincipalName(
        principalName: IACAPrincipalName,
    ) {

        require(isValidIsoCountryCode(principalName.country)) {
            "IACA certificate data invalid ISO 3166-1 country code: '${principalName.country}'. Must be a valid 2-letter uppercase code."
        }

        require(principalName.stateOrProvinceName == null || principalName.stateOrProvinceName.isNotBlank()) {
            "IACA certificate data stateOrProvinceName must not be blank if specified"
        }
        require(principalName.organizationName == null || principalName.organizationName.isNotBlank()) {
            "IACA certificate data organizationName must not be blank if specified"
        }

    }

    private fun validateCertificateValidityPeriod(
        validityPeriod: CertificateValidityPeriod,
    ) {

        val timeNow = Clock.System.now()
        require(validityPeriod.notAfter > timeNow) {
            "IACA certificate notAfter ${validityPeriod.notAfter} must be greater than the current time: $timeNow "
        }

        require(validityPeriod.notBefore < validityPeriod.notAfter) {
            "IACA certificate data notBefore must be before (and not equal to) notAfter"
        }

        require(validityPeriod.notAfter.minus(validityPeriod.notBefore).inWholeSeconds < IACA_CERT_MAX_VALIDITY_SECONDS) {
            "IACA certificates should not have a validity that is larger than 15 years" +
                    "notAfter: ${validityPeriod.notAfter}, notBefore: ${validityPeriod.notBefore} " +
                    "and difference in whole days is: ${validityPeriod.notAfter.minus(validityPeriod.notBefore).inWholeDays}"
        }

    }

    private fun validateIssuerAlternativeName(
        issAltName: IssuerAlternativeName,
    ) {
        require(!issAltName.email.isNullOrBlank() || !issAltName.uri.isNullOrBlank()) {
            "IACA issuer alternative name must have at least one of 'email' or 'uri' specified with a non-null, or blank value"
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
