package id.walt.mdoc.readertrust

/**
 * Trust outcome for one mdoc reader authentication statement (ISO/IEC 18013-5 `ReaderAuth`).
 *
 * Reader authentication is judged in two independent steps:
 * 1. **Signature:** the statement's COSE signature is verified with the leaf key of the reader-supplied
 *    `x5chain`. This proves only that whoever holds that key signed the request.
 * 2. **Trust:** a [ReaderTrustEvaluator] decides whether that key belongs to a reader the holder
 *    trusts. It checks certification paths against configured anchors or lists such as a RICAL, the
 *    certificate profile, and revocation.
 *
 * This state is the result of step 2, or [NOT_EVALUATED] when step 2 never ran. It is the
 * evaluator-neutral contract: every [ReaderTrustEvaluator] reports one of these values, and
 * implementation-specific detail (for example why a reader is untrusted) goes into
 * [ReaderTrustDecision.reason] or into the implementation's own detailed result type.
 *
 * How callers should act:
 * - Only [TRUSTED] identifies the reader. Present [ReaderTrustDecision.displayName] as the reader's
 *   identity only then.
 * - [REVOKED] fails closed: do not answer the request.
 * - [VALID_BUT_UNTRUSTED] and [NOT_EVALUATED] are anonymous readers. Whether they may still be
 *   answered, for example after explicit user consent, is the caller's policy.
 * - When a request carries several statements (one per document request and/or one for the whole
 *   request, see [ReaderAuthenticationScope]), a single [REVOKED] rejects the whole request, and the
 *   reader is identified only if the statements covering the disclosed documents are all [TRUSTED].
 */
enum class ReaderTrustState {
    /**
     * No trust evaluation ran: the request carried no reader authentication, or the statement was
     * malformed or its signature did not verify.
     *
     * Produced by the caller, never by a [ReaderTrustEvaluator], which is only called for statements
     * whose signature has already been verified. Nothing is known about the reader.
     */
    NOT_EVALUATED,

    /**
     * The signature verifies, but no configured trust source vouches for the reader.
     *
     * Covers every case that does not establish trust: no trust policy configured, a reader
     * certificate that violates the ISO/IEC 18013-5 profile or is outside its validity period, a
     * chain that does not reach a configured anchor or RICAL authority, unmet RICAL constraints, an
     * unavailable or invalid trust source, a policy that records evidence without establishing
     * trust, and an evaluator that failed or threw. [ReaderTrustDecision.reason] says which.
     *
     * The reader must be treated as anonymous; any [ReaderTrustDecision.displayName] is evidence
     * only and must not be presented as a verified identity.
     */
    VALID_BUT_UNTRUSTED,

    /**
     * A configured trust source reports the reader authentication certificate, or a certificate in
     * its validated path, as revoked.
     *
     * The request must not be answered, regardless of user consent or other statements in the same
     * request.
     */
    REVOKED,

    /**
     * A configured trust source vouches for the reader: its certificate conforms to the ISO/IEC
     * 18013-5 reader authentication profile and its path validates against a trust anchor or RICAL
     * authority the holder configured, with any constraints satisfied.
     *
     * Only this state identifies the reader; [ReaderTrustDecision.displayName], when present, may be
     * shown as the reader's name. It never authorizes disclosure by itself: the holder still decides
     * what to share.
     */
    TRUSTED,
}
