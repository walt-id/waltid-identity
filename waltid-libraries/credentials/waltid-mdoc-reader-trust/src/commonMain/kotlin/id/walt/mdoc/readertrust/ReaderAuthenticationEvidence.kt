package id.walt.mdoc.readertrust

import kotlinx.io.bytestring.ByteString

/**
 * Identifies one reader authentication statement in a request and carries the certificate chain it
 * was signed with.
 *
 * Evidence handed to a [ReaderTrustEvaluator] belongs to a statement whose signature has already
 * been verified with the leaf key. Its chain is still exactly what the reader sent: neither the
 * certification path nor revocation has been checked, so it must not be treated as trusted input.
 */
class ReaderAuthenticationEvidence(
    /** Which part of the request the statement authenticates: one document request or the whole request. */
    val scope: ReaderAuthenticationScope,
    /** Zero-based statement index within the authentication scope. */
    val authenticationIndex: Int = 0,
    certificateChainDer: List<ByteString> = emptyList(),
) {
    private val certificateChain = certificateChainDer.toList()

    /**
     * DER certificates from the statement's `x5chain`, leaf first, in the order the reader sent them.
     * Empty only before the statement has been verified. Each read returns a defensive copy.
     */
    val certificateChainDer: List<ByteString> get() = certificateChain.toList()

    init { require(authenticationIndex >= 0) }
}
