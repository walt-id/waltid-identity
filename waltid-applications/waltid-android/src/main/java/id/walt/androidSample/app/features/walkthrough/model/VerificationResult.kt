package id.walt.androidSample.app.features.walkthrough.model

sealed interface VerificationResult {
    data object Failed: VerificationResult
    data object Success: VerificationResult
}