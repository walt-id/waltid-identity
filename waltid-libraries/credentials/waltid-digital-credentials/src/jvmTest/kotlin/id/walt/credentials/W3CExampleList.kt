package id.walt.credentials

import id.walt.credentials.CredentialDetectorTypes.CredentialDetectionResult
import id.walt.credentials.CredentialDetectorTypes.CredentialPrimaryDataType
import id.walt.credentials.CredentialDetectorTypes.W3CSubType
import id.walt.credentials.examples.W3CExamples

object W3CExampleList {

    val allW3CCredentialExamples = listOf(
        W3CExamples.w3cCredential to W3CExamples.w3cCredentialValues to CredentialDetectionResult(
            CredentialPrimaryDataType.W3C,
            W3CSubType.W3C_2,
            CredentialDetectorTypes.SignaturePrimaryType.UNSIGNED
        ), // unsigned
        W3CExamples.dipEcdsaSignedW3CCredential to W3CExamples.dipEcdsaSignedW3CCredentialValues to CredentialDetectionResult(
            CredentialPrimaryDataType.W3C,
            W3CSubType.W3C_2,
            CredentialDetectorTypes.SignaturePrimaryType.DATA_INTEGRITY_PROOF
        ), // DataIntegrityProof: ECDSA
        W3CExamples.dipEddsaSignedW3CCredential to W3CExamples.dipEddsaSignedW3CCredentialValues to CredentialDetectionResult(
            CredentialPrimaryDataType.W3C,
            W3CSubType.W3C_2,
            CredentialDetectorTypes.SignaturePrimaryType.DATA_INTEGRITY_PROOF
        ), // DataIntegrityProof: EdDSA
        W3CExamples.dipEcdsaSdSignedW3CCredential to W3CExamples.dipEcdsaSdSignedW3CCredentialValues to CredentialDetectionResult(
            CredentialPrimaryDataType.W3C,
            W3CSubType.W3C_2,
            CredentialDetectorTypes.SignaturePrimaryType.DATA_INTEGRITY_PROOF
        ), // DataIntegrityProof: ECDSA-SD
        W3CExamples.dipBbsSignedW3CCredential to W3CExamples.dipBbsSignedW3CCredentialValues to CredentialDetectionResult(
            CredentialPrimaryDataType.W3C,
            W3CSubType.W3C_2,
            CredentialDetectorTypes.SignaturePrimaryType.DATA_INTEGRITY_PROOF
        ), // DataIntegrityProof: BBS
        W3CExamples.joseSignedW3CCredential to W3CExamples.joseSignedW3CCredentialValues to CredentialDetectionResult(
            CredentialPrimaryDataType.W3C,
            W3CSubType.W3C_2,
            CredentialDetectorTypes.SignaturePrimaryType.JWT
        ), // JOSE
        W3CExamples.waltidIssuedJoseSignedW3CCredential to W3CExamples.waltidIssuedJoseSignedW3CCredentialValues to CredentialDetectionResult(
            CredentialPrimaryDataType.W3C,
            W3CSubType.W3C_1_1,
            CredentialDetectorTypes.SignaturePrimaryType.JWT
        ),
        //coseSignedW3CCredential to CredentialHeuristicGuess(CredentialPrimaryDataType.W3C, W3CSubType.W3C_2, CredentialHeuristics.SignaturePrimaryType.COSE), // COSE // TODO FIXME
        /*sdJwtSignedW3CCredential to CredentialDetectionResult(
            CredentialPrimaryDataType.W3C,
            W3CSubType.W3C_2,
            CredentialDetectorTypes.SignaturePrimaryType.SDJWT,
            containsDisclosables = true,
            providesDisclosures = true
        ), // SD-JWT*/
        W3CExamples.ossIssuedW3CSdJwt to W3CExamples.ossIssuedW3CSdJwtValues to CredentialDetectionResult(
            CredentialPrimaryDataType.W3C,
            W3CSubType.W3C_1_1,
            CredentialDetectorTypes.SignaturePrimaryType.SDJWT,
            containsDisclosables = true,
            providesDisclosures = true
        ),
        W3CExamples.ossIssuedW3CSdJwt2 to W3CExamples.ossIssuedW3CSdJwt2Values to CredentialDetectionResult(
            CredentialPrimaryDataType.W3C,
            W3CSubType.W3C_2,
            CredentialDetectorTypes.SignaturePrimaryType.SDJWT,
            containsDisclosables = true,
            providesDisclosures = true
        )
    )

}
