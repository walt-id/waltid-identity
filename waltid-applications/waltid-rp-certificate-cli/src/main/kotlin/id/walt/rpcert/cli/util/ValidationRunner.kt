package id.walt.rpcert.cli.util

import id.walt.rpcert.wallet.RegistrationCertificateWalletValidator
import id.walt.rpcert.wallet.RegistrationValidationResult
import id.walt.verifier.openid.models.authorization.AuthorizationRequest
import id.walt.x509.CertificateDer

data class ValidationOutcome(
    val result: RegistrationValidationResult,
    val reasoning: String,
) {
    val allowed: Boolean get() = result.allowed
}

/**
 * Extracts the registration certificate from an Authorization Request's `verifier_info` and runs it
 * through [RegistrationCertificateWalletValidator], producing a human-readable reasoning line
 * alongside the raw result.
 */
object ValidationRunner {

    suspend fun run(
        authorizationRequest: AuthorizationRequest,
        trustAnchors: List<CertificateDer>,
        allowSelfSigned: Boolean,
    ): ValidationOutcome {
        val certJwt = VerifierInfoCertExtractor.extractRegistrationCertificateJwt(authorizationRequest)
        val result = RegistrationCertificateWalletValidator.validate(
            authorizationRequest = authorizationRequest,
            registrationCertificateJwt = certJwt,
            trustAnchors = trustAnchors.ifEmpty { null },
            allowTrustedChainRoot = allowSelfSigned,
        )
        return ValidationOutcome(result, describe(result))
    }

    private fun describe(result: RegistrationValidationResult): String = when (result) {
        is RegistrationValidationResult.Allowed ->
            "ALLOWED: registration certificate '${result.registrationCertificate.certificate.name}' covers all requested claims"

        is RegistrationValidationResult.RequestNotCovered ->
            "REJECTED: registration certificate '${result.registrationCertificate.certificate.name}' does not cover all requested claims"

        is RegistrationValidationResult.InvalidRegistrationCertificate ->
            "REJECTED: registration certificate is invalid (${result.cause.message})"

        is RegistrationValidationResult.MissingDcqlQuery ->
            "REJECTED: ${result.message}"
    }
}
