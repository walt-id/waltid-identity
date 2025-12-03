package id.walt.openid4vp.verifier.data

import id.walt.credentials.formats.DigitalCredential

data class PresentationValidationResult(
    /** overall result */
    val presentationValid: Boolean,
    val allSuccessfullyValidatedAndProcessedData: Map<String, List<DigitalCredential>>
)
