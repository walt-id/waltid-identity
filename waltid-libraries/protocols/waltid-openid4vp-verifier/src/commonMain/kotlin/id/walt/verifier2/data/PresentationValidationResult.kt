package id.walt.verifier2.data

import id.walt.credentials.formats.DigitalCredential

data class PresentationValidationResult(
    /** overall result */
    val presentationValid: Boolean,
    val allSuccessfullyValidatedAndProcessedData: Map<String, List<DigitalCredential>>
)
