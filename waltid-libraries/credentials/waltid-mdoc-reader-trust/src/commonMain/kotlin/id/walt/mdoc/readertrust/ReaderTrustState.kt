package id.walt.mdoc.readertrust

/** Trust outcome for a reader authentication statement. */
enum class ReaderTrustState {
    /** No trust evaluation ran, because the statement was absent, malformed or cryptographically invalid. */
    NOT_EVALUATED,

    /**
     * The signature verifies, but no configured trust source vouches for the reader, or trust
     * evaluation itself failed. Whether such a reader may still be answered is the caller's policy.
     */
    VALID_BUT_UNTRUSTED,

    /** A configured trust source reports the reader certificate as revoked; the request must not be answered. */
    REVOKED,

    /** A configured trust source vouches for the reader whose signature verifies. */
    TRUSTED,
}
