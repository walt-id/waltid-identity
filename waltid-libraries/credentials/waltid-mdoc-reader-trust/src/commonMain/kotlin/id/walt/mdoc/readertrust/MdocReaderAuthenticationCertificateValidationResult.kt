package id.walt.mdoc.readertrust

/** Outcome of [MdocReaderAuthenticationTrustEvaluator.validateReaderCertificateChain]. */
sealed interface MdocReaderAuthenticationCertificateValidationResult {
    /** The reader certificate violates the ISO/IEC 18013-5 profile or validity, or could not be validated. */
    data class InvalidCertificate(val reason: String) : MdocReaderAuthenticationCertificateValidationResult

    /** The reader certificate conforms, but no trust anchor was available to validate its path against. */
    data object NoTrustAnchor : MdocReaderAuthenticationCertificateValidationResult

    /** The reader certificate conforms, but its chain does not validate against any of the trust anchors. */
    data class UntrustedPath(val reason: String) : MdocReaderAuthenticationCertificateValidationResult

    /** The reader chain validates against one of the trust anchors. */
    data object Trusted : MdocReaderAuthenticationCertificateValidationResult
}
