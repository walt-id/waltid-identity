package id.walt.mdoc.readertrust

data class ReaderTrustDecision(
    /** Trust outcome for a reader whose authentication signature has already been verified. */
    val state: ReaderTrustState,
    /** Display-safe explanation of why the reader is not trusted; usually absent when [state] is trusted. */
    val reason: String? = null,
    /**
     * Display-safe reader name taken from the trust source that recognised it, such as a RICAL
     * authority. It can be present when [state] is not trusted and never establishes trust by itself.
     */
    val displayName: String? = null,
)
