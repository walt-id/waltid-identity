package id.walt.mdoc.readertrust

/** Secure default: a verified signature alone never makes a reader trusted. */
object UnconfiguredReaderTrustEvaluator : ReaderTrustEvaluator {
    /** Reports the reader as untrusted because no trust policy is configured. */
    override suspend fun evaluate(evidence: ReaderAuthenticationEvidence): ReaderTrustDecision =
        ReaderTrustDecision(
            state = ReaderTrustState.VALID_BUT_UNTRUSTED,
            reason = "Reader authentication is cryptographically valid, but no reader trust policy is configured",
        )
}
