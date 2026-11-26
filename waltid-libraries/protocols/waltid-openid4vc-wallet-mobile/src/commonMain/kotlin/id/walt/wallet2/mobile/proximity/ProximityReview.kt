package id.walt.wallet2.mobile.proximity

import kotlin.time.Instant

/**
 * One requested or alternative data element shown during consent.
 *
 * @property namespace Issuer-signed namespace containing the element.
 * @property elementIdentifier Element identifier within [namespace].
 * @property intentToRetain Reader-declared retention intent.
 * @property satisfiesRequestedElements Requested elements satisfied by this disclosed alternative.
 */
public data class ProximityRequestedElement(
    public val namespace: String,
    public val elementIdentifier: String,
    public val intentToRetain: Boolean,
    public val satisfiesRequestedElements: List<ProximityElementReference> = emptyList(),
) {
    init {
        require(namespace.isNotBlank() && elementIdentifier.isNotBlank())
        require(satisfiesRequestedElements.distinct().size == satisfiesRequestedElements.size)
    }
}

/**
 * Original requested-document context when the final request cannot be satisfied overall.
 * This does not imply that every document was individually unmatched. Reader authentication
 * has not been evaluated on this path. No wallet credential values or identifiers are retained.
 *
 * @param documents Requested documents in their original order, including repeated document types.
 */
public class ProximityUnsatisfiedRequest(documents: List<ProximityUnsatisfiedDocument>) {
    private val ownedDocuments = documents.toList()

    init { require(ownedDocuments.isNotEmpty()) }

    /** Requested documents. The returned collection can be changed without altering this summary. */
    public val documents: List<ProximityUnsatisfiedDocument> get() = ownedDocuments.toList()
}

/**
 * One document in the original reader request, without credential or raw cryptographic data.
 *
 * @property requestIndex Original zero-based document request index.
 * @property docType Requested mdoc document type, unchanged from the request.
 * @param requestedElements Requested namespace/element pairs and reader-declared retention intent.
 */
public class ProximityUnsatisfiedDocument(
    public val requestIndex: Int,
    public val docType: String,
    requestedElements: List<ProximityRequestedElement>,
) {
    private val ownedElements = requestedElements.map { it.copy(satisfiesRequestedElements = it.satisfiesRequestedElements.toList()) }

    init {
        require(requestIndex >= 0 && docType.isNotBlank())
        require(ownedElements.isNotEmpty())
        require(ownedElements.all { it.satisfiesRequestedElements.isEmpty() }) {
            "An unsatisfied request contains original requested elements, not disclosure alternatives"
        }
    }

    /** Original requested elements. The returned collection does not expose owned mutable state. */
    public val requestedElements: List<ProximityRequestedElement> get() = ownedElements.toList()
}

/**
 * Dependency-free namespace and element identifier.
 *
 * @property namespace Namespace containing the element.
 * @property elementIdentifier Element identifier within [namespace].
 */
public data class ProximityElementReference(
    public val namespace: String,
    public val elementIdentifier: String,
) {
    init { require(namespace.isNotBlank() && elementIdentifier.isNotBlank()) }
}

/**
 * Eligible wallet credential projected without raw credential or key material.
 *
 * @property credentialId Stable wallet-local credential identifier.
 * @property label Display-safe credential label when available.
 * @property issuer Display-safe issuer identifier when available.
 * @property validUntil End of the locally verified MSO validity interval.
 * @property deviceAuthentication Holder authentication frozen for this option.
 */
public data class ProximityCredentialOption(
    public val credentialId: String,
    public val label: String?,
    public val issuer: String?,
    public val validUntil: Instant,
    public val deviceAuthentication: ProximityDeviceAuthenticationMethod,
    /** Exact requested or alternative elements this credential would disclose. */
    public val requestedElements: List<ProximityRequestedElement>,
) {
    init {
        require(credentialId.isNotBlank())
        require(requestedElements.isNotEmpty())
        require(requestedElements.distinctBy { it.namespace to it.elementIdentifier }.size == requestedElements.size)
    }
}

/**
 * One satisfiable document request in an immutable review snapshot.
 *
 * @property requestIndex Zero-based document request index.
 * @property docType Requested mdoc document type.
 * @property credentialOptions Eligible credentials and their exact disclosure choices.
 */
public data class ProximityDocumentReview(
    public val requestIndex: Int,
    public val docType: String,
    public val credentialOptions: List<ProximityCredentialOption>,
    /** Profile-defined data required if this document is shared, such as the requested mDL portrait. */
    public val requiredElements: Set<ProximityElementReference> = emptySet(),
) {
    init {
        require(requestIndex >= 0 && docType.isNotBlank())
        require(credentialOptions.isNotEmpty())
        require(credentialOptions.distinctBy { it.credentialId }.size == credentialOptions.size)
    }
}

/**
 * Reader-asserted purpose hint associated with the selected use case.
 *
 * @property type Purpose-hint type defined by the selected profile.
 * @property code Purpose-hint code defined by the selected profile.
 * @property readerAsserted Whether the value came from the reader request.
 */
public data class ProximityPurposeHint(
    public val type: String,
    public val code: Int,
    public val readerAsserted: Boolean = true,
) {
    init { require(type.isNotBlank()) }
}

/**
 * Selected edition-2 use case projected for review.
 *
 * @property index Zero-based use-case index in the request.
 * @property mandatory Whether the reader marked the use case mandatory.
 * @property documentRequestIndices Document requests governed by the use case.
 * @property purposeHints Reader-asserted purpose hints associated with the use case.
 */
public data class ProximityUseCase(
    public val index: Int,
    public val mandatory: Boolean,
    public val documentRequestIndices: List<Int>,
    public val purposeHints: List<ProximityPurposeHint>,
) {
    init {
        require(index >= 0)
        require(documentRequestIndices.isNotEmpty() && documentRequestIndices.all { it >= 0 })
        require(documentRequestIndices.distinct().size == documentRequestIndices.size)
    }
}

/**
 * Immutable holder-consent snapshot bound to one exchange and exact request.
 *
 * @property exchange One-based request exchange number within the session.
 * @property documents Satisfiable document requests and eligible credential choices.
 * @property readerAuthentication Reader authentication, certificate, revocation, RICAL, and trust facts.
 * @property useCases Edition-2 use cases selected by the request.
 * @property applicationAuthorizations Validated application-profile authorizations.
 */
public data class ProximityReview(
    /** Opaque identity unique to this review across session instances. */
    public val reviewId: ProximityReviewId,
    public val exchange: Int,
    public val documents: List<ProximityDocumentReview>,
    public val readerAuthentication: List<ProximityReaderAuthentication>,
    public val useCases: List<ProximityUseCase>,
    public val applicationAuthorizations: List<ProximityApplicationAuthorization>,
) {
    /**
     * Coverage and trust summary computed in the shared SDK for all reviewed documents.
     * The model preserves invalid, malformed and revoked states for completeness. The session
     * processor rejects those states before consent; render their terminal error instead.
     */
    public val readerAuthenticationSummary: ProximityReaderAuthenticationSummary
        get() {
            if (readerAuthentication.any { it.validity == ProximityReaderAuthenticationValidity.Malformed }) {
                return ProximityReaderAuthenticationSummary.Malformed
            }
            if (readerAuthentication.any { it.validity == ProximityReaderAuthenticationValidity.Invalid }) {
                return ProximityReaderAuthenticationSummary.Invalid
            }
            if (readerAuthentication.any { it.trust == ProximityReaderTrustState.Revoked }) {
                return ProximityReaderAuthenticationSummary.Revoked
            }
            val valid = readerAuthentication.filter { it.validity == ProximityReaderAuthenticationValidity.Valid }
            if (valid.isEmpty()) return ProximityReaderAuthenticationSummary.Absent
            val coverage = documents.map { document ->
                valid.filter { authentication ->
                    authentication.scope == ProximityReaderAuthenticationScope.WholeRequest ||
                        authentication.scope.documentRequestIndex == document.requestIndex
                }.map { it.trust }.let { trust ->
                    when {
                        ProximityReaderTrustState.Trusted in trust -> ProximityReaderAuthenticationSummary.Trusted
                        ProximityReaderTrustState.ValidButUntrusted in trust -> ProximityReaderAuthenticationSummary.ValidButUntrusted
                        else -> ProximityReaderAuthenticationSummary.Partial
                    }
                }
            }
            return when {
                ProximityReaderAuthenticationSummary.Partial in coverage -> ProximityReaderAuthenticationSummary.Partial
                ProximityReaderAuthenticationSummary.ValidButUntrusted in coverage -> ProximityReaderAuthenticationSummary.ValidButUntrusted
                else -> ProximityReaderAuthenticationSummary.Trusted
            }
        }

    init {
        require(exchange > 0 && documents.isNotEmpty())
        require(documents.distinctBy { it.requestIndex }.size == documents.size)
        val requestIndices = documents.map { it.requestIndex }.toSet()
        require(readerAuthentication.all { authentication ->
            authentication.scope.documentRequestIndex == null || authentication.scope.documentRequestIndex in requestIndices
        }) { "Reader authentication refers to a document outside this review" }
        require(readerAuthentication.distinctBy {
            it.scope to it.authenticationIndex
        }.size == readerAuthentication.size) { "Reader-authentication statements must be unique" }
        require(useCases.distinctBy { it.index }.size == useCases.size)
        require(useCases.all { useCase -> useCase.documentRequestIndices.all { it in requestIndices } }) {
            "A selected use case refers to a document outside this review"
        }
        require(applicationAuthorizations.distinctBy { it.profileId }.size == applicationAuthorizations.size)
        val credentialIds = documents.flatMap { it.credentialOptions }.map { it.credentialId }.toSet()
        require(applicationAuthorizations.all { it.compatibleCredentialIds.all(credentialIds::contains) }) {
            "An application authorization refers to a credential outside this review"
        }
    }
}
