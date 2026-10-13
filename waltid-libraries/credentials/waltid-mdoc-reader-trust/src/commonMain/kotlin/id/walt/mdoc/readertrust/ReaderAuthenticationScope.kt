package id.walt.mdoc.readertrust

/**
 * The part of a `DeviceRequest` a reader authentication statement signs.
 *
 * Scope is part of the statement identity; whole-request statements have no document index.
 */
sealed interface ReaderAuthenticationScope {
    /** A per-document `ReaderAuth` statement, which signs only that document request's `ItemsRequest`. */
    data class Document(
        /** Zero-based index of the signed document request within the `DeviceRequest`'s `docRequests`. */
        val index: Int,
    ) : ReaderAuthenticationScope {
        init { require(index >= 0) { "Document request index must not be negative" } }
    }

    /**
     * A `ReaderAuthAll` statement, which signs every document request together with the request-level
     * `DeviceRequestInfo`. A request can carry several, told apart by
     * [ReaderAuthenticationEvidence.authenticationIndex].
     */
    data object WholeRequest : ReaderAuthenticationScope
}
