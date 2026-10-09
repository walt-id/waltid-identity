package id.walt.certificate.x509.validation.validator

import id.walt.certificate.x509.X509Certificate
import id.walt.certificate.x509.validation.ValidationContext
import id.walt.certificate.x509.validation.ValidationResult

/**
 * Requires the certificates to have been provided in leaf-first order, each followed by its issuer — the
 * order mandated for e.g. `x5chain` (RFC 9360) and `x5c` (RFC 7515).
 *
 * The chain is validated root first, so the certificate at [ValidationContext.certificateIndex] `i` must
 * have been provided at position `chainLength - 1 - i`
 * ([ValidationContext.certificateIndexInProvidedChain]). Each certificate in the wrong position is
 * reported with severity ERROR. A certificate that was not part of the provided list is not judged.
 *
 * Exact duplicates are removed before the chain is built, so they are not detected here; the
 * positions of the remaining certificates are still checked.
 */
class X509CertificateChainInOrderValidator : X509CertificateValidator {

    override val id: String = ID

    override suspend fun validate(
        context: ValidationContext,
        x509Certificate: X509Certificate
    ) {
        val providedIndex = context.certificateIndexInProvidedChain ?: return
        val expectedIndex = context.chainLength - 1 - context.certificateIndex
        if (providedIndex != expectedIndex) {
            context.addLogEntry(
                ValidationResult.Severity.ERROR,
                "Certificate provided at position $providedIndex, expected at position $expectedIndex: " +
                        "the chain must be ordered leaf first, each certificate followed by its issuer"
            )
        }
    }

    companion object {
        const val ID = "certificate-chain-in-order"
    }
}
