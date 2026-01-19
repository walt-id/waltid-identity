@file:OptIn(ExperimentalTime::class)

package id.walt.x509.iso.documentsigner.validate

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import id.walt.x509.X509BasicConstraints
import id.walt.x509.X509KeyUsage
import id.walt.x509.X509V3ExtensionOID
import id.walt.x509.X509ValidityPeriod
import id.walt.x509.iso.DS_CERT_MAX_VALIDITY_SECONDS
import id.walt.x509.iso.DocumentSignerEkuOID
import id.walt.x509.iso.blockingBridge
import id.walt.x509.iso.documentsigner.certificate.DocumentSignerCertificateProfileData
import id.walt.x509.iso.documentsigner.certificate.DocumentSignerDecodedCertificate
import id.walt.x509.iso.documentsigner.certificate.DocumentSignerPrincipalName
import id.walt.x509.iso.iaca.certificate.IACACertificateProfileData
import id.walt.x509.iso.iaca.certificate.IACADecodedCertificate
import id.walt.x509.iso.isValidIsoCountryCode
import id.walt.x509.iso.validateSerialNo
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * ISO 18013-5 profile validator for decoded Document Signer X.509 certificates.
 *
 * Validations can be toggled using [DocumentSignerValidationConfig]. Failures
 * throw [IllegalArgumentException] with descriptive messages.
 */
class DocumentSignerValidator(
    val config: DocumentSignerValidationConfig = DocumentSignerValidationConfig(),
) {

    /**
     * Validate a decoded Document Signer X.509 certificate against that of its issuing IACA.
     *
     * The decoded IACA certificate is not validated in the context of this function.
     */
    suspend fun validate(
        dsDecodedCert: DocumentSignerDecodedCertificate,
        iacaDecodedCert: IACADecodedCertificate,
    ) {

        validateDocumentSignerPublicKey(dsDecodedCert.publicKey)

        val dsProfileData = dsDecodedCert.toDocumentSignerCertificateProfileData()
        validateDocumentSignerProfileData(
            data = dsProfileData,
        )

        if (config.profileDataAgainstIACAProfileData) {
            validateProfileDataAgainstIACAProfileData(
                dsProfileData = dsProfileData,
                iacaProfileData = iacaDecodedCert.toIACACertificateProfileData(),
            )
        }

        if (config.authorityKeyIdentifier)
            require(iacaDecodedCert.skiHex == dsDecodedCert.akiHex) {
                "Document signer certificate authority key identifier (hex): ${dsDecodedCert.akiHex} does not match " +
                        "IACA certificate subject key identifier (hex): ${iacaDecodedCert.skiHex}"
            }

        if (config.serialNo)
            validateSerialNo(dsDecodedCert.serialNumber)

        if (config.basicConstraints)
            validateX509BasicConstraints(dsDecodedCert.basicConstraints)

        if (config.requiredCriticalExtensionOIDs) {
            require(dsDecodedCert.criticalExtensionOIDs.containsAll(requiredCriticalOIDs)) {
                "Document signer certificate was not found to contain all required critical extension oids, " +
                        "missing oids are: ${requiredCriticalOIDs.minus(dsDecodedCert.criticalExtensionOIDs)}"
            }
        }

        if (config.requiredNonCriticalExtensionOIDs) {
            require(dsDecodedCert.nonCriticalExtensionOIDs.containsAll(requiredNonCriticalOIDs)) {
                "Document signer certificate was not found to contain all required non critical extension oids, " +
                        "missing oids are: ${requiredNonCriticalOIDs.minus(dsDecodedCert.nonCriticalExtensionOIDs)}"
            }
        }

        if (config.keyUsage)
            validateKeyUsage(dsDecodedCert.keyUsage)

        if (config.extendedKeyUsage)
            validateExtendedKeyUsage(dsDecodedCert.extendedKeyUsage)

        if (config.signature) {
            dsDecodedCert.verifySignature(iacaDecodedCert.publicKey)
        }

    }

    /**
     * Blocking variant of [validate].
     */
    fun validateBlocking(
        dsDecodedCert: DocumentSignerDecodedCertificate,
        iacaDecodedCert: IACADecodedCertificate,
    ) = blockingBridge {
        validate(
            dsDecodedCert = dsDecodedCert,
            iacaDecodedCert = iacaDecodedCert,
        )
    }

    /**
     * Validate the Document Signer public key prior to certificate creation.
     */
    internal fun validateDocumentSignerPublicKey(
        publicKey: Key,
    ) {

        require(!publicKey.hasPrivateKey) {
            "Document signer key must be a public key, but instead was found to have hasPrivateKey: ${publicKey.hasPrivateKey}"
        }

        if (config.keyType) {
            validateKeyType(publicKey.keyType)
        }

    }

    /**
     * Validate profile data prior to certificate creation.
     */
    internal fun validateDocumentSignerProfileData(
        data: DocumentSignerCertificateProfileData,
    ) {

        if (config.principalName) {
            validatePrincipalName(data.principalName)
        }

        if (config.validityPeriod) {
            validateValidityPeriod(data.validityPeriod)
        }

        if (config.crlDistributionPointUri) {
            require(data.crlDistributionPointUri.isNotBlank()) {
                "Document signer CRL distribution point uri must not be blank"
            }
        }
    }

    /**
     * Validate Document Signer profile data against the issuing IACA profile data.
     *
     * This enforces shared fields such as country and ensures the Document Signer
     * validity window is within the IACA validity window.
     */
    internal fun validateProfileDataAgainstIACAProfileData(
        dsProfileData: DocumentSignerCertificateProfileData,
        iacaProfileData: IACACertificateProfileData,
    ) {
        require(iacaProfileData.principalName.country == dsProfileData.principalName.country) {
            "IACA and Document Signer country names must be the same"
        }

        require(iacaProfileData.principalName.stateOrProvinceName == dsProfileData.principalName.stateOrProvinceName) {
            "IACA and Document Signer state/province names must be the same"
        }

        require(iacaProfileData.validityPeriod.notBefore <= dsProfileData.validityPeriod.notBefore) {
            "IACA certificate not before must be before the Document Signer's not before"
        }

        require(iacaProfileData.validityPeriod.notAfter >= dsProfileData.validityPeriod.notAfter) {
            "IACA certificate not after must be after the Document Signer's not after"
        }
    }

    private fun validateKeyType(
        keyType: KeyType,
    ) {

        require(allowedDocumentSignerKeyTypes.contains(keyType)) {
            "Document signer public key type must be one of ${allowedDocumentSignerKeyTypes}, but was found to be $keyType"
        }

    }

    private fun validatePrincipalName(
        principalName: DocumentSignerPrincipalName,
    ) {

        require(isValidIsoCountryCode(principalName.country)) {
            "Document signer certificate data invalid ISO 3166-1 country code: '${principalName.country}'. Must be a valid 2-letter uppercase code."
        }

        // Validations of optional string values
        require(principalName.stateOrProvinceName == null || principalName.stateOrProvinceName.isNotBlank()) {
            "Document signer certificate data stateOrProvinceName must not be blank if specified"
        }
        require(principalName.organizationName == null || principalName.organizationName.isNotBlank()) {
            "Document signer certificate data organizationName must not be blank if specified"
        }
        require(principalName.localityName == null || principalName.localityName.isNotBlank()) {
            "Document signer certificate data localityName must not be blank if specified"
        }

    }

    private fun validateValidityPeriod(
        validityPeriod: X509ValidityPeriod,
    ) {

        val timeNow = Clock.System.now()
        require(validityPeriod.notAfter > timeNow) {
            "Document signer certificate notAfter ${validityPeriod.notAfter} must be greater than the current time: $timeNow "
        }

        require(validityPeriod.notBefore < validityPeriod.notAfter) {
            "Document signer certificate data notBefore must be before (and not equal to) notAfter"
        }

        require(
            validityPeriod.notAfter.minus(validityPeriod.notBefore).inWholeSeconds <= DS_CERT_MAX_VALIDITY_SECONDS
        ) {
            "Document signer certificates should not have a validity that is larger than 457 days, " +
                    "notAfter: ${validityPeriod.notAfter}, notBefore: ${validityPeriod.notBefore} " +
                    "and difference in whole days is: ${validityPeriod.notAfter.minus(validityPeriod.notBefore).inWholeDays}"
        }

    }

    private fun validateX509BasicConstraints(
        basicConstraints: X509BasicConstraints
    ) {

        require(!basicConstraints.isCA) {
            "Document signer basic constraints isCA flag must be set to false, but was found to be true"
        }

    }

    private fun validateKeyUsage(
        keyUsage: Set<X509KeyUsage>
    ) {

        require(expectedKeyUsageSet == keyUsage) {
            "Document signer key usage must be equal to the set: ${expectedKeyUsageSet.joinToString()}, " +
                    "but instead is: ${keyUsage.joinToString()}"
        }
    }

    private fun validateExtendedKeyUsage(
        extKeyUsage: Set<String>,
    ) {

        require(extKeyUsage.contains(DocumentSignerEkuOID)) {
            "Document signer extended key usage must contain OID: ${DocumentSignerEkuOID}, but was found missing"
        }
    }

    companion object {
        private val allowedDocumentSignerKeyTypes = setOf(
            KeyType.secp256r1,
            KeyType.secp384r1,
            KeyType.secp521r1,
        )

        private val requiredCriticalOIDs = setOf(
            X509V3ExtensionOID.KeyUsage,
            X509V3ExtensionOID.ExtendedKeyUsage,
        )
        private val requiredNonCriticalOIDs = setOf(
            X509V3ExtensionOID.SubjectKeyIdentifier,
            X509V3ExtensionOID.AuthorityKeyIdentifier,
            X509V3ExtensionOID.IssuerAlternativeName,
            X509V3ExtensionOID.CrlDistributionPoints,
        )

        private val expectedKeyUsageSet = setOf(
            X509KeyUsage.DigitalSignature,
        )

    }

}
