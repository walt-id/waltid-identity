package id.walt.mdoc.readertrust

/**
 * Trust policy that decides whether a reader with a verified authentication signature is trusted.
 *
 * Signature verification is not this interface's job: it runs only for statements whose signature has
 * already been verified with the leaf key of the reader-supplied chain. Everything that makes the
 * reader trusted belongs here instead — certification path validation against configured trust
 * anchors or lists, certificate profile checks, revocation and authorization. The reader's chain must
 * never become a trust anchor by itself.
 */
fun interface ReaderTrustEvaluator {
    /**
     * Evaluates one verified reader authentication statement.
     *
     * Called once per statement, so a request with several statements is evaluated several times.
     * Callers treat a thrown exception, other than cancellation, as
     * [ReaderTrustState.VALID_BUT_UNTRUSTED]; an implementation should still report an outcome it
     * cannot establish as an untrusted decision with a [ReaderTrustDecision.reason] instead of throwing.
     */
    suspend fun evaluate(evidence: ReaderAuthenticationEvidence): ReaderTrustDecision
}
