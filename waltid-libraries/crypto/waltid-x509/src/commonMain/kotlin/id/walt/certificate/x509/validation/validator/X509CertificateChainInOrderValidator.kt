package id.walt.certificate.x509.validation.validator

import id.walt.certificate.x509.X509Certificate
import id.walt.certificate.x509.validation.ValidationContext
import id.walt.certificate.x509.validation.ValidationResult

/**
 * Requires the certificates to have been provided in leaf-first order, each followed by its issuer and each
 * exactly once — the order mandated for e.g. `x5chain` (RFC 9360) and `x5c` (RFC 7515).
 *
 * The chain is validated root first, so the certificate at [ValidationContext.certificateIndex] `i` must
 * have been provided only at position `chainLength - 1 - i`
 * ([ValidationContext.certificateIndicesInProvidedChain]). Each certificate provided at another position,
 * or more than once, is reported with severity ERROR. Duplicates are removed before the chain is built, so
 * this is checked against the provided list: `[leaf, root, leaf]` is rejected for the repeated leaf. A
 * certificate that was not part of the provided list is not judged.
 */
class X509CertificateChainInOrderValidator : X509CertificateValidator {

    override val id: String = ID

    override suspend fun validate(
        context: ValidationContext,
        x509Certificate: X509Certificate
    ) {
        val providedIndices = context.certificateIndicesInProvidedChain
        if (providedIndices.isEmpty()) return
        val expectedIndex = context.chainLength - 1 - context.certificateIndex
        if (providedIndices != listOf(expectedIndex)) {
            val provided = if (providedIndices.size == 1) {
                "Certificate provided at position ${providedIndices.single()}"
            } else {
                "Certificate provided ${providedIndices.size} times, at positions ${providedIndices.joinToString()}"
            }
            context.addLogEntry(
                ValidationResult.Severity.ERROR,
                "$provided, expected only at position $expectedIndex: " +
                        "the chain must be ordered leaf first, each certificate followed by its issuer"
            )
        }
    }

    companion object {
        const val ID = "certificate-chain-in-order"
    }
}
