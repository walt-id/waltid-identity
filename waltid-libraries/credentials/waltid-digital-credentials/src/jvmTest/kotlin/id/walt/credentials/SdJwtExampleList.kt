package id.walt.credentials

import id.walt.credentials.CredentialDetectorTypes.CredentialDetectionResult
import id.walt.credentials.CredentialDetectorTypes.CredentialPrimaryDataType
import id.walt.credentials.CredentialDetectorTypes.SDJWTVCSubType
import id.walt.credentials.examples.SdJwtExamples

object SdJwtExampleList {

    val allSdJwtVcCredentialExamples = listOf(
        SdJwtExamples.unsignedSdJwtVcNoDisclosablesExample to CredentialDetectionResult(
            CredentialPrimaryDataType.SDJWTVC,
            SDJWTVCSubType.sdjwtvc,
            CredentialDetectorTypes.SignaturePrimaryType.UNSIGNED,
        ),
        SdJwtExamples.sdJwtVcWithDisclosablesExample to CredentialDetectionResult(
            CredentialPrimaryDataType.SDJWTVC,
            SDJWTVCSubType.sdjwtvc,
            CredentialDetectorTypes.SignaturePrimaryType.UNSIGNED,
            containsDisclosables = true
        ),
        SdJwtExamples.sdJwtVcDmExample to CredentialDetectionResult(
            CredentialPrimaryDataType.SDJWTVC,
            SDJWTVCSubType.sdjwtvcdm,
            CredentialDetectorTypes.SignaturePrimaryType.UNSIGNED
        ),
        /* SdJwtExamples.sdJwtVcSignedExample to CredentialDetectionResult(
            CredentialPrimaryDataType.SDJWTVC,
            SDJWTVCSubType.sdjwtvc,
            CredentialDetectorTypes.SignaturePrimaryType.SDJWT,
            containsDisclosables = true,
            providesDisclosures = true
        ),*/
        SdJwtExamples.sdJwtVcSignedExample2 to CredentialDetectionResult(
            CredentialPrimaryDataType.SDJWTVC,
            SDJWTVCSubType.sdjwtvc,
            CredentialDetectorTypes.SignaturePrimaryType.SDJWT,
            containsDisclosables = true,
            providesDisclosures = true
        ),
        SdJwtExamples.sdJwtVcSignedExample2WithoutProvidedDisclosures to CredentialDetectionResult(
            CredentialPrimaryDataType.SDJWTVC,
            SDJWTVCSubType.sdjwtvc,
            CredentialDetectorTypes.SignaturePrimaryType.JWT,
            containsDisclosables = true
        )
    )

}
