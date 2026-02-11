@file:OptIn(ExperimentalTime::class)

package id.walt.x509.iso.iaca.validate

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import id.walt.x509.X509BasicConstraints
import id.walt.x509.X509KeyUsage
import id.walt.x509.X509V3ExtensionOID
import id.walt.x509.X509ValidityPeriod
import id.walt.x509.iso.IACA_CERT_MAX_VALIDITY_SECONDS
import id.walt.x509.iso.IssuerAlternativeName
import id.walt.x509.iso.blockingBridge
import id.walt.x509.iso.iaca.certificate.IACACertificateProfileData
import id.walt.x509.iso.iaca.certificate.IACADecodedCertificate
import id.walt.x509.iso.iaca.certificate.IACAPrincipalName
import id.walt.x509.iso.isValidIsoCountryCode
import id.walt.x509.iso.validateSerialNo
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * ISO 18013-5 profile validator for decoded IACA X.509 certificates.
 *
 * Validations can be toggled using [IACAValidationConfig]. Failures
 * throw [IllegalArgumentException] with descriptive messages.
 */
class IACAValidator(
    val config: IACAValidationConfig = IACAValidationConfig(),
) {

    /**
     * Validate a decoded IACA X.509 certificate.
     */
    suspend fun validate(
        decodedCert: IACADecodedCertificate,
    ) {

        if (config.keyType) {
            validateKeyType(decodedCert.publicKey.keyType)
        }

        validateCertificateProfileData(decodedCert.toIACACertificateProfileData())

        if (config.serialNo)
            validateSerialNo(decodedCert.serialNumber)

        if (config.basicConstraints)
            validateX509BasicConstraints(decodedCert.basicConstraints)

        if (config.requiredCriticalExtensionOIDs) {
            require(decodedCert.criticalExtensionOIDs.containsAll(requiredCriticalOIDs)) {
                "IACA certificate was not found to contain all required critical extension oids, " +
                        "missing oids are: ${requiredCriticalOIDs.minus(decodedCert.criticalExtensionOIDs)}"
            }
        }

        if (config.requiredNonCriticalExtensionOIDs) {
            require(decodedCert.nonCriticalExtensionOIDs.containsAll(requiredNonCriticalOIDs)) {
                "IACA certificate was not found to contain all required non critical extension oids, " +
                        "missing oids are: ${requiredNonCriticalOIDs.minus(decodedCert.nonCriticalExtensionOIDs)}"
            }
        }

        if (config.keyUsage)
            validateKeyUsage(decodedCert.keyUsage)

        if (config.signature) {
            decodedCert.verifySignature(decodedCert.publicKey)
        }

    }

    /**
     * Blocking variant of [validate].
     */
    fun validateBlocking(
        decodedCert: IACADecodedCertificate,
    ) = blockingBridge {
        validate(decodedCert)
    }

    /**
     * Validate a signing key intended for building an IACA certificate.
     */
    internal fun validateSigningKey(
        signingKey: Key,
    ) {

        require(signingKey.hasPrivateKey) {
            "IACA signing key must have a private key, but was found to have hasPrivateKey: ${signingKey.hasPrivateKey}"
        }

        if (config.keyType) {
            validateKeyType(signingKey.keyType)
        }

    }

    /**
     * Validate profile data prior to certificate creation.
     */
    internal fun validateCertificateProfileData(
        data: IACACertificateProfileData,
    ) {

        if (config.principalName) {
            validatePrincipalName(data.principalName)
        }

        if (config.issuerAlternativeName) {
            validateIssuerAlternativeName(data.issuerAlternativeName)
        }

        if (config.validityPeriod) {
            validateCertificateValidityPeriod(data.validityPeriod)
        }

        if (config.crlDistributionPointUri) {
            data.crlDistributionPointUri?.let {
                require(it.isNotBlank()) {
                    "IACA CRL distribution point, when optionally specified, must not be blank."
                }
            }
        }

    }

    private fun validateKeyType(
        keyType: KeyType,
    ) {

        require(allowedSigningKeyTypes.contains(keyType)) {
            "IACA signing key type must be one of ${allowedSigningKeyTypes}, but was found to be $keyType"
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
        validityPeriod: X509ValidityPeriod,
    ) {

        val timeNow = Clock.System.now()
        require(validityPeriod.notAfter > timeNow) {
            "IACA certificate notAfter ${validityPeriod.notAfter} must be greater than the current time: $timeNow "
        }

        require(validityPeriod.notBefore < validityPeriod.notAfter) {
            "IACA certificate data notBefore must be before (and not equal to) notAfter"
        }

        require(
            validityPeriod.notAfter.minus(validityPeriod.notBefore).inWholeSeconds <= IACA_CERT_MAX_VALIDITY_SECONDS
        ) {
            "IACA certificates should not have a validity that is larger than 20 years, " +
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

    private fun validateX509BasicConstraints(
        basicConstraints: X509BasicConstraints
    ) {

        require(basicConstraints.isCA) {
            "IACA basic constraints isCA flag must be set to true, but was found to be false"
        }

        require(basicConstraints.pathLengthConstraint == 0) {
            "IACA basic constraints pathLengthConstraint must be 0, but was found to be ${basicConstraints.pathLengthConstraint}"
        }
    }

    private fun validateKeyUsage(
        keyUsage: Set<X509KeyUsage>
    ) {

        require(expectedKeyUsageSet == keyUsage) {
            "IACA key usage must be equal to the set: ${expectedKeyUsageSet.joinToString()}, but instead is: ${keyUsage.joinToString()}"
        }
    }

    companion object {

        private val allowedSigningKeyTypes = setOf(
            KeyType.secp256r1,
            KeyType.secp384r1,
            KeyType.secp521r1,
        )

        private val requiredCriticalOIDs = setOf(
            X509V3ExtensionOID.BasicConstraints,
            X509V3ExtensionOID.KeyUsage,
        )
        private val requiredNonCriticalOIDs = setOf(
            X509V3ExtensionOID.SubjectKeyIdentifier,
            X509V3ExtensionOID.IssuerAlternativeName,
        )

        private val expectedKeyUsageSet = setOf(
            X509KeyUsage.KeyCertSign,
            X509KeyUsage.CRLSign,
        )

    }
}
