@file:OptIn(ExperimentalTime::class)

package id.walt.x509.iso.documentsigner.validate

import id.walt.x509.iso.DS_CERT_MAX_VALIDITY_SECONDS
import id.walt.x509.iso.documentsigner.certificate.DocumentSignerCertificateProfileData
import id.walt.x509.iso.iaca.certificate.IACACertificateProfileData
import id.walt.x509.iso.isValidIsoCountryCode
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class DocumentSignerCertificateValidator {

    fun validateProfileData(
        data: DocumentSignerCertificateProfileData,
    ) {

        require(isValidIsoCountryCode(data.principalName.country)) {
            "Document signer certificate data invalid ISO 3166-1 country code: '${data.principalName.country}'. Must be a valid 2-letter uppercase code."
        }

        // Validations of optional string values
        require(data.principalName.stateOrProvinceName == null || data.principalName.stateOrProvinceName.isNotBlank()) {
            "Document signer certificate data stateOrProvinceName must not be blank if specified"
        }
        require(data.principalName.organizationName == null || data.principalName.organizationName.isNotBlank()) {
            "Document signer certificate data organizationName must not be blank if specified"
        }
        require(data.principalName.localityName == null || data.principalName.localityName.isNotBlank()) {
            "Document signer certificate data localityName must not be blank if specified"
        }

        val validity = data.validityPeriod
        val timeNow = Clock.System.now()
        require(validity.notAfter > timeNow) {
            "Document signer certificate notAfter ${validity.notAfter} must be greater than the current time: $timeNow "
        }

        require(validity.notBefore < validity.notAfter) {
            "Document signer certificate data notBefore must be before (and not equal to) notAfter"
        }

        require(validity.notAfter.minus(validity.notBefore).inWholeSeconds <= DS_CERT_MAX_VALIDITY_SECONDS) {
            "Document signer certificates should not have a validity that is larger than 457 days" +
                    "notAfter: ${validity.notAfter}, notBefore: ${validity.notBefore} " +
                    "and difference in whole days is: ${validity.notAfter.minus(validity.notBefore).inWholeDays}"
        }
    }

    fun validateProfileDataAgainstIACAProfileData(
        dsData: DocumentSignerCertificateProfileData,
        iacaData: IACACertificateProfileData,
    ) {
        require(iacaData.principalName.country == dsData.principalName.country) {
            "IACA and document signer country names must be the same"
        }

        require(iacaData.principalName.stateOrProvinceName == dsData.principalName.stateOrProvinceName) {
            "IACA and document signer state/province names must be the same"
        }

        require(iacaData.validityPeriod.notBefore <= dsData.validityPeriod.notBefore) {
            "IACA certificate not before must be before the document signer's not before"
        }

        require(iacaData.validityPeriod.notAfter >= dsData.validityPeriod.notAfter) {
            "IACA certificate not after must be after the document signer's not after"
        }
    }

}
