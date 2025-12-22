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

        require(isValidIsoCountryCode(data.country)) {
            "Document signer certificate data invalid ISO 3166-1 country code: '${data.country}'. Must be a valid 2-letter uppercase code."
        }

        // Validations of optional string values
        require(data.stateOrProvinceName == null || data.stateOrProvinceName.isNotBlank()) {
            "Document signer certificate data stateOrProvinceName must not be blank if specified"
        }
        require(data.organizationName == null || data.organizationName.isNotBlank()) {
            "Document signer certificate data organizationName must not be blank if specified"
        }

        val timeNow = Clock.System.now()
        require(data.notAfter > timeNow) {
            "Document signer certificate notAfter ${data.notAfter} must be greater than the current time: $timeNow "
        }

        require(data.notBefore < data.notAfter) {
            "Document signer certificate data notBefore must be before (and not equal to) notAfter"
        }

        require(data.notAfter.minus(data.notBefore).inWholeSeconds <= DS_CERT_MAX_VALIDITY_SECONDS) {
            "Document signer certificates should not have a validity that is larger than 457 days" +
                    "notAfter: ${data.notAfter}, notBefore: ${data.notBefore} " +
                    "and difference in whole days is: ${data.notAfter.minus(data.notBefore).inWholeDays}"
        }
    }

    fun validateProfileDataAgainstIACAProfileData(
        dsData: DocumentSignerCertificateProfileData,
        iacaData: IACACertificateProfileData,
    ) {
        require(iacaData.country == dsData.country) {
            "IACA and document signer country names must be the same"
        }

        require(iacaData.stateOrProvinceName == dsData.stateOrProvinceName) {
            "IACA and document signer state/province names must be the same"
        }

        require(iacaData.notBefore <= dsData.notBefore) {
            "IACA certificate not before must be before the document signer's not before"
        }

        require(iacaData.notAfter >= dsData.notAfter) {
            "IACA certificate not after must be after the document signer's not after"
        }
    }

}