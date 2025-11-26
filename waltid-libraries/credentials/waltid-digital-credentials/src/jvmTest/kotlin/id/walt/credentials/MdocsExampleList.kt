package id.walt.credentials

import id.walt.credentials.CredentialDetectorTypes.CredentialDetectionResult
import id.walt.credentials.CredentialDetectorTypes.CredentialPrimaryDataType
import id.walt.credentials.CredentialDetectorTypes.MdocsSubType
import id.walt.credentials.examples.MdocsExamples

object MdocsExampleList {

    val allMdocsExamples = listOf(
        MdocsExamples.mdocsExampleBase64Url to CredentialDetectionResult(
            CredentialPrimaryDataType.MDOCS,
            MdocsSubType.mdocs,
            CredentialDetectorTypes.SignaturePrimaryType.COSE,
            true, true
        ),
        MdocsExamples.mdocExampleHex to CredentialDetectionResult(
            CredentialPrimaryDataType.MDOCS,
            MdocsSubType.mdocs,
            CredentialDetectorTypes.SignaturePrimaryType.COSE,
            true, true
        ),
       /*MdocsExamples.mdocExampleMdocsLib to CredentialDetectionResult(
            CredentialPrimaryDataType.MDOCS,
            MdocsSubType.mdocs,
            CredentialDetectorTypes.SignaturePrimaryType.COSE,
           true, true
        )*/
    )
}
