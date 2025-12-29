@file:OptIn(ExperimentalTime::class)

package id.walt.x509.iso.documentsigner.validate

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import id.walt.x509.X509V3ExtensionOID
import id.walt.x509.iso.CertificateValidityPeriod
import id.walt.x509.iso.DS_CERT_MAX_VALIDITY_SECONDS
import id.walt.x509.iso.documentsigner.certificate.DocumentSignerCertificateProfileData
import id.walt.x509.iso.documentsigner.certificate.DocumentSignerDecodedCertificate
import id.walt.x509.iso.documentsigner.certificate.DocumentSignerPrincipalName
import id.walt.x509.iso.iaca.certificate.IACACertificateProfileData
import id.walt.x509.iso.iaca.certificate.IACADecodedCertificate
import id.walt.x509.iso.isValidIsoCountryCode
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class DocumentSignerValidator(
    val config: DocumentSignerValidationConfig = DocumentSignerValidationConfig(),
) {

    //TODO: check if all validations are correct
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

        if (config.signature) {
            dsDecodedCert.verifySignature(iacaDecodedCert.publicKey)
        }

    }

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

    internal fun validateProfileDataAgainstIACAProfileData(
        dsProfileData: DocumentSignerCertificateProfileData,
        iacaProfileData: IACACertificateProfileData,
    ) {
        require(iacaProfileData.principalName.country == dsProfileData.principalName.country) {
            "IACA and document signer country names must be the same"
        }

        require(iacaProfileData.principalName.stateOrProvinceName == dsProfileData.principalName.stateOrProvinceName) {
            "IACA and document signer state/province names must be the same"
        }

        require(iacaProfileData.validityPeriod.notBefore <= dsProfileData.validityPeriod.notBefore) {
            "IACA certificate not before must be before the document signer's not before"
        }

        require(iacaProfileData.validityPeriod.notAfter >= dsProfileData.validityPeriod.notAfter) {
            "IACA certificate not after must be after the document signer's not after"
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
        validityPeriod: CertificateValidityPeriod,
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
    }

}
